package com.match.SwipeAI;

import com.match.SwipeAI.config.FeatureFlagsProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("prod")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:swipeai_prod_test;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "JWT_SECRET=prod_super_secure_jwt_secret_key_minimum_32_bytes_long!",
        "SERVER_PEPPER=prod_pepper_test_key_2026"
})
class SwipeAiProdProfileTests {

    @Autowired
    private FeatureFlagsProperties properties;

    @Test
    void testProdProfileProperties() {
        assertNotNull(properties);
        // In prod profile, live integrations default to true unless overridden
        assertTrue(properties.getFeatures().getDigilocker().isEnabled());
        assertTrue(properties.getFeatures().getWhatsapp().isEnabled());
        assertTrue(properties.getFeatures().getRazorpay().isEnabled());
        assertTrue(properties.getFeatures().getLivekit().isEnabled());
        assertTrue(properties.getFeatures().getAiWingman().isEnabled());
        assertTrue(properties.getFeatures().getR2Storage().isEnabled());
    }
}
