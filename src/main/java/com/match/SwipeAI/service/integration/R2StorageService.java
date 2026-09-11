package com.match.SwipeAI.service.integration;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileOutputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Production-grade Cloudflare R2 Storage Service.
 * Implements S3-compatible SigV4 direct upload, presigning, and deletion.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class R2StorageService {

    private final FeatureFlagsProperties properties;

    private static final DateTimeFormatter AMZ_DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_STAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private Path localStorageDir;

    @PostConstruct
    public void init() {
        System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host,Host");
        try {
            localStorageDir = Paths.get("uploads").toAbsolutePath().normalize();
            if (!Files.exists(localStorageDir)) {
                Files.createDirectories(localStorageDir);
            }
        } catch (Exception e) {
            log.warn("Failed to initialize local uploads directory: {}", e.getMessage());
        }
    }

    /**
     * Upload an image file directly to Cloudflare R2 (or local fallback storage if R2 disabled).
     *
     * @param originalFilename Original file name
     * @param contentType      MIME type (e.g., image/jpeg)
     * @param data             File binary payload
     * @return Map containing fileId and publicUrl
     */
    public Map<String, String> uploadFile(String originalFilename, String contentType, byte[] data) {
        String cleanName = (originalFilename != null && !originalFilename.isBlank())
                ? originalFilename.replaceAll("[^a-zA-Z0-9.-]", "_")
                : "photo.jpg";
        String fileId = UUID.randomUUID().toString() + "_" + cleanName;

        var r2 = properties.getFeatures().getR2Storage();

        if (r2.isEnabled() && r2.getAccountId() != null && !r2.getAccountId().isBlank()
                && r2.getAccessKeyId() != null && !r2.getAccessKeyId().isBlank()
                && r2.getSecretAccessKey() != null && !r2.getSecretAccessKey().isBlank()) {
            try {
                uploadToR2(r2, fileId, contentType, data);
                String domain = r2.getPublicDomain().replaceAll("/+$", "");
                String publicUrl = domain + "/" + fileId;
                log.info("Successfully uploaded image {} to Cloudflare R2: {}", fileId, publicUrl);
                return Map.of(
                        "fileId", fileId,
                        "publicUrl", publicUrl,
                        "storage", "R2"
                );
            } catch (Exception e) {
                log.error("Failed to upload to Cloudflare R2: {}. Falling back to local storage.", e.getMessage(), e);
            }
        }

        // Fallback: Store locally under uploads/
        try {
            Path target = localStorageDir.resolve(fileId);
            try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                fos.write(data);
            }
            String publicUrl = "/uploads/" + fileId;
            log.info("Stored file locally under {}: {}", target, publicUrl);
            return Map.of(
                    "fileId", fileId,
                    "publicUrl", publicUrl,
                    "storage", "LOCAL"
            );
        } catch (Exception e) {
            log.error("Failed to save file to local storage: {}", e.getMessage(), e);
            throw new RuntimeException("Image upload failed: " + e.getMessage(), e);
        }
    }

    /**
     * Generate a presigned PUT URL for direct client-to-R2 upload.
     */
    public Map<String, String> generatePresignedPutUrl(String filename, String contentType) {
        String cleanName = (filename != null && !filename.isBlank())
                ? filename.replaceAll("[^a-zA-Z0-9.-]", "_")
                : "photo.jpg";
        String fileId = UUID.randomUUID().toString() + "_" + cleanName;
        var r2 = properties.getFeatures().getR2Storage();

        String domain = r2.getPublicDomain().replaceAll("/+$", "");
        String publicUrl = domain + "/" + fileId;

        if (r2.isEnabled() && r2.getAccountId() != null && !r2.getAccountId().isBlank()) {
            try {
                String presignedUrl = generateR2PresignedUrl(r2, fileId, contentType, 900);
                return Map.of(
                        "fileId", fileId,
                        "uploadUrl", presignedUrl,
                        "publicUrl", publicUrl
                );
            } catch (Exception e) {
                log.warn("Failed to create presigned URL: {}. Returning direct upload endpoint.", e.getMessage());
            }
        }

        return Map.of(
                "fileId", fileId,
                "uploadUrl", "/v1/images/upload",
                "publicUrl", publicUrl
        );
    }

    /**
     * Delete an object from R2 (and local storage).
     */
    public boolean deleteFile(String fileId) {
        var r2 = properties.getFeatures().getR2Storage();
        boolean deleted = false;

        if (r2.isEnabled() && r2.getAccountId() != null && !r2.getAccountId().isBlank()) {
            try {
                deleteFromR2(r2, fileId);
                deleted = true;
                log.info("Deleted {} from Cloudflare R2", fileId);
            } catch (Exception e) {
                log.error("Failed to delete {} from Cloudflare R2: {}", fileId, e.getMessage());
            }
        }

        try {
            Path target = localStorageDir.resolve(fileId);
            if (Files.exists(target)) {
                Files.delete(target);
                deleted = true;
                log.info("Deleted {} from local storage", fileId);
            }
        } catch (Exception ignored) {
        }

        return deleted;
    }

    // =========================================================================
    // S3 SigV4 Low-Level Implementation
    // =========================================================================

    private void uploadToR2(FeatureFlagsProperties.R2Storage r2, String key, String contentType, byte[] data) throws Exception {
        String host = r2.getAccountId() + ".r2.cloudflarestorage.com";
        String bucket = r2.getBucketName();
        String uri = "/" + bucket + "/" + key;
        String endpoint = "https://" + host + uri;

        Instant now = Instant.now();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        String dateStamp = DATE_STAMP_FORMAT.format(now);

        String contentHash = toHex(sha256(data));
        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + contentHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

        String canonicalRequest = "PUT\n"
                + uri + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + contentHash;

        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateStamp + "/auto/s3/aws4_request";
        String stringToSign = algorithm + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + toHex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = getSignatureKey(r2.getSecretAccessKey(), dateStamp, "auto", "s3");
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        String authHeader = algorithm + " Credential=" + r2.getAccessKeyId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", contentHash)
                .header("Authorization", authHeader)
                .header("Content-Type", contentType != null ? contentType : "image/jpeg")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(data))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new RuntimeException("R2 upload responded with HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private void deleteFromR2(FeatureFlagsProperties.R2Storage r2, String key) throws Exception {
        String host = r2.getAccountId() + ".r2.cloudflarestorage.com";
        String bucket = r2.getBucketName();
        String uri = "/" + bucket + "/" + key;
        String endpoint = "https://" + host + uri;

        Instant now = Instant.now();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        String dateStamp = DATE_STAMP_FORMAT.format(now);

        String contentHash = toHex(sha256(new byte[0]));
        String canonicalHeaders = "host:" + host + "\n"
                + "x-amz-content-sha256:" + contentHash + "\n"
                + "x-amz-date:" + amzDate + "\n";
        String signedHeaders = "host;x-amz-content-sha256;x-amz-date";

        String canonicalRequest = "DELETE\n"
                + uri + "\n\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + contentHash;

        String algorithm = "AWS4-HMAC-SHA256";
        String credentialScope = dateStamp + "/auto/s3/aws4_request";
        String stringToSign = algorithm + "\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + toHex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = getSignatureKey(r2.getSecretAccessKey(), dateStamp, "auto", "s3");
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        String authHeader = algorithm + " Credential=" + r2.getAccessKeyId() + "/" + credentialScope
                + ", SignedHeaders=" + signedHeaders + ", Signature=" + signature;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("x-amz-date", amzDate)
                .header("x-amz-content-sha256", contentHash)
                .header("Authorization", authHeader)
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 204 && response.statusCode() != 200) {
            throw new RuntimeException("R2 delete responded with HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private String generateR2PresignedUrl(FeatureFlagsProperties.R2Storage r2, String key, String contentType, int expiresSeconds) throws Exception {
        String host = r2.getAccountId() + ".r2.cloudflarestorage.com";
        String bucket = r2.getBucketName();
        String uri = "/" + bucket + "/" + key;

        Instant now = Instant.now();
        String amzDate = AMZ_DATE_FORMAT.format(now);
        String dateStamp = DATE_STAMP_FORMAT.format(now);

        String credentialScope = dateStamp + "/auto/s3/aws4_request";
        String credential = r2.getAccessKeyId() + "/" + credentialScope;

        String signedHeaders = "host";
        String canonicalHeaders = "host:" + host + "\n";

        String canonicalQueryString = "X-Amz-Algorithm=AWS4-HMAC-SHA256"
                + "&X-Amz-Credential=" + URLEncoder.encode(credential, StandardCharsets.UTF_8)
                + "&X-Amz-Date=" + amzDate
                + "&X-Amz-Expires=" + expiresSeconds
                + "&X-Amz-SignedHeaders=" + signedHeaders;

        String canonicalRequest = "PUT\n"
                + uri + "\n"
                + canonicalQueryString + "\n"
                + canonicalHeaders + "\n"
                + signedHeaders + "\n"
                + "UNSIGNED-PAYLOAD";

        String stringToSign = "AWS4-HMAC-SHA256\n"
                + amzDate + "\n"
                + credentialScope + "\n"
                + toHex(sha256(canonicalRequest.getBytes(StandardCharsets.UTF_8)));

        byte[] signingKey = getSignatureKey(r2.getSecretAccessKey(), dateStamp, "auto", "s3");
        String signature = toHex(hmacSha256(signingKey, stringToSign));

        return "https://" + host + uri + "?" + canonicalQueryString + "&X-Amz-Signature=" + signature;
    }

    private static byte[] sha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(data);
    }

    private static byte[] hmacSha256(byte[] key, String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(key, "HmacSHA256"));
        return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] getSignatureKey(String key, String dateStamp, String regionName, String serviceName) throws Exception {
        byte[] kSecret = ("AWS4" + key).getBytes(StandardCharsets.UTF_8);
        byte[] kDate = hmacSha256(kSecret, dateStamp);
        byte[] kRegion = hmacSha256(kDate, regionName);
        byte[] kService = hmacSha256(kRegion, serviceName);
        return hmacSha256(kService, "aws4_request");
    }

    private static String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
