package com.match.SwipeAI.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * REST Controller serving public static legal web pages (Terms, Privacy Policy, EULA, Delete Account)
 * directly as UTF-8 HTML with zero view engine dependencies.
 */
@RestController
public class WebPageController {

    private ResponseEntity<String> renderStaticHtml(String filename) {
        try {
            ClassPathResource resource = new ClassPathResource("static/" + filename);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            String content = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(content);
        } catch (IOException e) {
            return ResponseEntity.internalServerError().body("Error loading page");
        }
    }

    @GetMapping({"/terms", "/terms.html", "/eula", "/terms-of-service"})
    public ResponseEntity<String> terms() {
        return renderStaticHtml("terms.html");
    }

    @GetMapping({"/privacy", "/privacy.html", "/privacy-policy"})
    public ResponseEntity<String> privacy() {
        return renderStaticHtml("privacy.html");
    }

    @GetMapping({"/delete-account", "/delete-account.html", "/data-deletion"})
    public ResponseEntity<String> deleteAccount() {
        return renderStaticHtml("delete-account.html");
    }

    @GetMapping({"/"})
    public ResponseEntity<String> index() {
        return renderStaticHtml("index.html");
    }
}
