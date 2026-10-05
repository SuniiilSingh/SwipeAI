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
    if "/uploads/" in raw_str:
        filename = raw_str.split("/uploads/")[-1]
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
