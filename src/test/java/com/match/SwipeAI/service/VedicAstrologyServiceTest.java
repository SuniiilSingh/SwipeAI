package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.AstrologyDto;
import com.match.SwipeAI.model.UserAstrology;
import com.match.SwipeAI.repository.ProfileRepository;
import com.match.SwipeAI.repository.UserAstrologyRepository;
import com.match.SwipeAI.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class VedicAstrologyServiceTest {

    private final UserAstrologyRepository astrologyRepo = Mockito.mock(UserAstrologyRepository.class);
    private final UserRepository userRepo = Mockito.mock(UserRepository.class);
    private final ProfileRepository profileRepo = Mockito.mock(ProfileRepository.class);

    private final VedicAstrologyService service = new VedicAstrologyService(astrologyRepo, userRepo, profileRepo);

    @Test
    public void testComputeAstrology_RohiniMoon() {
        UUID userId = UUID.randomUUID();
        LocalDate dob = LocalDate.of(1996, 5, 20); // May 20, 1996
        LocalTime time = LocalTime.of(14, 30);
        Mockito.when(astrologyRepo.save(Mockito.any())).thenAnswer(i -> i.getArgument(0));
        UserAstrology a = service.computeAndSaveAstrology(userId, dob, time, "Bengaluru", 12.97, 77.59);

        assertNotNull(a);
        assertEquals(userId, a.getUserId());
        assertNotNull(a.getNakshatraName());
        assertNotNull(a.getChandraRashi());
        assertNotNull(a.getSunSign());
        assertNotNull(a.getYoniAnimal());
        assertNotNull(a.getGana());
        assertNotNull(a.getNadi());
        assertTrue(a.getNumerologyNumber() >= 1 && a.getNumerologyNumber() <= 9);
        assertTrue(a.getNakshatraId() >= 1 && a.getNakshatraId() <= 27);
    }

    @Test
    public void testAshtakootMatchCalculation() {
        UUID u1 = UUID.randomUUID();
        UUID u2 = UUID.randomUUID();

        UserAstrology a1 = UserAstrology.builder()
                .userId(u1)
                .nakshatraId(4) // Rohini (Serpent, Manushya, Antya)
                .nakshatraName("Rohini")
                .chandraRashi("Taurus (Vrishabha)")
                .chandraRashiLord("Venus")
                .varna("Vaishya")
                .vashya("Chatushpada")
                .yoniAnimal("Serpent")
                .gana("Manushya")
                .nadi("Antya")
                .isManglik(false)
                .isExactTimeProvided(true)
                .build();

        UserAstrology a2 = UserAstrology.builder()
                .userId(u2)
                .nakshatraId(7) // Punarvasu (Cat, Deva, Aadi)
                .nakshatraName("Punarvasu")
                .chandraRashi("Gemini (Mithuna)")
                .chandraRashiLord("Mercury")
                .varna("Shudra")
                .vashya("Manava")
                .yoniAnimal("Cat")
                .gana("Deva")
                .nadi("Aadi")
                .isManglik(false)
                .isExactTimeProvided(false)
                .build();

        Mockito.when(astrologyRepo.findById(u1)).thenReturn(Optional.of(a1));
        Mockito.when(astrologyRepo.findById(u2)).thenReturn(Optional.of(a2));

        AstrologyDto.AshtakootMatchResponse match = service.calculateAshtakootMatch(u1, u2);

        assertNotNull(match);
        assertTrue(match.getTotalScore() >= 0 && match.getTotalScore() <= 36);
        assertEquals(36, match.getMaxScore());
        assertEquals(8, match.getNadiScore()); // Different Nadi (Antya vs Aadi) = 8 pts!
        assertNotNull(match.getVibeTitle());
        assertNotNull(match.getVibeSummary());
        assertTrue(match.getIsManglikCompatible());
    }
}
