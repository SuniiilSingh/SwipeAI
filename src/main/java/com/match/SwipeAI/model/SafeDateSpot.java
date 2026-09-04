package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Safe Date Spot (O2O Partner Cafe) Entity.
 * Represents verified, well-lit partner cafes (Blue Tokai, Third Wave Coffee, Starbucks)
 * offering 15% discount coupons and automated live SOS check-in tracking.
 */
@Entity
@Table(name = "safe_date_spots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SafeDateSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 50)
    private String brand; // Blue Tokai, Third Wave Coffee, Starbucks, Olive

    @Column(nullable = false, length = 255)
    private String address;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(length = 50)
    private String neighborhood;

    @Column(nullable = false)
    private Double latitude;

    @Column(nullable = false)
    private Double longitude;

    @Builder.Default
    @Column(name = "discount_percent")
    private Integer discountPercent = 15;

    @Column(name = "coupon_code", length = 30)
    private String couponCode;

    @Builder.Default
    @Column(name = "sos_enabled")
    private Boolean sosEnabled = true;

    @Column(name = "photo_url")
    private String photoUrl;
}
