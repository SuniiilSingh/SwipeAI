# 🌶️ Blunderr Dating (SwipeAI) — Production Backend Engine

> **High-Intent Modern Dating Platform with 3D Biometric Liveness, Shadow Shield Privacy, Dual-Rail User Choice Billing, and End-to-End Encrypted Real-Time Messaging.**

![Java 21](https://img.shields.io/badge/Java-21-orange.svg)
![Spring Boot 3.x](https://img.shields.io/badge/Spring%20Boot-3.4.0-brightgreen.svg)
![PostgreSQL 16](https://img.shields.io/badge/PostgreSQL-16%20%2B%20PostGIS-blue.svg)
![Gradle](https://img.shields.io/badge/Build-Gradle%209.7-blueviolet.svg)
![Compliance](https://img.shields.io/badge/App%20Store%20Compliance-Apple%201.2%20%2F%203.1.1%20%7C%20Google%20Play%20UCB-red.svg)

---

## 📖 Table of Contents
1. [Tech Stack & Architecture Highlights](#-tech-stack--architecture-highlights)
2. [Quickstart & Running Locally](#-quickstart--running-locally)
3. [System Architecture & Component Flow Diagrams](#-system-architecture--component-flow-diagrams)
   - [1. High-Level Full-Stack System Architecture](#1-high-level-full-stack-system-architecture)
   - [2. User Onboarding, Authentication & KYC Flow](#2-user-onboarding-authentication--kyc-flow)
   - [3. Discovery Feed & Multi-Objective Ranking Pipeline](#3-discovery-feed--multi-objective-ranking-pipeline)
   - [4. Dual-Rail Monetization (User Choice Billing India)](#4-dual-rail-monetization-user-choice-billing-india)
   - [5. Chat Lounge, Safe Date Spots & Shield 360 Moderation](#5-chat-lounge-safe-date-spots--shield-360-moderation)
   - [6. Trust & Safety Support Hub & Right to Erasure Flow](#6-trust--safety-support-hub--right-to-erasure-flow)
   - [7. Relational Database Schema (ER Diagram)](#7-relational-database-schema-er-diagram)
4. [Core API Endpoint Catalog](#-core-api-endpoint-catalog)
5. [Environment Variables Reference](#-environment-variables-reference)

---

## ⚡ Tech Stack & Architecture Highlights

* **Core Runtime:** Java 21 (LTS) with Spring Boot 3.x utilizing Loom Virtual Threads for ultra-high concurrency.
* **Database & Search:** PostgreSQL 16 with spatial bounding-box pushdown (`idx_users_spatial_coord`, `idx_users_discovery`), eliminating full-table scans.
* **Privacy & Security (Shadow Shield):** Zero-Knowledge client contact hashing (salted SHA-256) and corporate email domain blocking (e.g., `swiggy.in`, `tcs.com`, `infosys.com`) to guarantee complete bi-directional invisibility from family, relatives, neighbors, and coworkers.
* **Trust & Identity:** DigiLocker 18+ Government ID age verification combined with 3D Biometric Facial Liveness checks to permanently eliminate catfish accounts.
* **Real-time Messaging & Calls:** End-to-End Encrypted (AES-256-GCM) 1-on-1 chat lounges, 15s Vernacular Audio Notes (Cloudflare R2 Object Storage), and masked WebRTC Virtual Chai calling powered by LiveKit Cloud.
* **Dual-Rail Monetization (User Choice Billing India):** Fully compliant with the Competition Commission of India (CCI) ruling and Google Play User Choice Billing—offering direct UPI/QR through Cashfree alongside Apple StoreKit 2 and Google Play Billing, complete with an Apple Guideline 3.1.1 compliant **"Restore Purchases"** mechanism.
* **Regulatory Compliance:** Built-in 1-tap Account Deletion (Right to Erasure / DPDP Act), in-app Report & Block for UGC safety (Apple Review Guideline 1.2), and a 24-hour SLA Support Ticketing Hub.

---

## 🚀 Quickstart & Running Locally

### Prerequisites
* JDK 21+ (`openjdk 21` or `temurin-21`)
* PostgreSQL 16+ running on `localhost:5432` with database `swipeai`

```bash
# Clone and enter directory
cd /Users/sunilsingh/Workspace/SwipeAI

# Set environment variables (or copy .env.example)
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/swipeai
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export JWT_SECRET=your_super_secret_jwt_hmac_256_bit_key_here_must_be_long
export CASHFREE_APP_ID=test_cashfree_app_id
export CASHFREE_SECRET_KEY=test_cashfree_secret_key

# Compile and verify build
./gradlew compileJava

# Run Spring Boot backend server on port 8080
./gradlew bootRun

# Run full CRUD & integration test suite
./gradlew test
```

---

## 📊 System Architecture & Component Flow Diagrams

### 1. High-Level Full-Stack System Architecture

```mermaid
flowchart TD
    subgraph Client["📱 Frontend Client (React Native / Expo v57)"]
        UI_AUTH["Auth & KYC\n(WhatsApp OTP, DigiLocker, 3D Liveness)"]
        UI_FEED["Discovery Feed Deck\n(Card Swipe, Spring-Back Gating)"]
        UI_CHAT["Chat Lounge & Calls\n(AES-256 GCM, Virtual Chai WebRTC)"]
        UI_STORE["VibeStore & Passes\n(Dual-Rail Paywall, Restore Purchases)"]
        UI_PROFILE["Profile & Shield Center\n(Desire Profile, Contact Hashing)"]
        UI_SUPPORT["Help & Support Hub\n(FAQs, Ticket Tracking, Account Erasure)"]
    end

    subgraph Gateway["🌐 API & Security Gateway"]
        TLS["HTTPS / WSS Gateway (Cloudflare / Nginx Reverse Proxy)"]
        JWT_FILTER["JWT Bearer Authentication Filter (Stateless Principal Extraction)"]
        RATE_LIMIT["Rate Limiting & DDOS Shield (25 Swipes Hard Cap, OTP Brute-force Block)"]
    end

    subgraph Backend["⚙️ Spring Boot 3.x Backend Architecture"]
        subgraph Controllers["REST Controllers"]
            C_AUTH["AuthController & ProfileController"]
            C_FEED["DiscoveryController & MatchController"]
            C_CHAT["ChatController & CallingController"]
            C_PAY["PaymentsController & SupportController"]
        end

        subgraph CoreServices["Service Layer"]
            S_AUTH["AuthService & ProfileService"]
            S_FEED["DiscoveryService & MatchService"]
            S_CHAT["ChatService & LiveKitCallingService"]
            S_PAY["UpiPaymentService & CashfreePaymentService\nIapVerificationService & SupportService"]
        end

        subgraph Engines["Intelligence & Security Engines"]
            E_RANK["MultiObjectiveMatchEngine\n(Spatial + Age + Intent Ranking)"]
            E_SHIELD["ShadowShieldService\n(Salted SHA-256 Contact Blocker)"]
            E_COSMIC["CosmicChemistryEngine\n(Astrological Synastry & Vibe Cards)"]
            E_CRYPTO["ChatCryptoService & Liveness\n(AES-256-GCM End-to-End Encryption)"]
        end
    end

    subgraph DataTier["💾 Persistence & Cache Tier"]
        DB_POSTGRES[("PostgreSQL 16 Database\n(Users, Profiles, Matches, Chats, Tickets, Orders)")]
        STORAGE_R2[("Cloudflare R2 Object Storage\n(Encrypted Voice Notes & Watermarked Photos)")]
    end

    Client -->|HTTPS REST & WS| Gateway
    Gateway --> Controllers
    Controllers --> CoreServices
    CoreServices --> Engines
    CoreServices --> DataTier
    Engines --> DataTier
```

---

### 2. User Onboarding, Authentication & KYC Flow

```mermaid
sequenceDiagram
    autonumber
    actor User as User (Mobile App)
    participant Client as React Native Client
    participant Auth as AuthController / AuthService
    participant OTP as WhatsApp / SMS Gateway
    participant KYC as DigiLocker & Liveness
    participant Shield as ShadowShieldService
    participant DB as PostgreSQL Database

    User->>Client: Enters 10-digit Mobile Number
    Client->>Client: Checks local WhatsApp package installed
    Client->>Auth: POST /v1/auth/otp/send { phone, channel }
    Auth->>OTP: Dispatch 6-digit OTP via WhatsApp/SMS
    OTP-->>User: Delivers OTP Code
    User->>Client: Auto-detects or enters OTP
    Client->>Auth: POST /v1/auth/otp/verify { phone, otp }
    Auth->>DB: Lookup/Create User record
    Auth-->>Client: Returns JWT Token + User ID

    opt DigiLocker 18+ Verification
        User->>Client: Taps "Verify via DigiLocker"
        Client->>KYC: Initiate DigiLocker OAuth
        KYC-->>Client: Government ID Age & Name Verified (18+)
        Client->>Auth: POST /v1/kyc/digilocker/confirm
        Auth->>DB: Set isDigilockerVerified = true
    end

    opt 3D Biometric Liveness Verification
        User->>Client: Completes 3D Face Movement Scan
        Client->>KYC: Analyze face depth & anti-spoofing
        KYC-->>Client: Liveness score (e.g. 0.98 >= 0.85 pass)
        Client->>Auth: POST /v1/kyc/liveness/confirm
        Auth->>DB: Set livenessScore = 0.98
    end

    opt Relative & Boss Auto-Shield (Shadow Shield)
        User->>Client: Grants contact sync & enters employer domain
        Client->>Client: Hashes phonebook numbers using client SHA-256
        Client->>Shield: POST /v1/privacy/shadow-shield/sync-contacts
        Shield->>DB: Store salted hashes & corporate domain (e.g. swiggy.in)
    end

    User->>Client: Completes Photos, Prompts, Intent & Zodiac
    Client->>Auth: PUT /v1/profiles/me
    Auth-->>Client: Onboarding Complete -> Open Discovery Feed
```

---

### 3. Discovery Feed & Multi-Objective Ranking Pipeline

```mermaid
flowchart TD
    START(["User Opens Discovery Deck"]) --> FETCH_LOC["Client fetches GPS Coordinates & Filters"]
    FETCH_LOC --> REQ_FEED["POST /v1/discovery/feed { lat, lon, maxDistance, filters }"]

    subgraph Backend_Pipeline["Backend Discovery Pipeline"]
        CHECK_LIMIT{"Daily Free Swipes < 25<br/>OR User Has VIP Pass?"}
        CHECK_LIMIT -- No --> RET_GATED["Return Gated Feed (0 Daily Swipes Remaining)"]
        CHECK_LIMIT -- Yes --> SPATIAL["Calculate Spatial Bounding Box (Lat/Lon Delta)"]
        
        SPATIAL --> DB_SEARCH["CandidateSearchRepository.searchCandidates()<br/>• Bounding Box Pushdown<br/>• Target Gender / Orientation Pushdown<br/>• Age Range Pushdown<br/>• Exclude Incognito Users"]
        
        DB_SEARCH --> EXCLUDE_INTERACTION["SQL Subquery: Exclude self & prior interactions"]
        EXCLUDE_INTERACTION --> SHADOW_SHIELD["ShadowShield: Filter out matching phone hashes & corporate domains"]
        SHADOW_SHIELD --> RANKING["MultiObjectiveMatchEngine Scoring:<br/>Score = w1*Spatial + w2*Intent + w3*Astrology + w4*Karma + w5*Humor"]
        RANKING --> SORT_LIMIT["Sort by Composite Score & Take Top 25 Candidates"]
    end

    RET_GATED --> CLIENT_SPRINGBACK["Client Displays Spring-Back Physics on Deck & Prompts Store"]
    SORT_LIMIT --> DECK_CARDS["Client Displays Ranked Interactive Deck Cards"]

    DECK_CARDS --> USER_ACTION{"User Action on Candidate"}
    USER_ACTION -- Pass --> ACTION_PASS["POST /v1/discovery/interact { action: PASS }"]
    USER_ACTION -- Like / Swipe --> GATING_CHECK{"Like Limit Reached?"}
    
    GATING_CHECK -- Yes (Free user capped) --> SPRING_BACK["Deck Card Springs Back with Haptic Buzz<br/>Redirects to VibeStore Paywall"]
    GATING_CHECK -- No (Allowed) --> ACTION_LIKE["POST /v1/discovery/interact { action: LIKE }"]
    
    ACTION_LIKE --> CHECK_RECIPROCAL{"Reciprocal Like Exists<br/>from Target User?"}
    CHECK_RECIPROCAL -- No --> WAIT_LIKE["Interaction Saved in DB<br/>Wait for Target to Swipe"]
    CHECK_RECIPROCAL -- Yes --> AUTO_MATCH["Create Match in PENDING_ICEBREAKER<br/>Trigger Push Notification to Both Parties"]
```

---

### 4. Dual-Rail Monetization (User Choice Billing India)

```mermaid
sequenceDiagram
    autonumber
    actor User as User (App)
    participant Store as VibeStore UI (store.tsx)
    participant PaySvc as payments.ts Client
    participant PayCtrl as PaymentsController
    participant UPI as CashfreePaymentService
    participant IAP as IapVerificationService (StoreKit / Google Play)
    participant DB as PostgreSQL Database

    User->>Store: Opens VibeStore (Weekend Pass / Monthly Pass / DMs)
    Store->>PayCtrl: GET /v1/payments/catalog
    PayCtrl-->>Store: Returns SKU catalog items with pricing
    User->>Store: Taps "Unlock Pass" -> Opens Checkout Modal
    Store->>Store: Displays Parallel Square Billing Cards (Fintech Green Theme)

    alt Direct UPI / Alternative Gateway (India CCI Compliance)
        User->>Store: Selects "UPI / QR / Instant Card"
        Store->>PaySvc: executePurchase(item, 'CASHFREE_WEB')
        PaySvc->>PayCtrl: POST /v1/payments/order/create
        PayCtrl->>UPI: Create Cashfree Order ID
        UPI-->>PayCtrl: Return Payment Link / UPI Intent URL
        PayCtrl-->>PaySvc: Return Payment Session ID
        PaySvc->>User: Opens Native UPI Intent (GPay, PhonePe, Paytm)
        User->>UPI: Authorizes payment via UPI PIN
        UPI->>PayCtrl: POST /v1/payments/webhook (HMAC Signature Verified)
        PayCtrl->>DB: Provision Entitlements (hasActivePass=true, Sparks, DMs)
        PayCtrl-->>Store: Push Notification: "Pass Activated!"
    else Apple StoreKit 2 / Google Play Billing
        User->>Store: Selects "Google Play" or "Apple In-App Purchase"
        Store->>PaySvc: executePurchase(item, 'APPLE_STOREKIT' / 'GOOGLE_PLAY')
        PaySvc->>User: Native OS Biometric FaceID / Fingerprint Checkout
        User->>PaySvc: Confirms purchase via App Store / Google Play
        PaySvc->>PayCtrl: POST /v1/payments/iap/verify { receipt, sku }
        PayCtrl->>IAP: Validate JWS Receipt with Apple / Google Servers
        IAP-->>PayCtrl: Receipt Validated & Transaction Confirmed
        PayCtrl->>DB: Provision Entitlements (hasActivePass=true, Sparks, DMs)
        PayCtrl-->>Store: Return Success -> Refresh UI
    end

    opt Restore Purchases (Apple Guideline 3.1.1 Mandate)
        User->>Store: Taps "Restore Purchases"
        Store->>PayCtrl: GET /v1/profiles/me
        PayCtrl->>DB: Fetch Active Pass & Entitlements
        PayCtrl-->>Store: UserProfile with hasActivePass & Validity
        Store-->>User: Alert: "Purchases Restored: VIP Membership is Active!"
    end
```

---

### 5. Chat Lounge, Safe Date Spots & Shield 360 Moderation

```mermaid
flowchart TD
    MATCH_CREATED(["Mutual Match Created"]) --> ICEBREAKER_STATE["Match Status: PENDING_ICEBREAKER"]
    
    ICEBREAKER_STATE --> SOLVE_PROMPT["User A or B answers Icebreaker Prompt / Quiz"]
    SOLVE_PROMPT --> UNLOCK_LOUNGE["Match Status: ACTIVE_CHAT (Lounge Unlocked)"]

    subgraph Chat_Features["Active Chat Features"]
        MSG_TEXT["Text Chat (AES-256 GCM Encrypted)"]
        MSG_VOICE["15s Vernacular Voice Notes (Stored in R2 Storage)"]
        DATE_SPOTS["Safe Date Spots (Third Wave, Blue Tokai 15% discount)"]
        VIRTUAL_CHAI["Virtual Chai WebRTC Audio/Video Call (LiveKit)"]
    end

    UNLOCK_LOUNGE --> Chat_Features

    subgraph Safety_Moderation["Shield 360 & UGC Safety Controls"]
        MOD_CHECK{"Safety Action Triggered?"}
        MOD_CHECK -- "Unmatch" --> UNMATCH_ACTION["POST /v1/matches/{id}/unmatch<br/>Permanent chat close & DB status = UNMATCHED"]
        MOD_CHECK -- "Report Profile" --> REPORT_ACTION["POST /v1/profiles/{id}/report<br/>• Applies -50 Karma Penalty<br/>• Creates SupportTicket (SAFETY_HARASSMENT)<br/>• Excludes from deck forever"]
        MOD_CHECK -- "Block User" --> BLOCK_ACTION["POST /v1/profiles/{id}/block<br/>• Auto-marks interaction as PASS<br/>• Immediately severs active match"]
    end

    Chat_Features --> MOD_CHECK
```

---

### 6. Trust & Safety Support Hub & Right to Erasure Flow

```mermaid
flowchart TD
    subgraph Support_Hub["Help, Support & FAQs Hub"]
        FAQ_TAB["Browse FAQs Accordion (Billing, Verification, Privacy)"]
        RAISE_TAB["Raise Support Ticket (Category chips, Subject, Description)"]
        MY_TICKETS["My Tickets Tab (Live Status Badges: PENDING, IN PROGRESS, RESOLVED)"]
        RESOLVE_ACTION["Resolution Notes Displayed Upon Completion"]
    end

    subgraph Moderation_Queue["24-Hour Moderation & UGC Safety"]
        UGC_REPORT["User Flags Candidate Profile in Deck or Chat"]
        AUTO_TICKET["System Auto-creates SupportTicket (Category: SAFETY_HARASSMENT)"]
        KARMA_DOCK["Target Penalized -50 Karma Points"]
        ADMIN_REVIEW["Trust & Safety Team Reviews Ticket within 24h"]
        ADMIN_RESOLVE["Mark Resolved & Ban or Pardon Account"]
    end

    subgraph Account_Deletion["Right to Erasure (Delete Account)"]
        DEL_CLICK["User Taps 'Delete Account' in Profile Tab"]
        DEL_CONFIRM["2-Step Irreversible Confirmation Alert"]
        DEL_API["DELETE /v1/profiles/me"]
        
        subgraph Cascading_Wipe["Relational Cascading Data Purge"]
            WIPE_DESIRE["Delete from desire_profiles"]
            WIPE_TOKENS["Delete from push_tokens"]
            WIPE_SHIELDS["Delete from user_contact_shields"]
            WIPE_NOTIFS["Delete from user_notifications"]
            WIPE_PROF["Delete from profiles"]
            WIPE_USER["Delete from users"]
        end
        
        DEL_API --> Cascading_Wipe
        Cascading_Wipe --> PURGE_CLIENT["Client Clears AsyncStorage Auth Tokens & Redirects to /auth"]
    end

    UGC_REPORT --> AUTO_TICKET
    AUTO_TICKET --> KARMA_DOCK
    AUTO_TICKET --> MY_TICKETS
    ADMIN_REVIEW --> ADMIN_RESOLVE
    ADMIN_RESOLVE --> RESOLVE_ACTION
```

---

### 7. Relational Database Schema (ER Diagram)

```mermaid
erDiagram
    USERS ||--|| PROFILES : "has"
    USERS ||--o| DESIRE_PROFILES : "configures"
    USERS ||--o{ USER_CONTACT_SHIELDS : "protects"
    USERS ||--o{ INTERACTIONS : "initiates"
    USERS ||--o{ MATCHES : "participates_in"
    USERS ||--o{ CHAT_MESSAGES : "sends"
    USERS ||--o{ SUPPORT_TICKETS : "files"
    USERS ||--o{ UPI_ORDERS : "purchases"

    USERS {
        uuid id PK
        varchar phone_e164 UK
        enum gender
        double latitude
        double longitude
        boolean has_active_pass
        int karma_score
        timestamp created_at
    }

    PROFILES {
        uuid user_id PK, FK
        varchar display_name
        varchar city
        enum dietary_pref
        enum living_status
        boolean is_digilocker_verified
        float liveness_score
        int sparks_balance
        int direct_dms_balance
    }

    DESIRE_PROFILES {
        uuid user_id PK, FK
        int min_age
        int max_age
        int max_distance_km
        varchar dietary_harmony
    }

    INTERACTIONS {
        bigserial id PK
        uuid actor_id FK
        uuid target_id FK
        enum action_type
        varchar comment
        timestamp created_at
    }

    MATCHES {
        uuid id PK
        uuid user_a_id FK
        uuid user_b_id FK
        enum status
        varchar icebreaker_prompt
        timestamp expires_at
    }

    SUPPORT_TICKETS {
        uuid id PK
        uuid user_id FK
        varchar ticket_number UK
        enum category
        enum status
        varchar subject
        text description
        text resolution_notes
        timestamp created_at
    }

    UPI_ORDERS {
        varchar order_id PK
        uuid user_id FK
        varchar sku
        int amount_inr
        varchar status
        timestamp created_at
    }
```

---

## 📡 Core API Endpoint Catalog

| Module | Method & Path | Description | Security & Policy |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST /v1/auth/otp/send` | Dispatches 6-digit verification code via WhatsApp or SMS | Public (Rate Limited) |
| **Auth** | `POST /v1/auth/otp/verify` | Validates OTP and issues stateless JWT authorization token | Public |
| **Profile** | `GET /v1/profiles/me` | Fetches private profile, VIP pass status, and token balances | JWT Bearer |
| **Profile** | `PUT /v1/profiles/me` | Updates photos, bio, dietary preferences, and relationship intent | JWT Bearer |
| **Profile** | `DELETE /v1/profiles/me` | Cascading relational purge across all 6 data tables (DPDP Act) | JWT Bearer (Apple 5.1.1(v)) |
| **UGC Safety** | `POST /v1/profiles/{id}/report` | Reports objectionable profile, docks -50 karma & creates ticket | JWT Bearer (Apple Guideline 1.2) |
| **UGC Safety** | `POST /v1/profiles/{id}/block` | Blocks candidate and permanently excludes from discovery deck | JWT Bearer (Apple Guideline 1.2) |
| **Discovery** | `POST /v1/discovery/feed` | Fetches ranked candidates using spatial bounding box & AI score | JWT + 25 Swipes Hard Cap |
| **Discovery** | `POST /v1/discovery/interact` | Records Like, Pass, or Super Chai; triggers auto-match | JWT + Entitlement Gating |
| **Matches** | `GET /v1/matches` | Returns active matches, icebreakers, and expiring connections | JWT Bearer |
| **Matches** | `POST /v1/matches/{id}/unmatch`| Severs match connection and closes conversation history | JWT Bearer |
| **Payments** | `GET /v1/payments/catalog` | Returns catalog passes (Weekend, Monthly, Sachet DMs) | JWT Bearer |
| **Payments** | `POST /v1/payments/order/create`| Initiates Cashfree UPI order for alternative billing in India | JWT Bearer (Google Play UCB) |
| **Payments** | `POST /v1/payments/webhook` | Verifies Cashfree payment gateway HMAC-SHA256 signature | Signature Header Verified |
| **Payments** | `POST /v1/payments/iap/verify` | Validates Apple StoreKit 2 / Google Play purchase receipts | JWT Bearer |
| **Support** | `GET /v1/support/faqs` | Returns categorized FAQs (Billing, KYC, Privacy, Matching) | JWT Bearer |
| **Support** | `POST /v1/support/tickets` | Raises support or safety violation ticket with tracking number | JWT Bearer (Apple Guideline 1.2) |

---

## ⚙️ Environment Variables Reference

| Variable | Description | Default / Example |
| :--- | :--- | :--- |
| `SPRING_DATASOURCE_URL` | PostgreSQL connection URL | `jdbc:postgresql://localhost:5432/swipeai` |
| `SPRING_DATASOURCE_USERNAME` | PostgreSQL database user | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | PostgreSQL database password | `postgres` |
| `JWT_SECRET` | Secret key for HS256 HMAC JWT signing | `64-character-hex-or-base64-secret` |
| `CASHFREE_APP_ID` | Cashfree payment gateway client ID | `TEST_CF_APP_ID` |
| `CASHFREE_SECRET_KEY` | Cashfree payment gateway secret | `TEST_CF_SECRET_KEY` |
| `CASHFREE_ENV` | Cashfree environment (`SANDBOX` or `PRODUCTION`)| `SANDBOX` |
| `LIVEKIT_API_KEY` | LiveKit WebRTC Cloud API Key | `devkey` |
| `LIVEKIT_API_SECRET` | LiveKit WebRTC Cloud Secret Key | `secret` |
| `R2_ACCESS_KEY_ID` | Cloudflare R2 / S3 access key | `cloudflare_r2_access_key` |
| `R2_SECRET_ACCESS_KEY` | Cloudflare R2 / S3 secret key | `cloudflare_r2_secret_key` |

---

*Blunderr Dating (SwipeAI) • Engineering & Regulatory Compliance Playbook*
