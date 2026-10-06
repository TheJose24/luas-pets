package com.luaspets.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the public static manifest with its registered media type. */
@RestController
public class PwaManifestController {
    @GetMapping(value = "/manifest.webmanifest", produces = "application/manifest+json")
    public Resource manifest() {
        return new ClassPathResource("static/manifest.webmanifest");
    }
}
