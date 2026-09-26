package com.misl.leaveflow.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Browser origins allowed to call the API directly, bound from {@code app.cors.allowed-origins}.
 * Needed in local development, where the Vite dev server runs on a different port than the API.
 */
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
