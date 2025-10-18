package com.chuwa.redbook.controller;

import com.chuwa.redbook.security.RsaKeyProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Controller to view RSA key information (metadata only, not the actual keys)
 */
@RestController
@RequestMapping("/api/v1/keys")
@CrossOrigin(origins = "${app.cors.allowed-origins}", allowedHeaders = "*")
public class KeyInfoController {

    @Autowired
    private RsaKeyProperties rsaKeyProperties;

    /**
     * Get public key information
     * This is safe to expose - returns the public key which can be used to verify tokens
     */
    @GetMapping("/public-key")
    public Map<String, Object> getPublicKeyInfo() {
        Map<String, Object> keyInfo = new HashMap<>();
        keyInfo.put("algorithm", rsaKeyProperties.getPublicKey().getAlgorithm());
        keyInfo.put("format", rsaKeyProperties.getPublicKey().getFormat());
        keyInfo.put("key_size", rsaKeyProperties.getPublicKey().getModulus().bitLength());
        keyInfo.put("public_key_pem", formatPublicKeyToPEM());
        keyInfo.put("storage_location", rsaKeyProperties.getKeyStorePath());
        keyInfo.put("jwks_uri", "http://localhost:8080/oauth2/jwks");
        return keyInfo;
    }

    /**
     * Get key metadata (no sensitive information)
     */
    @GetMapping("/info")
    public Map<String, String> getKeyMetadata() {
        Map<String, String> info = new HashMap<>();
        info.put("key_type", "RSA");
        info.put("key_size", String.valueOf(rsaKeyProperties.getPublicKey().getModulus().bitLength()) + " bits");
        info.put("storage_path", rsaKeyProperties.getKeyStorePath());
        info.put("algorithm", "RS256 (RSA Signature with SHA-256)");
        info.put("public_key_access", "Available at /oauth2/jwks");
        info.put("private_key_access", "Secured - not accessible via API");
        return info;
    }

    /**
     * Format public key to PEM format
     */
    private String formatPublicKeyToPEM() {
        byte[] encoded = rsaKeyProperties.getPublicKey().getEncoded();
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(encoded);
        return "-----BEGIN PUBLIC KEY-----\n" + base64 + "\n-----END PUBLIC KEY-----";
    }
}
