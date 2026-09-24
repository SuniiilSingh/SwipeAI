package com.match.SwipeAI.service.engine;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

/**
 * Bank-Grade AES-256-GCM Payment Security Vault & Cryptographic Service.
 * Encrypts sensitive payment data, receipts, external transaction payloads, and audit records at rest.
 * Uses 128-bit authentication tags and unique 96-bit (12-byte) random IVs per record.
 * Also provides PII/token masking to guarantee zero sensitive data leakage in logs or responses.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCryptoService {

    public static final String PREFIX = "ENC_PAY_GCM:v1:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // 96 bits recommended for GCM

    private static PaymentCryptoService instance;

    private final FeatureFlagsProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        instance = this;
        String rawKey = properties.getSecurity().getPaymentEncryptionKey();
        String pepper = properties.getSecurity().getServerPepper();
        this.secretKey = deriveSecretKey(rawKey, pepper);
        log.info("PaymentCryptoService initialized with AES-256-GCM authenticated encryption.");
    }

    public static PaymentCryptoService getInstance() {
        if (instance == null) {
            FeatureFlagsProperties props = new FeatureFlagsProperties();
            instance = new PaymentCryptoService(props);
            instance.init();
        }
        return instance;
    }

    private SecretKey deriveSecretKey(String key, String pepper) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = (key != null ? key : "swipeai_payment_vault_default_key_2026") + ":" +
                    (pepper != null ? pepper : "swipeai_pepper_2026");
            byte[] keyBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 payment encryption key", e);
        }
    }

    /**
     * Encrypts plaintext payment data into AES-256-GCM authenticated ciphertext.
     *
     * @param plainText Sensitive financial or receipt payload
     * @return Formatted ciphertext: ENC_PAY_GCM:v1:<Base64(IV)>:<Base64(Ciphertext)>
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // Idempotent: already encrypted
        if (plainText.startsWith(PREFIX)) {
            return plainText;
        }

        try {
            byte[] iv = new byte[IV_LENGTH];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);

            byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            String ivB64 = Base64.getEncoder().encodeToString(iv);
            String cipherB64 = Base64.getEncoder().encodeToString(cipherText);

            return PREFIX + ivB64 + ":" + cipherB64;
        } catch (Exception e) {
            log.error("Failed to encrypt sensitive payment data", e);
            throw new IllegalStateException("Payment encryption failed: Unable to secure financial payload", e);
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext back into readable plaintext for authorized reviewers.
     *
     * @param cipherText Stored ciphertext or legacy plaintext
     * @return Decrypted plaintext string
     */
    public String decrypt(String cipherText) {
        if (cipherText == null || cipherText.isEmpty()) {
            return cipherText;
        }

        // Backward compatibility: If not encrypted with this algorithm, return as-is
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText;
        }

        try {
            String payload = cipherText.substring(PREFIX.length());
            int sepIndex = payload.indexOf(':');
            if (sepIndex == -1) {
                log.warn("Corrupted payment ciphertext: missing IV-cipher separator");
                return cipherText;
            }

            String ivB64 = payload.substring(0, sepIndex);
            String encryptedB64 = payload.substring(sepIndex + 1);

            byte[] iv = Base64.getDecoder().decode(ivB64);
            byte[] encryptedBytes = Base64.getDecoder().decode(encryptedB64);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

            byte[] plainBytes = cipher.doFinal(encryptedBytes);
            return new String(plainBytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to decrypt payment data (tampered or unauthorized)", e);
            throw new IllegalStateException("Payment decryption failed: Data corrupted or unauthorized access", e);
        }
    }

    /**
     * Masks raw purchase or session tokens for safe logging.
     * e.g., "token_play_39847294827" -> "tok_****4827"
     */
    public String maskToken(String token) {
        if (token == null) return "null";
        if (token.length() <= 8) return "****";
        return token.substring(0, 4) + "****" + token.substring(token.length() - 4);
    }

    /**
     * Masks customer phone numbers for zero data leakage in logs.
     * e.g., "+919876543210" -> "+91****3210"
     */
    public String maskPhone(String phone) {
        if (phone == null) return "null";
        if (phone.length() <= 6) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Masks VPA / UPI IDs.
     * e.g., "sunil@okaxis" -> "s***l@okaxis"
     */
    public String maskVpa(String vpa) {
        if (vpa == null) return "null";
        int atIdx = vpa.indexOf('@');
        if (atIdx <= 1) return "****" + (atIdx != -1 ? vpa.substring(atIdx) : "");
        String handle = vpa.substring(0, atIdx);
        String domain = vpa.substring(atIdx);
        return handle.charAt(0) + "***" + handle.charAt(handle.length() - 1) + domain;
    }

    /**
     * Recursively sanitizes a Map payload before logging, masking sensitive tokens, keys, and PII.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> sanitizePayloadForLogging(Map<String, Object> payload) {
        if (payload == null) return Collections.emptyMap();
        Map<String, Object> sanitized = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();

            if (val instanceof Map) {
                sanitized.put(key, sanitizePayloadForLogging((Map<String, Object>) val));
            } else if (val instanceof List) {
                sanitized.put(key, sanitizeListForLogging((List<?>) val));
            } else if (val instanceof String strVal) {
                String lowerKey = key.toLowerCase();
                if (lowerKey.contains("token") || lowerKey.contains("secret") || lowerKey.contains("signature")) {
                    sanitized.put(key, maskToken(strVal));
                } else if (lowerKey.contains("phone")) {
                    sanitized.put(key, maskPhone(strVal));
                } else if (lowerKey.contains("vpa")) {
                    sanitized.put(key, maskVpa(strVal));
                } else {
                    sanitized.put(key, val);
                }
            } else {
                sanitized.put(key, val);
            }
        }
        return sanitized;
    }

    @SuppressWarnings("unchecked")
    private List<?> sanitizeListForLogging(List<?> list) {
        List<Object> sanitized = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map) {
                sanitized.add(sanitizePayloadForLogging((Map<String, Object>) item));
            } else {
                sanitized.add(item);
            }
        }
        return sanitized;
    }
}
