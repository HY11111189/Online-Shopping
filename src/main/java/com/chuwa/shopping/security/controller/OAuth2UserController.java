package com.chuwa.shopping.security.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 User Info and Token Validation Controller
 */
@RestController
@RequestMapping("/api/v1/oauth2")
@CrossOrigin(origins = "${app.cors.allowed-origins}", allowedHeaders = "*")
public class OAuth2UserController {

    /**
     * Get current user info from OAuth2 asymmetric JWT token
     */
    @GetMapping("/userinfo")
    public Map<String, Object> userInfo(Authentication authentication) {
        Map<String, Object> userInfo = new HashMap<>();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            userInfo.put("username", jwt.getSubject());
            userInfo.put("scopes", jwt.getClaim("scope"));
            userInfo.put("authorities", authentication.getAuthorities());
            userInfo.put("authenticated", true);
            userInfo.put("token_type", "Asymmetric JWT (RSA)");
        } else {
            userInfo.put("authenticated", false);
        }

        return userInfo;
    }

    /**
     * Validate asymmetric JWT token
     */
    @GetMapping("/validate")
    public Map<String, Object> validateToken(Authentication authentication) {
        Map<String, Object> response = new HashMap<>();

        if (authentication != null && authentication.isAuthenticated()) {
            response.put("valid", true);
            response.put("username", authentication.getName());
            response.put("authorities", authentication.getAuthorities());
            response.put("token_type", "Asymmetric JWT");
        } else {
            response.put("valid", false);
        }

        return response;
    }

    /**
     * Get detailed token information
     */
    @GetMapping("/token-details")
    public Map<String, Object> tokenDetails(Authentication authentication) {
        Map<String, Object> tokenInfo = new HashMap<>();

        if (authentication != null && authentication.getPrincipal() instanceof Jwt) {
            Jwt jwt = (Jwt) authentication.getPrincipal();
            tokenInfo.put("subject", jwt.getSubject());
            tokenInfo.put("issuer", jwt.getIssuer());
            tokenInfo.put("issued_at", jwt.getIssuedAt());
            tokenInfo.put("expires_at", jwt.getExpiresAt());
            tokenInfo.put("algorithm", jwt.getHeaders().get("alg"));
            tokenInfo.put("key_id", jwt.getHeaders().get("kid"));
            tokenInfo.put("token_type", "Asymmetric JWT (RSA)");
            tokenInfo.put("claims", jwt.getClaims());
        } else {
            tokenInfo.put("error", "No valid JWT token found");
        }

        return tokenInfo;
    }
}
