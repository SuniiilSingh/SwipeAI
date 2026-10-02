package com.match.SwipeAI.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller serving public static web pages for Terms of Service, Privacy Policy,
 * Data Safety & Account Deletion disclosures required by Apple & Google Play Store guidelines.
 */
@Controller
public class WebPageController {

    @GetMapping({"/terms", "/terms.html", "/eula", "/terms-of-service"})
    public String terms() {
        return "forward:/terms.html";
    }

    @GetMapping({"/privacy", "/privacy.html", "/privacy-policy"})
    public String privacy() {
        return "forward:/privacy.html";
    }

    @GetMapping({"/delete-account", "/delete-account.html", "/data-deletion"})
    public String deleteAccount() {
        return "forward:/delete-account.html";
    }

    @GetMapping({"/"})
    public String index() {
        return "forward:/index.html";
    }
}
