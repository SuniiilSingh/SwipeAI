package com.match.SwipeAI;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.service.engine.ChatCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatCryptoServiceTest {

    private ChatCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.getSecurity().setChatEncryptionKey("test_secret_key_aes256_blunderr_2026_super_secure!");
        properties.getSecurity().setServerPepper("test_server_pepper_blunderr_2026");

        cryptoService = new ChatCryptoService(properties);
        cryptoService.init();
    }

    @Test
    @DisplayName("Encryption produces AES-GCM ciphertext prefix and decrypts back to original")
    void testEncryptAndDecrypt() {
        String originalMessage = "Hey, are we still meeting for filter coffee at Blue Tokai Indiranagar?";

        String cipherText = cryptoService.encrypt(originalMessage);

        assertNotNull(cipherText);
        assertTrue(cipherText.startsWith(ChatCryptoService.PREFIX), "Ciphertext must start with ENC_GCM:v1: prefix");
        assertNotEquals(originalMessage, cipherText, "Ciphertext must not match plaintext");

        String decrypted = cryptoService.decrypt(cipherText);
        assertEquals(originalMessage, decrypted, "Decrypted message must match original plaintext exactly");
    }

    @Test
    @DisplayName("Identical plaintexts produce distinct ciphertexts due to fresh 96-bit random IV")
    void testRandomIvProducesUniqueCiphertexts() {
        String message = "Confidential chat message";

        String cipher1 = cryptoService.encrypt(message);
        String cipher2 = cryptoService.encrypt(message);

        assertNotEquals(cipher1, cipher2, "Two encryptions of same plaintext must produce different ciphertexts");
        assertEquals(message, cryptoService.decrypt(cipher1));
        assertEquals(message, cryptoService.decrypt(cipher2));
    }

    @Test
    @DisplayName("Tampered ciphertext fails authentication tag verification")
    void testTamperedCiphertextFails() {
        String message = "Secret date confession";
        String cipherText = cryptoService.encrypt(message);

        // Tamper with one character of the ciphertext
        char lastChar = cipherText.charAt(cipherText.length() - 1);
        char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
        String tamperedCipher = cipherText.substring(0, cipherText.length() - 1) + tamperedChar;

        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt(tamperedCipher),
                "Tampered ciphertext must fail GCM authentication tag verification");
    }

    @Test
    @DisplayName("Legacy unencrypted strings pass through decrypt gracefully")
    void testLegacyUnencryptedPassThrough() {
        String legacyPlaintext = "Legacy unencrypted message from old database";

        String result = cryptoService.decrypt(legacyPlaintext);

        assertEquals(legacyPlaintext, result, "Legacy message without ENC_GCM prefix should pass through untouched");
    }

    @Test
    @DisplayName("Null and empty inputs are handled safely")
    void testNullAndEmptyInputs() {
        assertNull(cryptoService.encrypt(null));
        assertNull(cryptoService.decrypt(null));
        assertEquals("", cryptoService.encrypt(""));
        assertEquals("", cryptoService.decrypt(""));
    }

    @Test
    @DisplayName("Idempotent encryption: Already encrypted string is not double-encrypted")
    void testIdempotentEncryption() {
        String original = "Double encrypt test";
        String encrypted = cryptoService.encrypt(original);
        String reEncrypted = cryptoService.encrypt(encrypted);

        assertEquals(encrypted, reEncrypted, "Encrypting an already encrypted string should be idempotent");
        assertEquals(original, cryptoService.decrypt(reEncrypted));
    }
}
