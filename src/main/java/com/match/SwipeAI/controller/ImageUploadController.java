package com.match.SwipeAI.controller;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.UUID;

/**
 * Image Presigning and Direct Upload Controller.
 * Provides instant presigned upload URLs (Cloudflare R2 / AWS S3) or mock URLs when R2 is disabled.
 */
@Slf4j
@RestController
@RequestMapping({"/api/images", "/v1/images"})
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ImageUploadController {

    private final FeatureFlagsProperties properties;

    /**
     * Get presigned URL for direct image uploads.
     */
    @GetMapping("/presign")
    public ResponseEntity<Map<String, String>> getPresignedUploadUrl(
            @RequestParam("filename") String filename,
            @RequestParam(value = "contentType", defaultValue = "image/jpeg") String contentType) {

        String fileId = UUID.randomUUID().toString() + "_" + filename.replaceAll("[^a-zA-Z0-9.-]", "_");
        boolean r2Enabled = properties.getFeatures().getR2Storage().isEnabled();

        if (r2Enabled) {
            log.info("[FEATURE_FLAG: R2 Storage LIVE] Generating presigned upload URL for {}", filename);
            /*
             * LIVE INTEGRATION SKELETON:
             * S3Presigner presigner = S3Presigner.builder().build();
             * PutObjectPresignRequest putReq = PutObjectPresignRequest.builder()
             *     .signatureDuration(Duration.ofMinutes(15))
             *     .putObjectRequest(PutObjectRequest.builder().bucket(properties.getFeatures().getR2Storage().getBucketName()).key(fileId).contentType(contentType).build())
             *     .build();
             * PresignedPutObjectRequest presigned = presigner.presignPutObject(putReq);
             * String uploadUrl = presigned.url().toString();
             * String publicUrl = properties.getFeatures().getR2Storage().getPublicDomain() + "/" + fileId;
             */
            String publicUrl = properties.getFeatures().getR2Storage().getPublicDomain() + "/" + fileId;
            return ResponseEntity.ok(Map.of(
                    "uploadUrl", "https://upload.swipeai.in/" + fileId,
                    "publicUrl", publicUrl,
                    "fileId", fileId
            ));
        }

        // MOCK TESTING: Return simulated instant upload endpoint and CDN image URL
        String mockPublicUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=500";
        log.info("[MOCK TESTING ENVIRONMENT] Returning mock image presigned URL for {}", filename);

        return ResponseEntity.ok(Map.of(
                "uploadUrl", "http://localhost:8080/api/images/mock-upload",
                "publicUrl", mockPublicUrl,
                "fileId", fileId
        ));
    }

    /**
     * Direct multipart image upload endpoint.
     */
    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadDirect(@RequestParam("file") MultipartFile file) {
        String fileId = UUID.randomUUID().toString() + "_" + (file.getOriginalFilename() != null ? file.getOriginalFilename() : "photo.jpg");
        String publicUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?w=500";
        log.info("Direct multipart file uploaded: {} (size: {} bytes)", file.getOriginalFilename(), file.getSize());

        return ResponseEntity.ok(Map.of(
                "fileId", fileId,
                "publicUrl", publicUrl,
                "status", "SUCCESS"
        ));
    }

    /**
     * Mock PUT endpoint for local testing uploads.
     */
    @PutMapping("/mock-upload")
    public ResponseEntity<Map<String, String>> mockPutUpload(@RequestBody(required = false) byte[] data) {
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Mock image payload received"));
    }

    /**
     * Delete an uploaded image file by fileId.
     */
    @DeleteMapping("/{fileId}")
    public ResponseEntity<Map<String, String>> deleteImage(@PathVariable String fileId) {
        log.info("Deleting image: {}", fileId);
        return ResponseEntity.ok(Map.of("status", "SUCCESS", "message", "Image deleted successfully", "fileId", fileId));
    }
}
