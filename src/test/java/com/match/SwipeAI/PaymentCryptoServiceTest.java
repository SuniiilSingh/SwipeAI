package com.match.SwipeAI;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import com.match.SwipeAI.service.engine.PaymentCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PaymentCryptoServiceTest {

    private PaymentCryptoService cryptoService;

    @BeforeEach
    void setUp() {
        FeatureFlagsProperties properties = new FeatureFlagsProperties();
        properties.getSecurity().setPaymentEncryptionKey("test_secret_payment_vault_key_2026_aes256_super_secure!");
        properties.getSecurity().setServerPepper("test_server_pepper_blunderr_2026");

        cryptoService = new PaymentCryptoService(properties);
        cryptoService.init();
    }

    @Test
    @DisplayName("Payment encryption produces AES-GCM ciphertext with prefix and decrypts accurately")
    void testEncryptAndDecrypt() {
        String sensitivePaymentPayload = "{\"cf_payment_id\": \"9847291\", \"bank_utr\": \"UTR202609240182\", \"token\": \"tok_live_83749281\"}";

        String encrypted = cryptoService.encrypt(sensitivePaymentPayload);

        assertNotNull(encrypted);
        assertTrue(encrypted.startsWith(PaymentCryptoService.PREFIX), "Must start with ENC_PAY_GCM:v1:");
        assertNotEquals(sensitivePaymentPayload, encrypted);

        String decrypted = cryptoService.decrypt(encrypted);
        assertEquals(sensitivePaymentPayload, decrypted, "Decrypted text must match original exactly");
    }

    @Test
    @DisplayName("Unique 96-bit IV ensures identical payloads produce distinct ciphertexts")
    void testRandomIvProducesUniqueCiphertexts() {
        String receipt = "receipt_apple_signed_payload_123";

        String enc1 = cryptoService.encrypt(receipt);
        String enc2 = cryptoService.encrypt(receipt);

        assertNotEquals(enc1, enc2, "Fresh random IV must produce different ciphertexts for same plaintext");
        assertEquals(receipt, cryptoService.decrypt(enc1));
        assertEquals(receipt, cryptoService.decrypt(enc2));
    }

    @Test
    @DisplayName("Tampered ciphertext fails AES-256-GCM authentication tag verification")
    void testTamperedCiphertextFails() {
        String payload = "sensitive_bank_data";
        String encrypted = cryptoService.encrypt(payload);

        char lastChar = encrypted.charAt(encrypted.length() - 1);
        char tamperedChar = (lastChar == 'A') ? 'B' : 'A';
        String tampered = encrypted.substring(0, encrypted.length() - 1) + tamperedChar;

        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt(tampered));
    }

    @Test
    @DisplayName("Masking utilities properly sanitize tokens, phones, and VPAs")
    void testMaskingUtilities() {
        // Token masking
        assertEquals("tok_****2348", cryptoService.maskToken("tok_982348572348"));
        assertEquals("****", cryptoService.maskToken("short"));
        assertEquals("null", cryptoService.maskToken(null));

        // Phone masking
        assertEquals("+91****3210", cryptoService.maskPhone("+919876543210"));
        assertEquals("****", cryptoService.maskPhone("1234"));
        assertEquals("null", cryptoService.maskPhone(null));

        // VPA masking
        assertEquals("r***n@okaxis", cryptoService.maskVpa("rohan@okaxis"));
        assertEquals("s***h@paytm", cryptoService.maskVpa("sunilsingh@paytm"));
        assertEquals("null", cryptoService.maskVpa(null));
    }

    @Test
    @DisplayName("Payload sanitization recursively removes PII from logs")
    void testPayloadSanitization() {
        Map<String, Object> rawPayload = Map.of(
                "event", "payment.captured",
                "customerPhone", "+919876543210",
                "vpa", "user@okhdfcbank",
                "nested", Map.of(
                        "purchaseToken", "tok_secret_google_play_receipt_token_12345",
                        "items", List.of("WEEKEND_PASS_99")
                )
        );

        Map<String, Object> sanitized = cryptoService.sanitizePayloadForLogging(rawPayload);

        assertEquals("payment.captured", sanitized.get("event"));
        assertEquals("+91****3210", sanitized.get("customerPhone"));
        assertEquals("u***r@okhdfcbank", sanitized.get("vpa"));

        @SuppressWarnings("unchecked")
        Map<String, Object> nested = (Map<String, Object>) sanitized.get("nested");
        assertNotNull(nested);
        assertEquals("tok_****2345", nested.get("purchaseToken"));
    }
}
