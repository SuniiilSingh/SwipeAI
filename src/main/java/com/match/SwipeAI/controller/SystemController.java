package com.match.SwipeAI.controller;

import com.match.SwipeAI.dto.SystemDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/system")
public class SystemController {

    @GetMapping("/app-version")
    public ResponseEntity<SystemDto.AppVersionResponse> getAppVersion() {
        SystemDto.AppVersionResponse response = SystemDto.AppVersionResponse.builder()
                .latestVersionName("1.0.8")
                .latestVersionCode(9)
                .minSupportedVersionCode(1)
                .updateType("FLEXIBLE")
                .title("New Update Available ✨")
                .message("BlunderR 1.0.8 includes audio note snippets on Discovery, neighborhood privacy tiers, and improved matching!")
                .apkDownloadUrl("https://blunderr.in/downloads/BlunderR.apk")
                .playStoreUrl("https://play.google.com/store/apps/details?id=com.blunderr.dating")
                .build();
        return ResponseEntity.ok(response);
    }
}
