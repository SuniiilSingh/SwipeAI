package com.match.SwipeAI.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Dedicated Vedic Astrology and Kundali Profile Entity.
 * Stores user-specific astrological attributes, calculated Nakshatra,
 * Moon Sign, Sun Sign, Lagna, and 36-Guna archetypes.
 */
@Entity
@Table(name = "user_astrology", indexes = {
    @Index(name = "idx_user_astro_nakshatra", columnList = "nakshatra_id"),
    @Index(name = "idx_user_astro_rashi", columnList = "chandra_rashi")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserAstrology {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    // Optional user-provided exact birth details (Private)
    @Column(name = "birth_time", length = 10)
    private String birthTime; // "HH:mm" e.g. "14:30"

    @Column(name = "birth_city", length = 100)
    private String birthCity;

    @Column(name = "birth_lat")
    private Double birthLat;

    @Column(name = "birth_lng")
    private Double birthLng;

    @Builder.Default
    @Column(name = "is_exact_time_provided", nullable = false)
    private Boolean isExactTimeProvided = false;

    // Computed Astronomical & Vedic Values
    @Column(name = "nakshatra_id", nullable = false)
    private Integer nakshatraId; // 1 to 27 (1 = Ashwini ... 27 = Revati)

    @Column(name = "nakshatra_name", nullable = false, length = 50)
    private String nakshatraName;

    @Builder.Default
    @Column(name = "nakshatra_pada")
    private Integer nakshatraPada = 1; // 1 to 4

    @Column(name = "chandra_rashi", nullable = false, length = 50)
    private String chandraRashi; // e.g. "Taurus (Vrishabha)"

    @Column(name = "chandra_rashi_lord", length = 30)
    private String chandraRashiLord; // e.g. "Venus (Shukra)"

    @Column(name = "sun_sign", nullable = false, length = 30)
    private String sunSign; // e.g. "Aries"

    @Column(name = "lagna_sign", length = 50)
    private String lagnaSign; // e.g. "Leo (Simha)"

    // 8 Compatibility Archetypes for Ashtakoot Milan
    @Column(name = "varna", length = 30)
    private String varna; // Brahmin, Kshatriya, Vaishya, Shudra

    @Column(name = "vashya", length = 30)
    private String vashya; // Chatushpada, Manava, Jalachara, Keeta, Vanachara

    @Column(name = "yoni_animal", length = 30)
    private String yoniAnimal; // Horse, Elephant, Sheep, Snake, Dog, Cat, Rat, Cow, Buffalo, Tiger, Hare, Monkey, Lion, Mongoose

    @Column(name = "gana", length = 30)
    private String gana; // Deva, Manushya, Rakshasa

    @Column(name = "nadi", length = 30)
    private String nadi; // Aadi, Madhya, Antya

    @Column(name = "numerology_number")
    private Integer numerologyNumber; // 1 to 9

    @Builder.Default
    @Column(name = "is_manglik", nullable = false)
    private Boolean isManglik = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
