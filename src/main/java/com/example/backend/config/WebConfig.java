package com.example.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.example.backend.common.api.ApiVersions;
import com.example.backend.common.api.MajorVersionParser;

/**
 * HTTP-level configuration: API versioning, and CORS for deployments where
 * the frontend is served from a different origin than the API.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${app.cors.allowed-origins}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /** {@code /api/v1/tasks}: path segment 1 carries the version. */
    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer.usePathSegment(1)
                .setVersionParser(new MajorVersionParser())
                .addSupportedVersions(ApiVersions.V1);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE")
                .allowedHeaders("Content-Type", "X-XSRF-TOKEN", "X-Request-ID")
                // Session cookie. Cross-site cookies also need SameSite=None; prefer serving
                // the API from the frontend's origin through a rewrite (see README).
                .allowCredentials(true)
                .exposedHeaders("Location", "X-Request-ID", "Retry-After")
                .maxAge(3600);
    }
}
