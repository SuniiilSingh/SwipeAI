package com.match.SwipeAI.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class FeatureFlagsProperties {

    private Security security = new Security();
    private Features features = new Features();

    @Data
    public static class Security {
        private String jwtSecret = "swipeai_super_secret_jwt_key_2026_32bytes_minimum_length!";
        private long jwtExpirationMs = 2592000000L; // 30 days
        private String serverPepper = "swipeai_shadow_shield_server_pepper_salt_key_2026";
    }

    @Data
    public static class Features {
        private DigiLocker digilocker = new DigiLocker();
        private WhatsApp whatsapp = new WhatsApp();
        private Twilio twilio = new Twilio();
        private Razorpay razorpay = new Razorpay();
        private LiveKit livekit = new LiveKit();
        private AiWingman aiWingman = new AiWingman();
        private R2Storage r2Storage = new R2Storage();
    }

    @Data
    public static class DigiLocker {
        private boolean enabled = false;
        private String apiUrl = "https://api.digitallocker.gov.in";
        private String clientId;
        private String clientSecret;
    }

    @Data
    public static class WhatsApp {
        private boolean enabled = false;
        private String apiUrl = "https://graph.facebook.com/v18.0";
        private String phoneNumberId;
        private String accessToken;
    }

    @Data
    public static class Twilio {
        private boolean enabled = false;
        private String accountSid;
        private String authToken;
        private String phoneNumber;
    }

    @Data
    public static class Razorpay {
        private boolean enabled = false;
        private String keyId;
        private String keySecret;
        private String webhookSecret;
    }

    @Data
    public static class LiveKit {
        private boolean enabled = false;
        private String host;
        private String apiKey;
        private String apiSecret;
    }

    @Data
    public static class AiWingman {
        private boolean enabled = false;
        private String apiKey;
        private String model = "gpt-4o-mini";
    }

    @Data
    public static class R2Storage {
        private boolean enabled = false;
        private String accountId;
        private String accessKeyId;
        private String secretAccessKey;
        private String bucketName = "swipeai-media";
        private String publicDomain = "https://cdn.swipeai.in";
    }
}
