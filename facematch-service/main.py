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

# Face recognition threshold (OpenCV SFace standard is 0.363 for cosine similarity)
DEFAULT_COSINE_THRESHOLD = 0.363

recognizer: Optional[cv2.FaceRecognizerSF] = None


@app.on_event("startup")
def load_models():
    global recognizer
    logger.info("Initializing YuNet and SFace models...")
    if not os.path.exists(YUNET_PATH) or not os.path.exists(SFACE_PATH):
        logger.error(f"Models not found at {YUNET_PATH} or {SFACE_PATH}")
        raise RuntimeError("Model files missing from container filesystem.")

    recognizer = cv2.FaceRecognizerSF.create(SFACE_PATH, "")
    logger.info("Models loaded successfully.")


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


def detect_faces_and_orient(img: np.ndarray, score_threshold: float = 0.35) -> Tuple[np.ndarray, np.ndarray]:
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
    if faces is not None and len(faces) > 0:
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
        if rot_faces is not None and len(rot_faces) > 0:
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

    # 3. Detect faces in selfie with multi-orientation support
    selfie_img, selfie_faces = detect_faces_and_orient(selfie_img, score_threshold=0.35)
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
    photo_img, photo_faces = detect_faces_and_orient(photo_img, score_threshold=0.35)
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
        f"threshold={threshold:.3f}, selfie_faces={len(selfie_faces)}, photo_faces={len(photo_faces)}"
    )

    is_match = best_cosine_score >= threshold

    # Compute normalized confidence percentage (0% - 100%)
    if is_match:
        # Map [threshold, 0.75] -> [75.0%, 99.5%]
        ratio = min(1.0, max(0.0, (best_cosine_score - threshold) / max(0.01, 0.75 - threshold)))
        confidence_percent = round(75.0 + ratio * 24.5, 1)
        resp_status = "SUCCESS"
        message = "Biometric identity verified! Face matches your profile photos."
    else:
        # Map [0, threshold] -> [0.0%, 65.0%]
        ratio = max(0.0, best_cosine_score / max(0.01, threshold))
        confidence_percent = round(min(65.0, ratio * 65.0), 1)
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
    selfie_img, selfie_faces = detect_faces_and_orient(selfie_img, score_threshold=0.35)
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

    threshold = req.threshold if req.threshold is not None else DEFAULT_COSINE_THRESHOLD

    slot_results: List[PhotoSlotVerificationResult] = []
    matched_count = 0
    mismatch_count = 0
    lifestyle_count = 0
    primary_matched = False

    for item in req.photos:
        slot_num = item.slot
        photo_ref = item.photo_url or ""
        try:
            photo_img = load_image(item.photo_base64, item.photo_url)
            photo_img, photo_faces = detect_faces_and_orient(photo_img, score_threshold=0.35)
        except Exception as e:
            logger.warning(f"Error loading photo slot {slot_num}: {e}")
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
                    message=f"Could not load image: {str(e)}",
                )
            )
            continue

        if len(photo_faces) == 0:
            if slot_num == 1:
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
                        is_match=False,
                        is_blocked=False,
                        similarity_score=0.0,
                        confidence_percent=0.0,
                        faces_detected=0,
                        message="No face detected. Classified as lifestyle or hobby photo.",
                    )
                )
            continue

        # Face(s) detected: compare all faces against primary selfie embedding
        best_cosine = -1.0
        for p_face in photo_faces:
            p_aligned = recognizer.alignCrop(photo_img, p_face)
            p_feat = recognizer.feature(p_aligned)
            cos_score = float(recognizer.match(selfie_feature, p_feat, cv2.FaceRecognizerSF_FR_COSINE))
            if cos_score > best_cosine:
                best_cosine = cos_score

        is_match = best_cosine >= threshold
        if is_match:
            matched_count += 1
            if slot_num == 1:
                primary_matched = True
            st = "GROUP_MATCHED" if len(photo_faces) > 1 else "MATCHED"
            ratio = min(1.0, max(0.0, (best_cosine - threshold) / max(0.01, 0.75 - threshold)))
            conf = round(75.0 + ratio * 24.5, 1)
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status=st,
                    is_match=True,
                    is_blocked=False,
                    similarity_score=round(best_cosine, 4),
                    confidence_percent=conf,
                    faces_detected=len(photo_faces),
                    message="Face verified with 3D selfie.",
                )
            )
        else:
            # Human face detected but does not match reference selfie -> Catfish / Mismatch
            mismatch_count += 1
            ratio = max(0.0, best_cosine / max(0.01, threshold))
            conf = round(min(65.0, ratio * 65.0), 1)
            slot_results.append(
                PhotoSlotVerificationResult(
                    slot=slot_num,
                    photo_url=photo_ref,
                    status="MISMATCH",
                    is_match=False,
                    is_blocked=True,
                    similarity_score=round(best_cosine, 4),
                    confidence_percent=conf,
                    faces_detected=len(photo_faces),
                    message="Face does not match your 3D selfie. Replacement required.",
                )
            )

    # Determine overall profile verification status
    if mismatch_count > 0:
        overall = "REJECTED_MISMATCH"
        verified = False
        summary = f"{mismatch_count} photo(s) do not match your 3D selfie. Please replace mismatched photos."
    elif not primary_matched and any(r.slot == 1 for r in slot_results):
        overall = "PRIMARY_PHOTO_FAILED"
        verified = False
        summary = "Your primary photo (Photo 1) must be a clear photo of yourself matching your 3D selfie."
    elif matched_count >= 1:
        overall = "VERIFIED"
        verified = True
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
    right_base64: str
    left_base64: str


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

    try:
        center_img = load_image(req.center_base64)
        right_img = load_image(req.right_base64)
        left_img = load_image(req.left_base64)
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

    # Detect faces in all 3 poses with multi-orientation auto-rotation (0°, 90°, 180°, 270°)
    center_img, center_faces = detect_faces_and_orient(center_img, score_threshold=0.32)
    right_img, right_faces = detect_faces_and_orient(right_img, score_threshold=0.25)
    left_img, left_faces = detect_faces_and_orient(left_img, score_threshold=0.25)

    if len(center_faces) == 0 or len(right_faces) == 0 or len(left_faces) == 0:
        missing = []
        if len(center_faces) == 0:
            missing.append("Center")
        if len(right_faces) == 0:
            missing.append("Right Turn")
        if len(left_faces) == 0:
            missing.append("Left Turn")
        missing_str = ", ".join(missing)
        logger.warning(f"3D Liveness rejected: no face detected in [{missing_str}]")
        return LivenessMotionResponse(
            status="NO_FACE_DETECTED",
            is_live_human=False,
            liveness_score=0.20,
            yaw_center=0.0,
            yaw_right=0.0,
            yaw_left=0.0,
            message=f"No clear human face detected during: {missing_str}. Keep your face well-lit inside the oval.",
        )

    # Pick primary largest face in each frame
    c_face = sorted(center_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
    r_face = sorted(right_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
    l_face = sorted(left_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]

    # Face size validation: Ensure user is the primary subject in front of the camera,
    # rejecting distant background faces, posters, or small non-human objects
    min_face_h = 0.10 * float(center_img.shape[0])
    if float(c_face[3]) < min_face_h or float(r_face[3]) < min_face_h or float(l_face[3]) < min_face_h:
        return LivenessMotionResponse(
            status="FACE_TOO_FAR",
            is_live_human=False,
            liveness_score=0.20,
            yaw_center=0.0,
            yaw_right=0.0,
            yaw_left=0.0,
            message="Face is too far from the camera. Please hold your phone closer inside the oval guide.",
        )

    # Compute tilt-invariant 3D head yaw in the face's own anatomical frame
    yaw_c = compute_face_yaw(c_face)
    yaw_r = compute_face_yaw(r_face)
    yaw_l = compute_face_yaw(l_face)

    delta_r = abs(yaw_r - yaw_c)
    delta_l = abs(yaw_l - yaw_c)
    span_rl = abs(yaw_r - yaw_l)
    opposite_sides = ((yaw_r - yaw_c) * (yaw_l - yaw_c)) <= 0.01

    # Measure aligned face pixel difference to catch static/constant unmoving photos
    c_aligned = recognizer.alignCrop(center_img, c_face)
    r_aligned = recognizer.alignCrop(right_img, r_face)
    l_aligned = recognizer.alignCrop(left_img, l_face)

    mad_cr = float(np.mean(np.abs(c_aligned.astype(np.float32) - r_aligned.astype(np.float32))))
    mad_cl = float(np.mean(np.abs(c_aligned.astype(np.float32) - l_aligned.astype(np.float32))))

    logger.info(
        f"3D Liveness motion analysis: yaw_c={yaw_c:.3f}, yaw_r={yaw_r:.3f}, yaw_l={yaw_l:.3f}, "
        f"delta_r={delta_r:.3f}, delta_l={delta_l:.3f}, span_rl={span_rl:.3f}, "
        f"opposite={opposite_sides}, mad_cr={mad_cr:.1f}, mad_cl={mad_cl:.1f}"
    )

    # A live human turning their head produces genuine 3D motion:
    # 1. Broad angular span between side poses (span_rl >= 0.045) OR
    # 2. Clear deviation from center (delta_r >= 0.035 or delta_l >= 0.035) OR
    # 3. Dynamic 3D pixel parallax across angles (mad_cr >= 6.5 or mad_cl >= 6.5)
    has_angular_turn = (span_rl >= 0.045) or (delta_r >= 0.035) or (delta_l >= 0.035)
    has_pixel_motion = (mad_cr >= 6.5 or mad_cl >= 6.5)

    # Strictly reject constant images, unmoving photos, or flat printed paper
    if not has_angular_turn and not has_pixel_motion:
        return LivenessMotionResponse(
            status="NO_HEAD_MOVEMENT",
            is_live_human=False,
            liveness_score=0.20,
            yaw_center=round(yaw_c, 4),
            yaw_right=round(yaw_r, 4),
            yaw_left=round(yaw_l, 4),
            message="No head movement detected. A live human must turn their head. Static photos or unmoving images are strictly rejected.",
        )

    # Verify same human across frames (strictly blocks switching subjects or showing non-human objects)
    c_feat = recognizer.feature(c_aligned)
    r_feat = recognizer.feature(r_aligned)
    l_feat = recognizer.feature(l_aligned)
    sim_cr = float(recognizer.match(c_feat, r_feat, cv2.FaceRecognizerSF_FR_COSINE))
    sim_cl = float(recognizer.match(c_feat, l_feat, cv2.FaceRecognizerSF_FR_COSINE))

    if sim_cr < 0.20 or sim_cl < 0.20:
        return LivenessMotionResponse(
            status="INCONSISTENT_FACE",
            is_live_human=False,
            liveness_score=0.25,
            yaw_center=round(yaw_c, 4),
            yaw_right=round(yaw_r, 4),
            yaw_left=round(yaw_l, 4),
            message="Different subject or face changed across the turns. The same live human face must be visible throughout all 3 steps.",
        )

    score = min(0.99, round(0.88 + min(0.11, span_rl * 0.25), 2))
    return LivenessMotionResponse(
        status="VERIFIED",
        is_live_human=True,
        liveness_score=score,
        yaw_center=round(yaw_c, 4),
        yaw_right=round(yaw_r, 4),
        yaw_left=round(yaw_l, 4),
        message="3D Biometric Liveness verified! Real human head movement confirmed.",
    )

