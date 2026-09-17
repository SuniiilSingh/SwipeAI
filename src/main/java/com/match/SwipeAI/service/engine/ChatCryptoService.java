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
import java.util.Base64;

/**
 * Bank-Grade AES-256-GCM Chat Encryption & Decryption Engine.
 * Ensures zero plaintext stored in the database at rest.
 * Uses 128-bit authentication tag and a fresh 96-bit (12-byte) random IV per message.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatCryptoService {

    public static final String PREFIX = "ENC_GCM:v1:";
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int IV_LENGTH = 12; // 96 bits recommended for GCM

    private static ChatCryptoService instance;

    private final FeatureFlagsProperties properties;
    private final SecureRandom secureRandom = new SecureRandom();
    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        instance = this;
        String rawKey = properties.getSecurity().getChatEncryptionKey();
        String pepper = properties.getSecurity().getServerPepper();
        this.secretKey = deriveSecretKey(rawKey, pepper);
        log.info("ChatCryptoService initialized with AES-256-GCM authenticated encryption.");
    }

    public static ChatCryptoService getInstance() {
        if (instance == null) {
            // Fallback for standalone converter tests or unmanaged Hibernate instances
            FeatureFlagsProperties props = new FeatureFlagsProperties();
            instance = new ChatCryptoService(props);
            instance.init();
        }
        return instance;
    }

    private SecretKey deriveSecretKey(String key, String pepper) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String combined = (key != null ? key : "blunderr_default_chat_key_2026") + ":" +
                    (pepper != null ? pepper : "blunderr_pepper_2026");
            byte[] keyBytes = digest.digest(combined.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(keyBytes, ALGORITHM);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to derive AES-256 chat encryption key", e);
        }
    }

    /**
     * Encrypts plaintext message content into AES-256-GCM authenticated ciphertext.
     *
     * @param plainText Readable conversation text
     * @return Formatted ciphertext: ENC_GCM:v1:<Base64(IV)>:<Base64(Ciphertext)>
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        // Already encrypted?
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
            log.error("Failed to encrypt chat message content", e);
            throw new IllegalStateException("Encryption failed: Unable to secure chat message", e);
        }
    }

    /**
     * Decrypts AES-256-GCM ciphertext back into readable plaintext for authenticated users.
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
                log.warn("Corrupted or malformed chat ciphertext: missing separator");
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
            log.error("Failed to decrypt chat message content (tampered or invalid key)", e);
            throw new IllegalStateException("Decryption failed: Message corrupted or unauthorized access", e);
        }
    }
}
