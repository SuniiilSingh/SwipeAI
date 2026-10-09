import base64
import io
import logging
import os
import re
from typing import Optional, Tuple

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
    Estimate horizontal 3D head yaw from YuNet 5-point facial landmarks:
    face[0..3]: bbox (x, y, w, h)
    face[4..5]: right eye (x, y)
    face[6..7]: left eye (x, y)
    face[8..9]: nose tip (x, y)
    face[10..11]: right mouth corner (x, y)
    face[12..13]: left mouth corner (x, y)
    Returns a signed yaw ratio (~0.0 when facing straight ahead; +/-0.12 to +/-0.45 when turned right/left).
    """
    bbox_x = float(face[0])
    bbox_w = max(float(face[2]), 1.0)
    re_x = float(face[4])
    le_x = float(face[6])
    nose_x = float(face[8])
    rm_x = float(face[10])
    lm_x = float(face[12])

    eye_mid_x = 0.5 * (re_x + le_x)
    mouth_mid_x = 0.5 * (rm_x + lm_x)
    anchor_mid_x = 0.65 * eye_mid_x + 0.35 * mouth_mid_x
    inter_eye_dist = max(abs(le_x - re_x), bbox_w * 0.22, 1.0)

    eye_nose_yaw = (nose_x - anchor_mid_x) / inter_eye_dist
    bbox_center_x = bbox_x + 0.5 * bbox_w
    bbox_nose_yaw = (nose_x - bbox_center_x) / bbox_w

    return float(0.65 * eye_nose_yaw + 0.35 * (bbox_nose_yaw * 2.5))


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

    # Detect faces in all 3 poses (slightly lower score threshold for side-turned poses)
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

    # Pick largest face in each frame
    c_face = sorted(center_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
    r_face = sorted(right_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]
    l_face = sorted(left_faces, key=lambda f: float(f[2]) * float(f[3]), reverse=True)[0]

    yaw_c = compute_face_yaw(c_face)
    yaw_r = compute_face_yaw(r_face)
    yaw_l = compute_face_yaw(l_face)

    delta_r = abs(yaw_r - yaw_c)
    delta_l = abs(yaw_l - yaw_c)
    span_rl = abs(yaw_r - yaw_l)
    opposite_sides = ((yaw_r - yaw_c) * (yaw_l - yaw_c)) <= 0.01

    # Also measure aligned face pixel difference to catch completely static/unmoving faces
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

    # Reject if user stayed still / constant without turning their head
    if delta_r < 0.065 or delta_l < 0.065 or span_rl < 0.11 or not opposite_sides or (mad_cr < 11.0 and mad_cl < 11.0):
        if delta_r < 0.065 and delta_l < 0.065:
            reason = "No 3D head movement detected! You stayed still—please turn your head clearly to the Right and Left when prompted."
        elif not opposite_sides or span_rl < 0.11:
            reason = "Incomplete 3D head turn! Please look straight for Center, then turn clearly to the Right, and then to the Left."
        elif delta_r < 0.065:
            reason = "Right head turn was not detected. Please turn your head clearly to the Right on step 2."
        else:
            reason = "Left head turn was not detected. Please turn your head clearly to the Left on step 3."

        return LivenessMotionResponse(
            status="NO_HEAD_MOVEMENT",
            is_live_human=False,
            liveness_score=0.35,
            yaw_center=round(yaw_c, 4),
            yaw_right=round(yaw_r, 4),
            yaw_left=round(yaw_l, 4),
            message=reason,
        )

    # Verify same human across frames (lenient threshold for angled views)
    c_feat = recognizer.feature(c_aligned)
    r_feat = recognizer.feature(r_aligned)
    l_feat = recognizer.feature(l_aligned)
    sim_cr = float(recognizer.match(c_feat, r_feat, cv2.FaceRecognizerSF_FR_COSINE))
    sim_cl = float(recognizer.match(c_feat, l_feat, cv2.FaceRecognizerSF_FR_COSINE))

    if sim_cr < 0.18 or sim_cl < 0.18:
        return LivenessMotionResponse(
            status="INCONSISTENT_FACE",
            is_live_human=False,
            liveness_score=0.40,
            yaw_center=round(yaw_c, 4),
            yaw_right=round(yaw_r, 4),
            yaw_left=round(yaw_l, 4),
            message="Face changed or left the frame during the turn. Please keep your face visible throughout all 3 steps.",
        )

    score = min(0.99, round(0.88 + min(0.11, span_rl * 0.25), 2))
    return LivenessMotionResponse(
        status="VERIFIED",
        is_live_human=True,
        liveness_score=score,
        yaw_center=round(yaw_c, 4),
        yaw_right=round(yaw_r, 4),
        yaw_left=round(yaw_l, 4),
        message="3D Biometric Liveness verified! Real head movement confirmed.",
    )

