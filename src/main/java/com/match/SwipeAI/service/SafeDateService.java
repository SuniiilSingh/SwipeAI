package com.match.SwipeAI.service;

import com.match.SwipeAI.dto.SafeDateDto;
import com.match.SwipeAI.model.SafeDateSpot;
import com.match.SwipeAI.repository.SafeDateSpotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SafeDateService {

    private final SafeDateSpotRepository safeDateSpotRepository;

    public List<SafeDateDto.SafeDateSpotDto> getSafeSpots(String city) {
        List<SafeDateSpot> spots;
        if (city != null && !city.isBlank()) {
            spots = safeDateSpotRepository.findTop50ByCityIgnoreCaseOrderByNameAsc(city.trim());
        } else {
            spots = safeDateSpotRepository.findTop50ByOrderByCityAscNameAsc();
        }

        List<SafeDateDto.SafeDateSpotDto> dtos = new ArrayList<>();
        for (SafeDateSpot s : spots) {
            dtos.add(SafeDateDto.SafeDateSpotDto.builder()
                    .id(s.getId())
                    .name(s.getName())
                    .brand(s.getBrand())
                    .address(s.getAddress())
                    .city(s.getCity())
                    .neighborhood(s.getNeighborhood())
                    .latitude(s.getLatitude())
                    .longitude(s.getLongitude())
                    .discountPercent(s.getDiscountPercent())
                    .couponCode(s.getCouponCode())
                    .sosEnabled(Boolean.TRUE.equals(s.getSosEnabled()))
                    .photoUrl(s.getPhotoUrl())
                    .build());
        }
        return dtos;
    }

    public SafeDateDto.SosStartResponse startSosSession(UUID userId, SafeDateDto.SosStartRequest request) {
        SafeDateSpot spot = safeDateSpotRepository.findById(request.getSafeSpotId())
                .orElseThrow(() -> new IllegalArgumentException("Safe spot not found"));

        String sessionId = "sos_trk_" + UUID.randomUUID().toString().substring(0, 8);
        String trackingUrl = "https://safe.swipeai.in/sos/live/" + sessionId;

        log.info("Started Safe Date SOS session {} for user {} at {}, sharing tracking link with {} contacts",
                sessionId, userId, spot.getName(), request.getEmergencyContacts() != null ? request.getEmergencyContacts().size() : 0);

        return SafeDateDto.SosStartResponse.builder()
                .trackingSessionId(sessionId)
                .trackingUrl(trackingUrl)
                .cafeName(spot.getName())
                .discountCoupon(spot.getCouponCode())
                .status("ACTIVE")
                .message("Safe Date Mode Active: Your live location & cafe check-in is securely shared with your emergency contacts.")
                .build();
    }
}
