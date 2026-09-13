package com.match.SwipeAI.model;

import com.match.SwipeAI.enums.DietaryPreference;
import com.match.SwipeAI.enums.LivingStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Cultural Context and Comprehensive Profile Entity.
 * Captures deep socio-cultural indicators, full profile completion fields,
 * photo grid (6 photos + selfie), interests, prompts, and preferences.
 */
@Entity
@Table(name = "profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Profile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "dietary_pref", length = 30)
    private DietaryPreference dietaryPref;

    @Enumerated(EnumType.STRING)
    @Column(name = "living_status", length = 30)
    private LivingStatus livingStatus;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "profile_languages", joinColumns = @JoinColumn(name = "profile_user_id"))
    @Column(name = "language")
    @Builder.Default
    private List<String> languagesSpoken = new ArrayList<>();

    @Column(name = "zodiac_sign", length = 20)
    private String zodiacSign;

    @Column(name = "sun_sign", length = 20)
    private String sunSign;

    @Column(name = "moon_sign", length = 20)
    private String moonSign;

    @Column(name = "voice_prompt_url")
    private String voicePromptUrl;

    @Column(name = "voice_prompt_duration")
    private Integer voicePromptDuration; // in seconds

    @Column(name = "voice_prompt_text")
    private String voicePromptText;

    @Column(name = "company", length = 100)
    private String company;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "job", length = 100)
    private String job;

    @Column(name = "education", length = 100)
    private String education;

    @Column(name = "institute", length = 100)
    private String institute;

    @Column(name = "interests", length = 512)
    private String interests;

    @Column(name = "height")
    private Integer height; // in cm

    @Column(name = "location", length = 100)
    private String location;

    @Column(name = "max_distance_km")
    private Integer maxDistanceKm;

    @Column(name = "sexual_orientation", length = 50)
    private String sexualOrientation;

    @Builder.Default
    @Column(name = "show_orientation_on_profile")
    private Boolean showOrientationOnProfile = true;

    @Column(name = "gender_display", length = 50)
    private String genderDisplay;

    @Builder.Default
    @Column(name = "show_gender_on_profile")
    private Boolean showGenderOnProfile = true;

    @Column(name = "gender_preference_display", length = 50)
    private String genderPreferenceDisplay;

    @Column(name = "relationship_intent", length = 50)
    private String relationshipIntent;

    @Column(name = "profile_prompt_question", length = 255)
    private String profilePromptQuestion;

    @Column(name = "profile_prompt_answer", length = 512)
    private String profilePromptAnswer;

    @Column(name = "photo1", length = 512)
    private String photo1;

    @Column(name = "photo2", length = 512)
    private String photo2;

    @Column(name = "photo3", length = 512)
    private String photo3;

    @Column(name = "photo4", length = 512)
    private String photo4;

    @Column(name = "photo5", length = 512)
    private String photo5;

    @Column(name = "photo6", length = 512)
    private String photo6;

    @Column(name = "selfie_url", length = 512)
    private String selfieUrl;

    @Column(name = "smoking_habit", length = 50)
    private String smokingHabit;

    @Column(name = "drinking_habit", length = 50)
    private String drinkingHabit;

    @Column(name = "hobbies", length = 512)
    private String hobbies;

    @Column(name = "vacation_preference", length = 50)
    private String vacationPreference;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "neighborhood", length = 50)
    private String neighborhood;

    @Column(name = "micro_circle", length = 100)
    private String microCircle;

    @Column(name = "meme_swipes", columnDefinition = "TEXT")
    private String memeSwipesJson;

    @Column(name = "selected_meme_url", length = 512)
    private String selectedMemeUrl;

    @Column(name = "selected_meme_title", length = 150)
    private String selectedMemeTitle;

    @Column(name = "photos_json", columnDefinition = "TEXT")
    private String photosJson;

    @Column(name = "embedding_vector", columnDefinition = "TEXT")
    private String embeddingVector;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}
