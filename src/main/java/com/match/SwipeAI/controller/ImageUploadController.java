package com.match.SwipeAI.controller;

import com.match.SwipeAI.service.integration.R2StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * Production Image Upload Controller.
 * Supports direct multipart uploads to Cloudflare R2 and presigned S3 URLs.
 */
@Slf4j
@RestController
@RequestMapping({"/api/images", "/v1/images"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageUploadController {

    private final R2StorageService r2StorageService;

    /**
     * Direct multipart image upload endpoint.
     * Uploads the file to Cloudflare R2 (or local fallback) and returns the public CDN URL.
     */
    @PostMapping(value = "/upload", consumes = {"multipart/form-data"})
    public ResponseEntity<?> uploadDirect(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No file provided or file is empty"));
        }

        try {
            String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg";
            String contentType = file.getContentType() != null ? file.getContentType() : "image/jpeg";
            byte[] bytes = file.getBytes();

            log.info("Processing direct image upload: {} ({} bytes, type: {})", filename, bytes.length, contentType);
            Map<String, String> result = r2StorageService.uploadFile(filename, contentType, bytes);

            return ResponseEntity.ok(Map.of(
                    "fileId", result.get("fileId"),
                    "publicUrl", result.get("publicUrl"),
                    "storage", result.getOrDefault("storage", "R2"),
                    "status", "SUCCESS"
            ));
        } catch (IOException e) {
            log.error("Failed to read uploaded file payload: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process image payload: " + e.getMessage()));
        } catch (Exception e) {
            log.error("Image upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload error: " + e.getMessage()));
        }
    }

    /**
     * Direct JSON Base64 image upload endpoint.
     * Guaranteed cross-platform fallback for environments with strict/non-standard FormData implementations.
     */
    @PostMapping(value = {"/upload-base64", "/upload"}, consumes = {"application/json"})
    public ResponseEntity<?> uploadBase64(@RequestBody Map<String, String> body) {
        String base64Data = body.get("base64Data");
        if (base64Data == null || base64Data.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No base64Data provided"));
        }

        try {
            if (base64Data.contains(",")) {
                base64Data = base64Data.substring(base64Data.indexOf(",") + 1);
            }
            byte[] bytes = java.util.Base64.getDecoder().decode(base64Data.trim());

            String filename = body.getOrDefault("filename", "photo.jpg");
            String contentType = body.getOrDefault("contentType", "image/jpeg");

            log.info("Processing base64 image upload: {} ({} bytes, type: {})", filename, bytes.length, contentType);
            Map<String, String> result = r2StorageService.uploadFile(filename, contentType, bytes);

            return ResponseEntity.ok(Map.of(
                    "fileId", result.get("fileId"),
                    "publicUrl", result.get("publicUrl"),
                    "storage", result.getOrDefault("storage", "R2"),
                    "status", "SUCCESS"
            ));
        } catch (Exception e) {
            log.error("Base64 image upload failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Upload error: " + e.getMessage()));
        }
    }

    /**
     * Get presigned S3/R2 URL for client-side direct uploads.
     */
    @GetMapping("/presign")
    public ResponseEntity<Map<String, String>> getPresignedUploadUrl(
            @RequestParam("filename") String filename,
            @RequestParam(value = "contentType", defaultValue = "image/jpeg") String contentType) {

        log.info("Generating presigned upload URL for: {} ({})", filename, contentType);
        Map<String, String> presigned = r2StorageService.generatePresignedPutUrl(filename, contentType);
        return ResponseEntity.ok(presigned);
    }

    /**
     * Delete an uploaded image by its fileId.
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, Object>> deleteImage(@PathVariable String fileId) {
        log.info("Request to delete image: {}", fileId);
        boolean deleted = r2StorageService.deleteFile(fileId);
        return ResponseEntity.ok(Map.of(
                "status", deleted ? "SUCCESS" : "NOT_FOUND_OR_SKIPPED",
                "message", deleted ? "Image deleted successfully" : "Image delete attempted",
                "fileId", fileId
        ));
    }
}
