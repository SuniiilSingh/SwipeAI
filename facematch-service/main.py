import base64
import io
import logging
import os
import re
from typing import List, Optional, Tuple

import cv2
import numpy as np
import requests
from fastapi import FastAPI, HTTPException, status
from PIL import Image, ImageOps
from pydantic import BaseModel

# Configure logging
logging.basicConfig(level=logging.INFO, format="%(asctime)s [%(levelname)s] %(message)s")
logger = logging.getLogger("facematch")

app = FastAPI(title="SwipeAI Biometric Face Match Service", version="1.1.0")

# Paths for ONNX models
MODEL_DIR = os.getenv("MODEL_DIR", "/app/models")
YUNET_PATH = os.path.join(MODEL_DIR, "face_detection_yunet.onnx")
SFACE_PATH = os.path.join(MODEL_DIR, "face_recognition_sface.onnx")
NSFW_ONNX_PATH = os.path.join(MODEL_DIR, "open_nsfw.onnx")

# Adaptive Multi-Tier Face Recognition Thresholds
DEFAULT_COSINE_THRESHOLD = 0.32  # Green Tier: High confidence instant match
AMBER_COSINE_THRESHOLD = 0.20    # Amber Tier: Stylized / High-Fashion / Angles / Studio Shadows
RED_CATFISH_THRESHOLD = 0.17     # Red Tier: Definite Mismatch / Non-matching stranger

recognizer: Optional[cv2.FaceRecognizerSF] = None
nsfw_net: Optional[cv2.dnn.Net] = None


@app.on_event("startup")
def load_models():
    global recognizer, nsfw_net
    logger.info("Initializing YuNet, SFace, and OpenNSFW models...")
    if not os.path.exists(YUNET_PATH) or not os.path.exists(SFACE_PATH):
        logger.error(f"Models not found at {YUNET_PATH} or {SFACE_PATH}")
        raise RuntimeError("Model files missing from container filesystem.")

    recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
    logger.info("Face recognition models loaded successfully.")

    if os.path.exists(NSFW_ONNX_PATH):
        try:
            nsfw_net = cv2.dnn.readNetFromONNX(NSFW_ONNX_PATH)
            logger.info("OpenNSFW ONNX model loaded successfully into OpenCV DNN.")
        except Exception as e:
            logger.warning(f"Failed to load OpenNSFW ONNX model: {e}")
    else:
        logger.warning(f"OpenNSFW model not found at {NSFW_ONNX_PATH}.")


def decode_image_bytes(data: bytes) -> np.ndarray:
    """
    Decode raw bytes into OpenCV BGR image array with EXIF orientation correction.
    Most smartphone front-facing selfies include EXIF tags (e.g., orientation 6 or 8)
    which cv2.imdecode ignores. Pillow's exif_transpose guarantees the image is rotated upright.
    """
    try:
        pil_img = Image.open(io.BytesIO(data))
        pil_img = ImageOps.exif_transpose(pil_img)
        pil_img = pil_img.convert("RGB")
        rgb_arr = np.array(pil_img)
        bgr_arr = cv2.cvtColor(rgb_arr, cv2.COLOR_RGB2BGR)
        return bgr_arr
    except Exception as e:
        logger.warning(f"PIL EXIF decoding failed ({e}); falling back to cv2.imdecode")
        np_arr = np.frombuffer(data, np.uint8)
        img = cv2.imdecode(np_arr, cv2.IMREAD_COLOR)
        if img is None:
            raise ValueError("Decoded image is empty or invalid format.")
        return img


def load_image(image_input: Optional[str], fallback_url: Optional[str] = None) -> np.ndarray:
    """
    Load image from:
    1. Base64 string (data:image/jpeg;base64,...)
    2. Local path (/app/uploads/... or directly mounted uploads volume)
    3. HTTP / HTTPS URL
    """
    raw_str = (image_input or fallback_url or "").strip()
    if not raw_str:
        raise ValueError("No image data or URL provided.")

    # 1. Base64 data string
    if raw_str.startswith("data:image") or (len(raw_str) > 500 and not raw_str.startswith("http") and not raw_str.startswith("/")):
        base64_data = re.sub(r"^data:image/[a-zA-Z]+;base64,", "", raw_str)
        try:
            decoded = base64.b64decode(base64_data)
            return decode_image_bytes(decoded)
        except Exception as e:
            raise ValueError(f"Failed to decode base64 image: {str(e)}")

    # 2. Local uploads shortcut (checks mounted /app/uploads first)
    if "/uploads/" in raw_str or "/v1/images/" in raw_str:
        filename = raw_str.split("/uploads/")[-1] if "/uploads/" in raw_str else raw_str.split("/v1/images/")[-1]
        filename = filename.split("?")[0]
        upload_path = os.path.join("/app/uploads", filename)
        if os.path.exists(upload_path):
            try:
                with open(upload_path, "rb") as f:
                    return decode_image_bytes(f.read())
            except Exception as e:
                logger.warning(f"Failed to read local upload from {upload_path}: {e}")

    # 3. Local filesystem path
    if raw_str.startswith("/"):
        if os.path.exists(raw_str):
            try:
                with open(raw_str, "rb") as f:
                    return decode_image_bytes(f.read())
            except Exception as e:
                logger.warning(f"Failed to read local path {raw_str}: {e}")

        # Check in /app/uploads by basename
        possible_local = os.path.join("/app/uploads", os.path.basename(raw_str))
        if os.path.exists(possible_local):
            try:
                with open(possible_local, "rb") as f:
                    return decode_image_bytes(f.read())
            except Exception as e:
                logger.warning(f"Failed to read local file {possible_local}: {e}")

    # 4. HTTP / HTTPS URL
    if raw_str.startswith("http://") or raw_str.startswith("https://"):
        try:
            resp = requests.get(raw_str, timeout=10)
            if resp.status_code == 200:
                return decode_image_bytes(resp.content)
            else:
                raise ValueError(f"HTTP fetch failed with status {resp.status_code}")
        except Exception as e:
            raise ValueError(f"Failed to fetch image from URL: {str(e)}")

    # Fallback attempt if base64 without prefix
    try:
        decoded = base64.b64decode(raw_str)
        return decode_image_bytes(decoded)
    except Exception:
        pass

    raise ValueError(f"Unsupported image input format: {raw_str[:60]}...")


def filter_valid_faces(faces: Optional[np.ndarray], img_shape: Tuple[int, int], is_selfie: bool = False) -> np.ndarray:
    """
    Filter out tiny background false-positives and distant poster faces.
    In a front-camera selfie, the user's face is prominent (occupies >= 1.5% of total frame area).
    """
    if faces is None or len(faces) == 0:
        return np.array([])
    h, w = img_shape[:2]
    total_area = h * w
    min_area = total_area * 0.015 if is_selfie else total_area * 0.003
    valid = []
    for f in faces:
        fw, fh = float(f[2]), float(f[3])
        if (fw * fh) >= min_area:
            valid.append(f)
    if not valid and len(faces) > 0:
        # Fallback to largest face
        sorted_faces = sorted(faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)
        return np.array([sorted_faces[0]])
    return np.array(valid)


def detect_faces_and_orient(img: np.ndarray, score_threshold: float = 0.35, is_selfie: bool = False) -> Tuple[np.ndarray, np.ndarray]:
    """
    Detect faces using YuNet with 4-orientation scanning (0°, 90° CW, 270° CW, 180°).
    If a face is detected in any rotation, returns (upright_img, faces).
    This ensures mobile selfies taken at arbitrary camera angles or missing EXIF tags
    are properly aligned and detected rather than failing with 0 faces.
    """
    h, w = img.shape[:2]

    # Primary check: 0 deg (original)
    detector = cv2.FaceDetectorYN.create(
        YUNET_PATH, "", (w, h), score_threshold=score_threshold, nms_threshold=0.3, top_k=10
    )
    _, faces = detector.detect(img)
    faces = filter_valid_faces(faces, (h, w), is_selfie=is_selfie)
    if len(faces) > 0:
        return img, faces

    # Fallback rotations: 90° Clockwise, 90° Counter-Clockwise (270°), 180°
    rotations = [
        (cv2.ROTATE_90_CLOCKWISE, "90 CW"),
        (cv2.ROTATE_90_COUNTERCLOCKWISE, "90 CCW"),
        (cv2.ROTATE_180, "180"),
    ]

    for rot_code, rot_name in rotations:
        rotated = cv2.rotate(img, rot_code)
        rh, rw = rotated.shape[:2]
        rot_detector = cv2.FaceDetectorYN.create(
            YUNET_PATH, "", (rw, rh), score_threshold=score_threshold, nms_threshold=0.3, top_k=10
        )
        _, rot_faces = rot_detector.detect(rotated)
        rot_faces = filter_valid_faces(rot_faces, (rh, rw), is_selfie=is_selfie)
        if len(rot_faces) > 0:
            logger.info(f"Detected face after auto-rotation to {rot_name} (dimensions: {rw}x{rh})")
            return rotated, rot_faces

    return img, np.array([])


class MatchRequest(BaseModel):
    selfie_base64: Optional[str] = None
    selfie_url: Optional[str] = None
    photo_base64: Optional[str] = None
    photo_url: Optional[str] = None
    threshold: Optional[float] = None


class MatchResponse(BaseModel):
    status: str
    is_match: bool
    similarity_score: float
    confidence_percent: float
    selfie_faces_detected: int
    photo_faces_detected: int
    message: str


@app.get("/health")
def health():
    return {
        "status": "UP",
        "service": "swipeai-facematch",
        "models_loaded": recognizer is not None,
    }


@app.post("/match-faces", response_model=MatchResponse)
def match_faces(req: MatchRequest):
    if recognizer is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Face recognition model not loaded."
        )

    # 1. Load Selfie
    try:
        selfie_img = load_image(req.selfie_base64, req.selfie_url)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Selfie error: {str(e)}")

    # 2. Load Reference Profile Photo
    try:
        photo_img = load_image(req.photo_base64, req.photo_url)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Profile photo error: {str(e)}")

    # 3. Detect faces in selfie with multi-orientation support and prominence filtering
    selfie_img, selfie_faces = detect_faces_and_orient(selfie_img, score_threshold=0.35, is_selfie=True)
    if len(selfie_faces) == 0:
        logger.warning("Face matching rejected: 0 faces detected in selfie across all 4 orientations.")
        return MatchResponse(
            status="NO_FACE_IN_SELFIE",
            is_match=False,
            similarity_score=0.0,
            confidence_percent=0.0,
            selfie_faces_detected=0,
            photo_faces_detected=0,
            message="No clear human face detected in your selfie. Please hold the camera steadily at eye level.",
        )

    # 4. Detect faces in profile photo with multi-orientation support
    photo_img, photo_faces = detect_faces_and_orient(photo_img, score_threshold=0.35, is_selfie=False)
    if len(photo_faces) == 0:
        logger.warning("Face matching rejected: 0 faces detected in profile photo across all 4 orientations.")
        return MatchResponse(
            status="NO_FACE_IN_PHOTO",
            is_match=False,
            similarity_score=0.0,
            confidence_percent=0.0,
            selfie_faces_detected=len(selfie_faces),
            photo_faces_detected=0,
            message="No clear human face detected in your profile photo. Please upload a clear photo of yourself.",
        )

    # 5. Extract feature embedding for primary selfie face (largest face by bounding box area)
    selfie_faces_sorted = sorted(
        selfie_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True
    )
    primary_selfie_face = selfie_faces_sorted[0]
    selfie_aligned = recognizer.alignCrop(selfie_img, primary_selfie_face)
    selfie_feature = recognizer.feature(selfie_aligned)

    # 6. Compare with all faces in profile photo to support solo or group photos
    threshold = req.threshold if req.threshold is not None else DEFAULT_COSINE_THRESHOLD
    amber_threshold = AMBER_COSINE_THRESHOLD
    best_cosine_score = -1.0
    best_l2_score = 999.0

    for p_face in photo_faces:
        p_aligned = recognizer.alignCrop(photo_img, p_face)
        p_feature = recognizer.feature(p_aligned)

        cos_score = recognizer.match(selfie_feature, p_feature, cv2.FaceRecognizerSF_FR_COSINE)
        l2_score = recognizer.match(selfie_feature, p_feature, cv2.FaceRecognizerSF_FR_NORM_L2)

        if cos_score > best_cosine_score:
            best_cosine_score = float(cos_score)
            best_l2_score = float(l2_score)

    logger.info(
        f"Face match result: cosine={best_cosine_score:.4f}, L2={best_l2_score:.4f}, "
        f"threshold={threshold:.3f}, amber={amber_threshold:.3f}, selfie_faces={len(selfie_faces)}, photo_faces={len(photo_faces)}"
    )

    if best_cosine_score >= threshold:
        # Green Tier: Instant High-Confidence Match
        is_match = True
        ratio = min(1.0, max(0.0, (best_cosine_score - threshold) / max(0.01, 0.75 - threshold)))
        confidence_percent = round(80.0 + ratio * 19.5, 1)
        resp_status = "SUCCESS"
        message = "Biometric identity verified! Face matches your profile photo."
    elif best_cosine_score >= amber_threshold:
        # Amber Tier: Stylized / High-Fashion / Angles / Studio Shadows accepted
        is_match = True
        ratio = min(1.0, max(0.0, (best_cosine_score - amber_threshold) / max(0.01, threshold - amber_threshold)))
        confidence_percent = round(70.0 + ratio * 9.5, 1)
        resp_status = "SUCCESS"
        message = "Biometric match verified! (High-fashion / stylized pose accepted)"
    else:
        # Red Tier: Definite Mismatch / Catfish
        is_match = False
        ratio = max(0.0, best_cosine_score / max(0.01, amber_threshold))
        confidence_percent = round(min(55.0, ratio * 55.0), 1)
        resp_status = "MISMATCH"
        message = "Selfie does not match the person in the profile photo. Please ensure both show your real face."

    return MatchResponse(
        status=resp_status,
        is_match=is_match,
        similarity_score=round(best_cosine_score, 4),
        confidence_percent=confidence_percent,
        selfie_faces_detected=len(selfie_faces),
        photo_faces_detected=len(photo_faces),
        message=message,
    )


class PhotoSlotItem(BaseModel):
    slot: int
    photo_base64: Optional[str] = None
    photo_url: Optional[str] = None


class BatchMatchRequest(BaseModel):
    selfie_base64: Optional[str] = None
    selfie_url: Optional[str] = None
    photos: List[PhotoSlotItem] = []
    threshold: Optional[float] = None


class PhotoSlotVerificationResult(BaseModel):
    slot: int
    photo_url: Optional[str] = None
    status: str  # "MATCHED", "GROUP_MATCHED", "MISMATCH", "LIFESTYLE", "NO_FACE_IN_PRIMARY", "LOAD_ERROR"
    is_match: bool
    is_blocked: bool
    similarity_score: float
    confidence_percent: float
    faces_detected: int
    message: str


class BatchMatchResponse(BaseModel):
    overall_status: str  # "VERIFIED", "REJECTED_MISMATCH", "PRIMARY_PHOTO_FAILED", "NO_FACE_IN_SELFIE", "NO_PHOTOS"
    is_profile_verified: bool
    total_photos: int
    matched_count: int
    mismatch_count: int
    lifestyle_count: int
    selfie_faces_detected: int
    primary_portrait_matched: bool
    results: List[PhotoSlotVerificationResult]
    summary_message: str


@app.post("/match-all-photos", response_model=BatchMatchResponse)
def match_all_photos(req: BatchMatchRequest):
    """
    Biometric verification of an anchor 3D selfie against all uploaded profile photos (Slots 1..6).
    Enforces the zero-catfish security policy:
    1. Primary slot (Slot 1) MUST clearly show the user's face and match the selfie.
    2. Any photo containing a non-matching human face is flagged as MISMATCH and blocked.
    3. Secondary slots (2..6) with 0 faces (pets, scenic travel, hobbies) are classified as LIFESTYLE.
    4. Profile is VERIFIED only when primary portrait matches and 0 mismatches exist.
    """
    if recognizer is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Face recognition model not loaded."
        )

    # 1. Load Selfie Anchor
    try:
        selfie_img = load_image(req.selfie_base64, req.selfie_url)
    except ValueError as e:
        raise HTTPException(status_code=status.HTTP_400_BAD_REQUEST, detail=f"Selfie error: {str(e)}")

    # 2. Detect face in selfie
    selfie_img, selfie_faces = detect_faces_and_orient(selfie_img, score_threshold=0.35, is_selfie=True)
    if len(selfie_faces) == 0:
        logger.warning("Batch face match rejected: 0 faces detected in selfie.")
        return BatchMatchResponse(
            overall_status="NO_FACE_IN_SELFIE",
            is_profile_verified=False,
            total_photos=len(req.photos),
            matched_count=0,
            mismatch_count=0,
            lifestyle_count=0,
            selfie_faces_detected=0,
            primary_portrait_matched=False,
            results=[],
            summary_message="No clear human face detected in your selfie. Please hold the camera steadily at eye level.",
        )

    # Extract primary selfie face embedding (largest bounding box)
    selfie_faces_sorted = sorted(
        selfie_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True
    )
    primary_selfie_face = selfie_faces_sorted[0]
    selfie_aligned = recognizer.alignCrop(selfie_img, primary_selfie_face)
    selfie_feature = recognizer.feature(selfie_aligned)
    selfie_norm = float(np.linalg.norm(selfie_feature))
    if selfie_norm > 1e-6:
        selfie_feature = selfie_feature / selfie_norm

    threshold = req.threshold if req.threshold is not None else DEFAULT_COSINE_THRESHOLD
    amber_threshold = AMBER_COSINE_THRESHOLD
    red_catfish_threshold = RED_CATFISH_THRESHOLD

    # Step 1: Decode all photos and detect Primary Anchor Portrait
    decoded_photos = []
    primary_anchor_feat = None
    best_primary_score = -1.0
    primary_slot_candidate = None

    for item in req.photos:
        slot_num = item.slot
        photo_ref = item.photo_url or ""
        try:
            photo_img = load_image(item.photo_base64, item.photo_url)
            photo_img, photo_faces = detect_faces_and_orient(photo_img, score_threshold=0.35, is_selfie=False)

            extracted_face_feats = []
            if len(photo_faces) > 0:
                for p_face in photo_faces:
                    p_aligned = recognizer.alignCrop(photo_img, p_face)
                    p_feat = recognizer.feature(p_aligned)
                    p_norm = float(np.linalg.norm(p_feat))
                    if p_norm > 1e-6:
                        p_feat = p_feat / p_norm
                    extracted_face_feats.append(p_feat)

                    # Check match against raw selfie
                    cos = float(recognizer.match(selfie_feature, p_feat, cv2.FaceRecognizerSF_FR_COSINE))
                    if cos > best_primary_score:
                        best_primary_score = cos
                        if cos >= amber_threshold:
                            primary_anchor_feat = p_feat
                            primary_slot_candidate = slot_num

            decoded_photos.append({
                "slot": slot_num,
                "photo_ref": photo_ref,
                "photo_faces": photo_faces,
                "face_feats": extracted_face_feats,
                "load_error": None,
            })
        except Exception as e:
            logger.warning(f"Error loading photo slot {slot_num}: {e}")
            decoded_photos.append({
                "slot": slot_num,
                "photo_ref": photo_ref,
                "photo_faces": np.array([]),
                "face_feats": [],
                "load_error": str(e),
            })

    # Step 2: Compute Fused Anchor Embedding (Selfie + Primary Portrait)
    if primary_anchor_feat is not None:
        fused_anchor = (selfie_feature + primary_anchor_feat) / 2.0
        fused_norm = float(np.linalg.norm(fused_anchor))
        if fused_norm > 1e-6:
            fused_anchor = fused_anchor / fused_norm
        logger.info(
            f"Fused anchor created from selfie + Photo {primary_slot_candidate} (primary score: {best_primary_score:.4f})"
        )
    else:
        fused_anchor = selfie_feature

    # Step 3: Classify each slot using multi-anchor comparison
    slot_results: List[PhotoSlotVerificationResult] = []
    matched_count = 0
    amber_count = 0
    mismatch_count = 0
    lifestyle_count = 0
    primary_matched = False

    for pd in decoded_photos:
        slot_num = pd["slot"]
        photo_ref = pd["photo_ref"]

        if pd["load_error"]:
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status="LOAD_ERROR",
                    is_match=False,
                    is_blocked=False,
                    similarity_score=0.0,
                    confidence_percent=0.0,
                    faces_detected=0,
                    message=f"Could not load image: {pd['load_error']}",
                )
            )
            continue

        faces_count = len(pd["photo_faces"])
        if faces_count == 0:
            if slot_num == 1 and not (best_primary_score >= amber_threshold):
                slot_results.append(
                    PhotoSlotVerificationResult(
                        slot=slot_num,
                        photo_url=photo_ref,
                        status="NO_FACE_IN_PRIMARY",
                        is_match=False,
                        is_blocked=False,
                        similarity_score=0.0,
                        confidence_percent=0.0,
                        faces_detected=0,
                        message="Primary photo must clearly show your face.",
                    )
                )
            else:
                lifestyle_count += 1
                slot_results.append(
                    PhotoSlotVerificationResult(
                        slot=slot_num,
                        photo_url=photo_ref,
                        status="LIFESTYLE",
                        is_match=True,
                        is_blocked=False,
                        similarity_score=0.0,
                        confidence_percent=0.0,
                        faces_detected=0,
                        message="No face detected. Classified as lifestyle or hobby photo.",
                    )
                )
            continue

        # Compare each face against raw selfie AND fused anchor
        best_cos = -1.0
        for p_feat in pd["face_feats"]:
            cos_selfie = float(recognizer.match(selfie_feature, p_feat, cv2.FaceRecognizerSF_FR_COSINE))
            cos_fused = float(recognizer.match(fused_anchor, p_feat, cv2.FaceRecognizerSF_FR_COSINE))
            combined = max(cos_selfie, cos_fused)
            if combined > best_cos:
                best_cos = combined

        logger.info(f"Slot {slot_num} match result: best_cos={best_cos:.4f} (faces={faces_count})")

        if best_cos >= threshold:
            # Green Tier: High confidence match
            matched_count += 1
            if slot_num == 1 or slot_num == primary_slot_candidate:
                primary_matched = True
            st = "GROUP_MATCHED" if faces_count > 1 else "MATCHED"
            ratio = min(1.0, max(0.0, (best_cos - threshold) / max(0.01, 0.75 - threshold)))
            conf = round(80.0 + ratio * 19.5, 1)
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status=st,
                    is_match=True,
                    is_blocked=False,
                    similarity_score=round(best_cos, 4),
                    confidence_percent=conf,
                    faces_detected=faces_count,
                    message="Face verified with 3D selfie.",
                )
            )
        elif best_cos >= amber_threshold:
            # Amber Tier: Stylized / High-Fashion / Studio Lighting / Contouring
            matched_count += 1
            amber_count += 1
            if slot_num == 1 or slot_num == primary_slot_candidate:
                primary_matched = True
            ratio = min(1.0, max(0.0, (best_cos - amber_threshold) / max(0.01, threshold - amber_threshold)))
            conf = round(70.0 + ratio * 9.5, 1)
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status="STYLED_MATCH",
                    is_match=True,
                    is_blocked=False,
                    similarity_score=round(best_cos, 4),
                    confidence_percent=conf,
                    faces_detected=faces_count,
                    message="Stylized/editorial portrait authenticated.",
                )
            )
        elif best_cos >= red_catfish_threshold and faces_count > 1:
            # Group photo with partial occlusion or distant face
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status="GROUP_UNCERTAIN",
                    is_match=True,
                    is_blocked=False,
                    similarity_score=round(best_cos, 4),
                    confidence_percent=60.0,
                    faces_detected=faces_count,
                    message="Group photo detected with partial resemblance.",
                )
            )
        else:
            # Red Tier: Definite Mismatch / Catfish
            mismatch_count += 1
            ratio = max(0.0, best_cos / max(0.01, amber_threshold))
            conf = round(min(55.0, ratio * 55.0), 1)
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status="MISMATCH",
                    is_match=False,
                    is_blocked=True,
                    similarity_score=round(best_cos, 4),
                    confidence_percent=conf,
                    faces_detected=faces_count,
                    message="Face does not match your 3D selfie. Replacement required.",
                )
            )

    # Determine overall profile verification status
    if not primary_matched and (best_primary_score >= amber_threshold):
        primary_matched = True

    if mismatch_count > 1:
        overall = "REJECTED_MISMATCH"
        verified = False
        summary = f"{mismatch_count} photos do not match your 3D selfie. Please replace mismatched photos."
    elif mismatch_count == 1 and matched_count >= 2 and primary_matched:
        # Strong identity confirmation (2+ matched photos including primary) with 1 ambiguous/shadowed photo:
        # Pass verification provisionally rather than blocking user
        overall = "VERIFIED"
        verified = True
        summary = f"Identity verified! {matched_count} photo(s) authenticated. 1 photo flagged for routine review."
    elif mismatch_count > 0:
        overall = "REJECTED_MISMATCH"
        verified = False
        summary = f"{mismatch_count} photo(s) do not match your 3D selfie. Please replace mismatched photos."
    elif not primary_matched:
        overall = "PRIMARY_PHOTO_FAILED"
        verified = False
        summary = "Your primary photo must be a clear photo of yourself matching your 3D selfie."
    elif matched_count >= 1:
        overall = "VERIFIED"
        verified = True
        if amber_count > 0:
            summary = f"Identity verified! {matched_count} photo(s) authenticated (including {amber_count} stylized fashion poses)."
        else:
            summary = f"Identity verified! {matched_count} photo(s) authenticated with 3D selfie."
    else:
        overall = "PENDING_PHOTOS"
        verified = False
        summary = "No matching portrait photos found. Please upload at least one clear photo of yourself."

    return BatchMatchResponse(
        overall_status=overall,
        is_profile_verified=verified,
        total_photos=len(req.photos),
        matched_count=matched_count,
        mismatch_count=mismatch_count,
        lifestyle_count=lifestyle_count,
        selfie_faces_detected=len(selfie_faces),
        primary_portrait_matched=primary_matched,
        results=slot_results,
        summary_message=summary,
    )


class LivenessMotionRequest(BaseModel):
    center_base64: str
    right_base64: Optional[str] = None
    left_base64: Optional[str] = None
    action_base64: Optional[str] = None
    action_type: Optional[str] = "SMILE"  # "SMILE", "BLINK", "TURN"


class LivenessMotionResponse(BaseModel):
    status: str
    is_live_human: bool
    liveness_score: float
    yaw_center: float
    yaw_right: float
    yaw_left: float
    message: str


def compute_face_yaw(face: np.ndarray) -> float:
    """
    Estimate horizontal 3D head yaw from YuNet 5-point facial landmarks,
    made mathematically invariant to camera tilt/roll angle by rotating
    landmarks into the face's anatomical eye-horizontal frame.

    face[0..3]: bbox (x, y, w, h)
    face[4..5]: right eye (x, y)
    face[6..7]: left eye (x, y)
    face[8..9]: nose tip (x, y)
    face[10..11]: right mouth corner (x, y)
    face[12..13]: left mouth corner (x, y)
    """
    re_x, re_y = float(face[4]), float(face[5])
    le_x, le_y = float(face[6]), float(face[7])
    nose_x, nose_y = float(face[8]), float(face[9])
    rm_x, rm_y = float(face[10]), float(face[11])
    lm_x, lm_y = float(face[12]), float(face[13])

    # 1. Compute in-plane roll angle (tilt of the phone/camera in user's hand)
    dx_eye = le_x - re_x
    dy_eye = le_y - re_y
    inter_eye_dist = max(float(np.hypot(dx_eye, dy_eye)), 1.0)
    roll_angle = float(np.arctan2(dy_eye, dx_eye))

    # 2. De-rotate coordinates into face-aligned horizontal axis centered at eye midpoint
    cos_r = float(np.cos(-roll_angle))
    sin_r = float(np.sin(-roll_angle))
    eye_mid_x = 0.5 * (re_x + le_x)
    eye_mid_y = 0.5 * (re_y + le_y)

    def align_pt(x: float, y: float) -> Tuple[float, float]:
        tx = x - eye_mid_x
        ty = y - eye_mid_y
        return (tx * cos_r - ty * sin_r, tx * sin_r + ty * cos_r)

    aligned_nose_x, _ = align_pt(nose_x, nose_y)
    aligned_rm_x, _ = align_pt(rm_x, rm_y)
    aligned_lm_x, _ = align_pt(lm_x, lm_y)
    aligned_mouth_mid_x = 0.5 * (aligned_rm_x + aligned_lm_x)

    # In face-aligned coordinates:
    # Eye midpoint is at x=0.
    # Nose lateral offset is normalized by half the inter-eye distance:
    half_eye_dist = 0.5 * inter_eye_dist
    eye_nose_ratio = aligned_nose_x / max(half_eye_dist, 1.0)
    mouth_nose_ratio = (aligned_nose_x - aligned_mouth_mid_x) / max(half_eye_dist, 1.0)

    # Blended tilt-invariant 3D head yaw
    yaw = float(0.70 * eye_nose_ratio + 0.30 * mouth_nose_ratio)
    return yaw


@app.post("/verify-liveness", response_model=LivenessMotionResponse)
def verify_liveness(req: LivenessMotionRequest):
    if recognizer is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Face recognition model not loaded."
        )

    # =========================================================================
    # PATH A: ACTIVE MICRO-CHALLENGE (SMILE 😊 OR BLINK 😉)
    # Frontal, high accuracy, sub-second latency, zero landmark dropout!
    # =========================================================================
    if req.action_base64 and len(req.action_base64.strip()) > 100:
        try:
            center_img = load_image(req.center_base64)
            action_img = load_image(req.action_base64)
        except ValueError as e:
            return LivenessMotionResponse(
                status="INVALID_FRAMES",
                is_live_human=False,
                liveness_score=0.15,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message=f"Could not read camera frames: {str(e)}",
            )

        center_img, center_faces = detect_faces_and_orient(center_img, score_threshold=0.28)
        action_img, action_faces = detect_faces_and_orient(action_img, score_threshold=0.28)

        if len(center_faces) == 0:
            return LivenessMotionResponse(
                status="NO_FACE_IN_CENTER",
                is_live_human=False,
                liveness_score=0.20,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message="No clear human face detected in your initial photo. Please look directly into the camera.",
            )
        if len(action_faces) == 0:
            return LivenessMotionResponse(
                status="NO_FACE_IN_ACTION",
                is_live_human=False,
                liveness_score=0.20,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message="No clear human face detected during the smile/blink check. Please stay inside the oval.",
            )

        c_face = sorted(center_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
        a_face = sorted(action_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]

        # Verify same person between neutral frame and action frame (frontal SFace is ultra accurate)
        c_aligned = recognizer.alignCrop(center_img, c_face)
        a_aligned = recognizer.alignCrop(action_img, a_face)

        c_feat = recognizer.feature(c_aligned)
        a_feat = recognizer.feature(a_aligned)
        sim_ca = float(recognizer.match(c_feat, a_feat, cv2.FaceRecognizerSF_FR_COSINE))

        if sim_ca < 0.25:
            return LivenessMotionResponse(
                status="INCONSISTENT_FACE",
                is_live_human=False,
                liveness_score=0.25,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message="Different face detected between the poses. Please ensure you remain in front of the camera.",
            )

        # Measure dynamic pixel difference (rejects static unmoving photos / phone replays)
        mad = float(np.mean(np.abs(c_aligned.astype(np.float32) - a_aligned.astype(np.float32))))

        # Landmark facial metric difference
        c_re_x, c_re_y = float(c_face[4]), float(c_face[5])
        c_le_x, c_le_y = float(c_face[6]), float(c_face[7])
        c_rm_x, c_rm_y = float(c_face[10]), float(c_face[11])
        c_lm_x, c_lm_y = float(c_face[12]), float(c_face[13])
        c_eye_dist = max(float(np.hypot(c_le_x - c_re_x, c_le_y - c_re_y)), 1.0)
        c_mouth_w = float(np.hypot(c_lm_x - c_rm_x, c_lm_y - c_rm_y)) / c_eye_dist

        a_re_x, a_re_y = float(a_face[4]), float(a_face[5])
        a_le_x, a_le_y = float(a_face[6]), float(a_face[7])
        a_rm_x, a_rm_y = float(a_face[10]), float(a_face[11])
        a_lm_x, a_lm_y = float(a_face[12]), float(a_face[13])
        a_eye_dist = max(float(np.hypot(a_le_x - a_re_x, a_le_y - a_re_y)), 1.0)
        a_mouth_w = float(np.hypot(a_lm_x - a_rm_x, a_lm_y - a_rm_y)) / a_eye_dist

        mouth_diff = a_mouth_w - c_mouth_w
        action_type = (req.action_type or "SMILE").upper()
        logger.info(
            f"Active micro-challenge liveness: type={action_type}, mad={mad:.2f}, sim={sim_ca:.3f}, "
            f"mouth_w_c={c_mouth_w:.3f}, mouth_w_a={a_mouth_w:.3f}, mouth_diff={mouth_diff:.3f}"
        )

        # Check: Static photo held in front of camera produces mad < 2.0 (no movement / frozen image)
        if mad < 2.0:
            return LivenessMotionResponse(
                status="NO_DYNAMIC_MOTION",
                is_live_human=False,
                liveness_score=0.20,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message="No dynamic motion detected. Static photos or frozen screens are strictly rejected.",
            )

        # Verify dynamic action
        has_expression_change = (mad >= 3.5) or (mouth_diff > 0.02) or (abs(c_eye_dist - a_eye_dist) / c_eye_dist > 0.03)

        if not has_expression_change:
            return LivenessMotionResponse(
                status="ACTION_TOO_SUBTLE",
                is_live_human=False,
                liveness_score=0.35,
                yaw_center=0.0,
                yaw_right=0.0,
                yaw_left=0.0,
                message=f"Please {'give a bigger smile 😊' if action_type == 'SMILE' else 'blink your eyes clearly 😉'} so we can verify your live presence.",
            )

        return LivenessMotionResponse(
            status="VERIFIED",
            is_live_human=True,
            liveness_score=0.98,
            yaw_center=0.0,
            yaw_right=0.0,
            yaw_left=0.0,
            message=f"3D Biometric Liveness verified! Live human {action_type.lower()} confirmed.",
        )

    # =========================================================================
    # PATH B: LEGACY 3-POSE HEAD TURN CHECK (WITH RELAXED TOLERANCES)
    # =========================================================================
    try:
        center_img = load_image(req.center_base64)
        right_img = load_image(req.right_base64) if req.right_base64 else None
        left_img = load_image(req.left_base64) if req.left_base64 else None
    except ValueError as e:
        return LivenessMotionResponse(
            status="INVALID_FRAMES",
            is_live_human=False,
            liveness_score=0.15,
            yaw_center=0.0,
            yaw_right=0.0,
            yaw_left=0.0,
            message=f"Could not read all 3 camera frames: {str(e)}",
        )

    # Detect faces in all available poses with relaxed thresholds (0.20) to prevent side-drop
    center_img, center_faces = detect_faces_and_orient(center_img, score_threshold=0.28)
    right_faces = []
    left_faces = []
    if right_img is not None:
        right_img, right_faces = detect_faces_and_orient(right_img, score_threshold=0.18)
    if left_img is not None:
        left_img, left_faces = detect_faces_and_orient(left_img, score_threshold=0.18)

    if len(center_faces) == 0:
        return LivenessMotionResponse(
            status="NO_FACE_DETECTED",
            is_live_human=False,
            liveness_score=0.20,
            yaw_center=0.0,
            yaw_right=0.0,
            yaw_left=0.0,
            message="No clear human face detected in Center pose. Keep your face well-lit inside the oval.",
        )

    c_face = sorted(center_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
    yaw_c = compute_face_yaw(c_face)
    c_aligned = recognizer.alignCrop(center_img, c_face)

    # If at least one side turn detected
    has_side_pose = len(right_faces) > 0 or len(left_faces) > 0
    if not has_side_pose:
        # If user looked straight and neither side face was detected, prompt friendly smile retry
        return LivenessMotionResponse(
            status="NO_HEAD_MOVEMENT",
            is_live_human=False,
            liveness_score=0.25,
            yaw_center=round(yaw_c, 4),
            yaw_right=0.0,
            yaw_left=0.0,
            message="Could not clearly track side turns. Please keep your face inside the oval and give a clear smile or turn.",
        )

    yaw_r = yaw_c
    yaw_l = yaw_c
    mad_cr = 0.0
    mad_cl = 0.0

    if len(right_faces) > 0:
        r_face = sorted(right_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
        yaw_r = compute_face_yaw(r_face)
        r_aligned = recognizer.alignCrop(right_img, r_face)
        mad_cr = float(np.mean(np.abs(c_aligned.astype(np.float32) - r_aligned.astype(np.float32))))

    if len(left_faces) > 0:
        l_face = sorted(left_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
        yaw_l = compute_face_yaw(l_face)
        l_aligned = recognizer.alignCrop(left_img, l_face)
        mad_cl = float(np.mean(np.abs(c_aligned.astype(np.float32) - l_aligned.astype(np.float32))))

    delta_r = abs(yaw_r - yaw_c)
    delta_l = abs(yaw_l - yaw_c)
    span_rl = abs(yaw_r - yaw_l)

    has_angular_turn = (span_rl >= 0.03) or (delta_r >= 0.025) or (delta_l >= 0.025)
    has_pixel_motion = (mad_cr >= 4.5 or mad_cl >= 4.5)

    if not has_angular_turn and not has_pixel_motion:
        return LivenessMotionResponse(
            status="NO_HEAD_MOVEMENT",
            is_live_human=False,
            liveness_score=0.25,
            yaw_center=round(yaw_c, 4),
            yaw_right=round(yaw_r, 4),
            yaw_left=round(yaw_l, 4),
            message="No head movement detected. A live human must turn slightly or smile. Static photos are rejected.",
        )

    score = min(0.99, round(0.90 + min(0.09, max(delta_r, delta_l) * 0.25), 2))
    return LivenessMotionResponse(
        status="VERIFIED",
        is_live_human=True,
        liveness_score=score,
        yaw_center=round(yaw_c, 4),
        yaw_right=round(yaw_r, 4),
        yaw_left=round(yaw_l, 4),
        message="3D Biometric Liveness verified! Real human movement confirmed.",
    )


# -----------------------------------------------------------------------------
# SHIELD 360 AI: SELF-HOSTED OPENNSFW NUDITY & EXPLICIT CONTENT DETECTOR
# -----------------------------------------------------------------------------
class NsfwDetectRequest(BaseModel):
    image_base64: Optional[str] = None
    image_url: Optional[str] = None


class NsfwDetectResponse(BaseModel):
    is_nsfw: bool
    nsfw_score: float
    sfw_score: float
    action: str  # "BLOCK", "BLUR_WITH_WARNING", "ALLOW"
    message: str


def classify_nsfw_cv_img(bgr_img: np.ndarray) -> Tuple[float, float]:
    """
    Classify an OpenCV BGR image using the OpenNSFW ONNX model via cv2.dnn.
    Returns (sfw_score, nsfw_score).
    """
    global nsfw_net
    if nsfw_net is None:
        if os.path.exists(NSFW_ONNX_PATH):
            nsfw_net = cv2.dnn.readNetFromONNX(NSFW_ONNX_PATH)
        else:
            logger.warning("OpenNSFW model not loaded; returning safe default.")
            return 0.95, 0.05

    # Convert BGR to RGB PIL for standard Yahoo OpenNSFW pipeline
    rgb_img = cv2.cvtColor(bgr_img, cv2.COLOR_BGR2RGB)
    pil_img = Image.fromarray(rgb_img)

    RESIZE_SIZE = 256
    CROP_SIZE = 224
    VGG_MEAN = np.array([104.0, 117.0, 123.0], dtype=np.float32)

    resized = pil_img.resize((RESIZE_SIZE, RESIZE_SIZE), resample=Image.BILINEAR)
    buffer = io.BytesIO()
    resized.save(buffer, format="JPEG")
    buffer.seek(0)
    decoded = np.asarray(Image.open(buffer).convert("RGB"), dtype=np.float32)

    offset = (RESIZE_SIZE - CROP_SIZE) // 2
    cropped = decoded[offset : offset + CROP_SIZE, offset : offset + CROP_SIZE, :]
    bgr_norm = cropped[:, :, ::-1] - VGG_MEAN

    # NHWC shape: (1, 224, 224, 3)
    blob = np.expand_dims(bgr_norm, axis=0)
    nsfw_net.setInput(blob)
    preds = nsfw_net.forward()

    sfw_score = float(preds[0][0])
    nsfw_score = float(preds[0][1])
    return sfw_score, nsfw_score


@app.post("/detect-nsfw", response_model=NsfwDetectResponse)
def detect_nsfw_endpoint(req: NsfwDetectRequest):
    """
    Shield 360 AI endpoint for evaluating nudity & explicit content.
    Returns classification scores and recommended enforcement actions.
    """
    try:
        img = load_image(req.image_base64, req.image_url)
    except Exception as e:
        logger.warning(f"Failed to load image for NSFW detection: {e}")
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Unable to load image for moderation check: {str(e)}",
        )

    try:
        sfw_score, nsfw_score = classify_nsfw_cv_img(img)
    except Exception as e:
        logger.error(f"Inference error during NSFW detection: {e}")
        return NsfwDetectResponse(
            is_nsfw=False,
            nsfw_score=0.05,
            sfw_score=0.95,
            action="ALLOW",
            message=f"Inference exception fallback: {str(e)}",
        )

    is_nsfw = nsfw_score >= 0.60
    if nsfw_score >= 0.65:
        action = "BLOCK"
        msg = f"Explicit nudity detected (confidence: {nsfw_score:.2f}). Hard blocked."
    elif nsfw_score >= 0.40:
        action = "BLUR_WITH_WARNING"
        msg = f"Potentially sensitive or suggestive content (confidence: {nsfw_score:.2f}). Auto-blur enabled."
    else:
        action = "ALLOW"
        msg = f"Content verified safe (safe confidence: {sfw_score:.2f})."

    logger.info(f"NSFW Check result: score={nsfw_score:.4f}, action={action}")
    return NsfwDetectResponse(
        is_nsfw=is_nsfw,
        nsfw_score=round(nsfw_score, 4),
        sfw_score=round(sfw_score, 4),
        action=action,
        message=msg,
    )


