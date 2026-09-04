# SwipeAI — Architecture & Complete API Documentation

SwipeAI is a high-intent, safety-first Spring Boot backend engineered specifically for Indian dating dynamics. It combines zero-knowledge trust verification (DigiLocker ZK-KYC), relative/boss privacy shielding (Shadow Shield), cultural compatibility algorithms, 10-second pre-chat icebreakers, and UPI micro-sachet monetization.

---

## 1. Project Package Structure

```
com.match.SwipeAI
├── config/                  # Spring Security, WebMvc, WebSocket & Feature Flags
│   ├── FeatureFlagsProperties.java
│   ├── SecurityConfig.java
│   └── WebSocketConfig.java
├── controller/              # REST Controllers with Javadoc & Validation
│   ├── AuthController.java
│   ├── KycController.java
│   ├── ShadowShieldController.java
│   ├── ProfileController.java
│   ├── DiscoveryController.java
│   ├── MatchController.java
│   ├── ChatController.java
│   ├── PaymentsController.java
│   ├── CallingController.java
│   └── SafeDateController.java
├── dto/                     # Strongly typed Request/Response DTOs
│   ├── AuthDto.java
│   ├── KycDto.java
│   ├── ShieldDto.java
│   ├── ProfileDto.java
│   ├── DiscoveryDto.java
│   ├── MatchDto.java
│   ├── ChatDto.java
│   ├── PaymentDto.java
│   ├── CallingDto.java
│   └── SafeDateDto.java
├── enums/                   # Dedicated Domain Enums
│   ├── ActionType.java          # LIKE, PASS, SUPER_CHAI
│   ├── ContextType.java         # PHOTO, VOICE, PROMPT, MEME
│   ├── DatingIntent.java        # MARRIAGE_MINDED, SERIOUS_DATING, CASUAL_DATES, FIGURING_IT_OUT
│   ├── DietaryPreference.java   # STRICT_JAIN, PURE_VEG, VEGAN, EGGETARIAN, NON_VEG
│   ├── Gender.java              # MALE, FEMALE, NON_BINARY
│   ├── LivingStatus.java        # WITH_PARENTS, INDEPENDENT_FLAT, PG
│   ├── MatchStatus.java         # PENDING_ICEBREAKER, ACTIVE_CHAT, EXPIRED, UNMATCHED
│   ├── MessageType.java         # TEXT, AUDIO_NOTE, IMAGE, MEME, VIRTUAL_CHAI, SYSTEM
│   ├── OrderStatus.java         # PENDING, CAPTURED, FAILED
│   └── SkuType.java             # SUPER_SPARK_19, BOOST_1X_FRIDAY_29, WEEKEND_PASS_99, FORTNIGHT_PASS_199, etc.
├── init/                    # Demo seed data runner (Users, Cafes, Matches, Chats)
│   └── DataInitializer.java
├── model/                   # JPA Entities (PostgreSQL / H2)
│   ├── User.java
│   ├── UserContactShield.java
│   ├── Profile.java
│   ├── Interaction.java
│   ├── Match.java
│   ├── ChatMessage.java
│   ├── UpiOrder.java
│   └── SafeDateSpot.java
├── repository/              # Spring Data JPA Repositories
├── security/                # JWT Token Filter & Signature Utilities
│   ├── JwtAuthFilter.java
│   └── JwtUtil.java
├── service/                 # Core Business Logic Services
│   ├── AuthService.java
│   ├── ProfileService.java
│   ├── DiscoveryService.java
│   ├── MatchService.java
│   ├── ChatService.java
│   └── SafeDateService.java
├── service/engine/          # Production Algorithmic Engines
│   ├── ShadowShieldService.java        # Zero-Knowledge SHA-256 salted hashing
│   ├── MultiObjectiveMatchEngine.java  # Multi-attribute scoring formula
│   ├── MatchKarmaService.java          # Dynamic Ghost-Buster karma tracking
│   ├── IcebreakerEngine.java           # 10s Rapid-Fire quiz state engine
│   └── CosmicChemistryEngine.java      # Modern astrological synastry
├── service/integration/     # Third-Party Clients (with Feature Flag Mocks)
│   ├── DigiLockerKycService.java
│   ├── LivenessService.java
│   ├── OtpService.java
│   ├── UpiPaymentService.java
│   ├── LiveKitCallingService.java
│   ├── AiWingmanService.java
│   └── NudityDetectorService.java
└── websocket/               # Real-Time WebSocket duplex gateway (/ws/chat)
    └── ChatWebSocketHandler.java
```

---

## 2. Spring Boot Profiles Architecture

The application provides two environment profiles:

### A. Default / Local Profile (`application.yaml`)
- **Activation**: Default (when no profile is specified) or `SPRING_PROFILES_ACTIVE=default`.
- **Database**: PostgreSQL (`jdbc:postgresql://localhost:5432/swipe`, username: `postgres`, password: `root`, driver: `org.postgresql.Driver`).
- **Credentials**: Pre-configured with local mock secrets (`jwt-secret`, `server-pepper`).
- **Feature Flags**: All external integrations (`digilocker`, `whatsapp`, `twilio`, `razorpay`, `livekit`, `ai-wingman`, `r2-storage`) default to `false` and use **built-in simulated default functionality** (no third-party API keys required).
- **Data Seeding**: Automatically seeds demo candidate profiles (*Ananya 24*, *Rohan 26*, *Priya 25*) and partner cafes (*Blue Tokai*, *Third Wave*, *Starbucks*) via [`DataInitializer.java`](file:///Users/sunilsingh/Workspace/SwipeAI/src/main/java/com/match/SwipeAI/init/DataInitializer.java).

### B. Production Profile (`application-prod.yaml`)
- **Activation**: `SPRING_PROFILES_ACTIVE=prod`.
- **Database**: Production PostgreSQL datasource with HikariCP connection pool tuning (`maximum-pool-size: 20`, `minimum-idle: 5`, `connection-timeout: 30s`).
- **Credentials**: Injected strictly via environment variables (`JWT_SECRET`, `SERVER_PEPPER`, `DIGILOCKER_CLIENT_ID`, `WHATSAPP_ACCESS_TOKEN`, `RAZORPAY_KEY_ID`, `LIVEKIT_API_KEY`, `OPENAI_API_KEY`).
- **Feature Flags**: Live third-party integrations enabled by default (`${FEATURE_DIGILOCKER_ENABLED:true}`, `${FEATURE_WHATSAPP_ENABLED:true}`, `${FEATURE_RAZORPAY_ENABLED:true}`, `${FEATURE_LIVEKIT_ENABLED:true}`, `${FEATURE_AI_WINGMAN_ENABLED:true}`).
- **Data Seeding**: Demo mock users disabled (`@Profile("!prod")`).

---

## 3. Feature Flags & Simulated Default Fallbacks

| Feature Flag Key | Default | Mock / Simulator Fallback | Live Integration |
| :--- | :--- | :--- | :--- |
| `app.features.digilocker.enabled` | `false` | Returns instant Age 18+ & Gender cryptographic assertion and Gold Badge | DigiLocker OAuth2 API |
| `app.features.whatsapp.enabled` | `false` | Generates 4-digit mock OTP (universal test OTP: `1234`) | WhatsApp Cloud API |
| `app.features.twilio.enabled` | `false` | Generates 4-digit mock OTP (universal test OTP: `1234`) | Twilio SMS REST API |
| `app.features.razorpay.enabled` | `false` | Generates NPCI intent `upi://pay` & enables `/test-confirm` | Razorpay UPI AutoPay |
| `app.features.livekit.enabled` | `false` | Generates mock WebRTC room tokens with masked numbers | LiveKit SFU Cluster |
| `app.features.ai-wingman.enabled` | `false` | Contextual local Hinglish spark generator | OpenAI / GPT-4o-mini |

---

## 3. Core Algorithmic Formulations

### 1. Multi-Objective Match Compatibility Function
The recommendation score $S(u, v)$ between viewing user $u$ and candidate $v$ balances vector similarity, cultural overlap, karma stability, and distance decay:

$$S(u, v) = w_1 \cdot \cos(\vec{E}_u, \vec{E}_v) + w_2 \cdot M_{\text{cultural}}(u, v) + w_3 \cdot \left(1 - \frac{|\text{Karma}_u - \text{Karma}_v|}{200}\right) - w_4 \cdot \log(1 + \text{Dist}_{\text{km}}(u, v))$$

**Weights**: $w_1 = 0.35, w_2 = 0.30, w_3 = 0.20, w_4 = 0.15$

**Cultural Overlap Formula**:
$$M_{\text{cultural}}(u, v) = 0.40 \cdot \text{DietMatch}(u, v) + 0.30 \cdot \text{LanguageOverlap}(u, v) + 0.20 \cdot \text{LivingConditionFit}(u, v) + 0.10 \cdot \text{CosmicVibeScore}(u, v)$$

---

### 2. Shadow Shield Contact-Filtering Pipeline
1. **Corporate Domain Check**: If viewer's corporate domain matches candidate's corporate domain (`@swiggy.in`, `@tcs.com`), the candidate is filtered out.
2. **Salted Hash Check**: Queries salted SHA-256 phone hash against `user_contact_shields`:
   $$\text{Hash} = \text{SHA256}(\text{Phone}_{10} + \text{SERVER\_PEPPER})$$
3. **Symmetric Check**: Checks if viewer is present in candidate's shield list. If either matches, candidate is 100% invisible.

---

### 3. Ghost-Buster Dynamic Match Karma
- **48-Hour Timeout with 0 Messages**: Deducts **-8 points** from the match initiator's Karma score.
- **Milestone Reward (>= 4 Messages Exchanged)**: Awards **+3 points** to both participants.
- **Harassment / Creep Report**: Immediate **-50 points penalty** and shadowban pool assignment.

---

## 4. REST API Endpoint Reference

### Authentication (`/v1/auth`)

#### `POST /v1/auth/otp/send`
Dispatches OTP to user via WhatsApp or Twilio SMS.
- **Request Body**:
  ```json
  {
    "phoneE164": "+919876543210"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "status": "success",
    "message": "OTP sent successfully. In demo mode, use OTP 1234",
    "demoOtp": "1234"
  }
  ```

#### `POST /v1/auth/otp/verify`
Verifies OTP and issues JWT token.
- **Request Body**:
  ```json
  {
    "phoneE164": "+919876543210",
    "otp": "1234",
    "gender": "FEMALE",
    "intent": "SERIOUS_DATING",
    "birthDate": "2000-07-21"
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6...",
    "userId": "8f3b2c14-5d82-4f2a-89b1-a1e948c27189",
    "phoneE164": "+919876543210",
    "isNewUser": false,
    "digilockerVerified": true,
    "whatsappVerified": true,
    "livenessScore": 0.99,
    "karmaScore": 145,
    "sparksBalance": 3,
    "hasActivePass": false
  }
  ```

#### `POST /v1/auth/whatsapp/login`
WhatsApp 1-tap instant login bypassing telecom carrier SMS drops.

---

### Zero-Knowledge Trust Pass & KYC (`/v1/kyc`)

#### `POST /v1/kyc/digilocker/initiate` *(Bearer Auth Required)*
Generates DigiLocker OAuth2 authorization link.

#### `POST /v1/kyc/digilocker/verify-proof` *(Bearer Auth Required)*
Verifies zero-knowledge proof assertion (Age 18+ & Gender) and awards Gold Shield Badge.
- **Request Body**:
  ```json
  {
    "stateToken": "state_token_123",
    "code": "auth_code_xyz",
    "simulateSuccess": true
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "isVerified": true,
    "isAdult": true,
    "gender": "VERIFIED",
    "maskedCity": "Bengaluru",
    "badge": "GOLD_SHIELD",
    "message": "DigiLocker Zero-Knowledge KYC Verified. Gold Badge awarded!"
  }
  ```

#### `POST /v1/kyc/liveness/verify` *(Bearer Auth Required)*
Evaluates 3-second head turn 3D selfie scan to eliminate deepfakes.

---

### Relative & Boss Auto-Shield (`/v1/privacy/shadow-shield`)

#### `POST /v1/privacy/shadow-shield/sync-contacts` *(Bearer Auth Required)*
Ingests contact phone numbers or client-computed SHA-256 hashes.
- **Request Body**:
  ```json
  {
    "rawPhoneNumbers": ["+919876500001", "+919876500002"]
  }
  ```

#### `POST /v1/privacy/shadow-shield/domain` *(Bearer Auth Required)*
Sets corporate email domain to hide profile from office coworkers.
- **Request Body**:
  ```json
  {
    "corporateDomain": "swiggy.in"
  }
  ```

#### `GET /v1/privacy/shadow-shield/status` *(Bearer Auth Required)*
Returns active contact count and corporate domain shield state.

---

### Cultural Canvas Profile (`/v1/profiles`)

#### `GET /v1/profiles/me` *(Bearer Auth Required)*
Returns current user's profile with dietary preferences, living status, and balances.

#### `PUT /v1/profiles/me` *(Bearer Auth Required)*
Updates cultural attributes, bio, photos, and living arrangements.

#### `POST /v1/profiles/voice-prompt` *(Bearer Auth Required)*
Uploads vernacular voice note snippet with duration in seconds and transcript.

#### `POST /v1/profiles/meme-dna/swipe` *(Bearer Auth Required)*
Records meme swipe for daily 5-meme humor DNA calibration.

#### `GET /v1/profiles/cosmic-chemistry/{targetUserId}` *(Bearer Auth Required)*
Generates modern Cosmic Chemistry 2.0 vibe card and synastry report.

---

### High-Intent Discovery Feed (`/v1/discovery`)

#### `POST /v1/discovery/feed` *(Bearer Auth Required)*
Returns curated candidates, enforcing 25/day anti-fatigue hard cap and Shadow Shield filtering.
- **Request Body**:
  ```json
  {
    "latitude": 12.9716,
    "longitude": 77.5946,
    "maxDistanceKm": 15,
    "dietaryFilters": ["PURE_VEG", "EGGETARIAN"],
    "intentFilter": "SERIOUS_DATING",
    "limit": 10
  }
  ```
- **Response** (`200 OK`):
  ```json
  {
    "status": "success",
    "data": {
      "remainingDailySwipes": 18,
      "dailyHardCap": 25,
      "candidates": [
        {
          "userId": "c1-rohan-26",
          "displayName": "Rohan",
          "age": 26,
          "isDigilockerVerified": true,
          "distanceKm": 3.8,
          "culturalBadges": {
            "diet": "EGGETARIAN",
            "living": "INDEPENDENT_FLAT",
            "languages": ["English", "Hindi", "Kannada"],
            "zodiac": "Aries"
          },
          "voicePrompt": {
            "audioUrl": "https://cdn.swipeai.in/v/rohan.m4a",
            "durationSec": 18,
            "promptText": "Dilli ki sardi ya Mumbai ki baarish? Bangalore weather wins."
          },
          "memeMatch": {
            "matchPercent": 88,
            "memeTitle": "Silk Board Peak Hour"
          },
          "compatibilityScore": 94
        }
      ]
    }
  }
  ```

#### `POST /v1/discovery/interact` *(Bearer Auth Required)*
Records prompt-level likes, passes, and Virtual Cutting Chai micro-invites.

#### `GET /v1/discovery/circles`
Returns list of active Micro-Community lifestyle circles.

---

### Match Lifecycle & Pre-Chat Games (`/v1/matches`)

#### `GET /v1/matches` *(Bearer Auth Required)*
Returns active matches with 48h ephemeral countdown timers.

#### `POST /v1/matches/{id}/icebreaker/answer` *(Bearer Auth Required)*
Answers 10-second rapid-fire icebreaker quiz. When both users answer, transitions match status to `ACTIVE_CHAT`.

#### `GET /v1/matches/{id}/wingman/sparks` *(Bearer Auth Required)*
Returns 3 witty Hinglish/English conversational icebreaker sparks generated by AI Wingman.

#### `POST /v1/matches/{id}/unmatch` *(Bearer Auth Required)*
Unmatches and severs chat and calling channels.

---

### Encrypted Chat Lounge & Shield 360 (`/v1/chat` & `/ws/chat`)

#### `GET /v1/chat/{matchId}/messages` *(Bearer Auth Required)*
Retrieves messages for an active match.

#### `POST /v1/chat/{matchId}/messages` *(Bearer Auth Required)*
Sends a text or image message. Runs Shield 360 AI Nudity Detector on images to auto-blur unsolicited/sensitive content.

#### WebSocket Gateway (`/ws/chat?userId={uuid}`)
Persistent duplex WebSocket channel for instant sub-35ms message dispatch.

---

### Masked In-App Calling (`/v1/calling`)

#### `POST /v1/calling/virtual-chai/session` *(Bearer Auth Required)*
Generates masked LiveKit WebRTC audio/video calling tokens with hidden phone numbers.

---

### UPI Sachet Monetization Store (`/v1/payments`)

#### `GET /v1/payments/store/catalog`
Returns full catalog:
- `SUPER_SPARK_19` (₹19)
- `REVIVE_MATCH_19` (₹19)
- `CUTTING_CHAI_21` (₹21)
- `BOOST_1X_FRIDAY_29` (₹29)
- `DIRECT_DMS_3X_49` (₹49)
- `WEEKEND_PASS_99` (₹99)
- `WEEKLY_PASS_149` (₹149)
- `FORTNIGHT_PASS_199` (₹199 - 14 Days Pass)
- `SELECT_QUARTERLY_999` (₹999)

#### `POST /v1/payments/upi/create-order` *(Bearer Auth Required)*
Generates NPCI UPI Intent URI (`upi://pay?pa=...&am=29&tn=BOOST&tr=order_id`) & QR code.

#### `POST /v1/payments/upi/webhook`
Idempotent payment capture webhook handler with HMAC-SHA256 signature verification.

---

### Safe Date Spots & SOS Tracking (`/v1/safe-date`)

#### `GET /v1/safe-date/spots?city=Bengaluru`
Returns curated partner cafes (Blue Tokai, Third Wave Coffee, Starbucks) with 15% discount coupon codes.

#### `POST /v1/safe-date/sos/start` *(Bearer Auth Required)*
Generates a dynamic check-in SOS tracking link shared with emergency contacts.
