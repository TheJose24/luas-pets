package com.luaspets.config;

import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Public caching applies only to these immutable-in-identity, own static files. */
@Configuration
public class PwaWebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl publicAssets = CacheControl.maxAge(Duration.ZERO).cachePublic().mustRevalidate();
        registry.addResourceHandler("/css/luaspets.css")
            .addResourceLocations("classpath:/static/").setCacheControl(publicAssets);
        registry.addResourceHandler("/js/carrito.js", "/js/notificaciones.js", "/js/pwa.js")
            .addResourceLocations("classpath:/static/").setCacheControl(publicAssets);
        registry.addResourceHandler("/icons/icon-192.png", "/icons/icon-512.png",
                "/icons/icon-maskable-512.png", "/icons/apple-touch-icon.png")
            .addResourceLocations("classpath:/static/").setCacheControl(publicAssets);
    }
}
