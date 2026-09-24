package com.match.SwipeAI.enums;

/**
 * Payment Provider Rails supported by Blunderr Hybrid Monetization.
 */
public enum PaymentProvider {
    /**
     * Native Google Play In-App Billing (Android App Store).
     */
    GOOGLE_PLAY,

    /**
     * Native Apple In-App Purchase StoreKit (iOS App Store).
     */
    APPLE_STOREKIT,

    /**
     * Cashfree Payments Gateway (Web Checkout, UPI Intent, and Out-of-band deals).
     */
    CASHFREE,

    /**
     * Direct NPCI UPI Intent & QR.
     */
    RAZORPAY_UPI
}
