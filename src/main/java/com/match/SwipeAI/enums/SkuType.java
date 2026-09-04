package com.match.SwipeAI.enums;

/**
 * 3-Tier Indian Sachet Monetization SKUs.
 * Designed to bypass Western credit card auto-debit friction (<6% credit card penetration in India)
 * via instant micro-transactions (₹19–₹99) using UPI (GPay/PhonePe/Paytm/BHIM).
 */
public enum SkuType {
    /**
     * ₹19 Super Spark: Highlights profile at the top of candidate feed.
     */
    SUPER_SPARK_19(1900, "₹19 Super Spark"),

    /**
     * ₹19 Revive Match: Unfreezes an expired 48h match and restores chat access.
     */
    REVIVE_MATCH_19(1900, "₹19 Revive Expired Match"),

    /**
     * ₹21 Cutting Chai: Sends a virtual chai micro-invite + 15% partner cafe discount coupon.
     */
    CUTTING_CHAI_21(2100, "₹21 Virtual Cutting Chai + Cafe Coupon"),

    /**
     * ₹29 Friday Night Boost: 10x profile visibility during 9 PM - 1 AM peak hours.
     */
    BOOST_1X_FRIDAY_29(2900, "₹29 Friday Night Boost"),

    /**
     * ₹49 3 Direct DMs: Skip the matching queue and send direct notes to high-intent profiles.
     */
    DIRECT_DMS_3X_49(4900, "₹49 3 Direct DMs"),

    /**
     * ₹99 Weekend Dating Pass: Unlimited likes + 3 Super Sparks for peak Friday–Sunday dating.
     */
    WEEKEND_PASS_99(9900, "₹99 Weekend Dating Pass"),

    /**
     * ₹149 Weekly VIP Pass: 7-day full VIP access with priority matching.
     */
    WEEKLY_PASS_149(14900, "₹149 Weekly VIP Pass"),

    /**
     * ₹199 14-Day Fortnight Pass: 14-day full VIP access + 6 Super Sparks + 2 Boosts.
     */
    FORTNIGHT_PASS_199(19900, "₹199 14-Day Fortnight Dating Pass"),

    /**
     * ₹999 Select Club: Quarterly high-income concierge recommendations and priority DigiLocker pool.
     */
    SELECT_QUARTERLY_999(99900, "₹999 Select Club Quarterly");

    private final int amountPaise;
    private final String description;

    SkuType(int amountPaise, String description) {
        this.amountPaise = amountPaise;
        this.description = description;
    }

    /**
     * Returns the price in paise (1 INR = 100 paise).
     */
    public int getAmountPaise() {
        return amountPaise;
    }

    /**
     * Returns the user-facing description.
     */
    public String getDescription() {
        return description;
    }
}
