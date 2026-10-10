package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Persists Marketing & Offer Email Campaigns dispatched from the BlunderR Admin Command Center.
 */
@Entity
@Table(name = "marketing_email_campaigns")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketingEmailCampaign {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String subject;

    @Column(name = "offer_badge", length = 100)
    private String offerBadge;

    @Column(name = "coupon_code", length = 50)
    private String couponCode;

    @Column(name = "cta_text", length = 80)
    private String ctaText;

    @Column(name = "cta_url", length = 255)
    private String ctaUrl;

    @Column(name = "body_text", columnDefinition = "TEXT", nullable = false)
    private String bodyText;

    @Column(name = "audience_filter", length = 50)
    private String audienceFilter;

    @Column(name = "city_filter", length = 80)
    private String cityFilter;

    @Column(name = "recipients_count")
    private Integer recipientsCount;

    @Column(name = "smtp_delivered_count")
    private Integer smtpDeliveredCount;

    @Column(name = "in_app_delivered_count")
    private Integer inAppDeliveredCount;

    @Column(name = "sent_by_email", length = 120)
    private String sentByEmail;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}
