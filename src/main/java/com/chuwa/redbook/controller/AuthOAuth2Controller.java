package com.chuwa.redbook.controller;

import com.chuwa.redbook.dao.security.RoleRepository;
import com.chuwa.redbook.dao.security.UserRepository;
import com.chuwa.redbook.entity.security.Role;
import com.chuwa.redbook.entity.security.User;
import com.chuwa.redbook.payload.security.SignUpDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth2 Authentication Controller with Asymmetric JWT
 * Replaces the symmetric JWT authentication
 */
@RestController
@RequestMapping("/api/v1/auth/oauth2")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT, RequestMethod.DELETE}) // Allow all for the entire controller
public class AuthOAuth2Controller {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Value("${app.oauth2.client-id}")
    private String clientId;

    @Value("${app.oauth2.client-secret}")
    private String clientSecret;

    @Value("${spring.security.oauth2.authorizationserver.issuer}")
    private String issuer;

    private static final Logger logger = LoggerFactory.getLogger(AuthOAuth2Controller.class);

    /**
     * User signup endpoint
     */
    @PostMapping("/signup")
    public ResponseEntity<?> registerUser(@RequestBody SignUpDto signUpDto) {
        logger.info("New User is trying to sign up: {}", signUpDto.getAccount());

        // check if username is in a DB
        if (userRepository.existsByAccount(signUpDto.getAccount())) {
            return new ResponseEntity<>("Username is already taken!", HttpStatus.BAD_REQUEST);
        }

        // check if email exists in DB
        if (userRepository.existsByEmail(signUpDto.getEmail())) {
            return new ResponseEntity<>("Email is already taken!", HttpStatus.BAD_REQUEST);
        }

        // create user object
        User user = new User();
        user.setName(signUpDto.getName());
        user.setAccount(signUpDto.getAccount());
        user.setEmail(signUpDto.getEmail());
        user.setPassword(passwordEncoder.encode(signUpDto.getPassword()));

        Role roles = null;
        if (signUpDto.getAccount().contains("chuwa")) {
            roles = roleRepository.findByName("ROLE_ADMIN").get();
        } else {
            roles = roleRepository.findByName("ROLE_USER").get();
        }

        user.setRoles(Collections.singleton(roles));
        userRepository.save(user);

        logger.info("User registered successfully: {}", signUpDto.getAccount());

        Map<String, String> response = new HashMap<>();
        response.put("message", "User registered successfully. Use OAuth2 token endpoint to get access token.");
        response.put("username", signUpDto.getAccount());
        response.put("token_endpoint", issuer + "/oauth2/token");

        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * Get OAuth2 token endpoint information
     * This provides clients with the information needed to obtain tokens using asymmetric JWT
     */
    @GetMapping("/token-info")
    public ResponseEntity<Map<String, String>> getTokenEndpoint() {
        Map<String, String> endpoints = new HashMap<>();
        endpoints.put("token_endpoint", issuer + "/oauth2/token");
        endpoints.put("authorization_endpoint", issuer + "/oauth2/authorize");
        endpoints.put("jwks_uri", issuer + "/oauth2/jwks");
        endpoints.put("grant_types_supported", "password, client_credentials, authorization_code, refresh_token");
        endpoints.put("token_type", "Bearer (Asymmetric JWT with RSA)");
        endpoints.put("client_id", clientId);
        endpoints.put("scopes", "read, write, openid, profile");
        endpoints.put("authentication_type", "OAuth 2.0 with Asymmetric RSA JWT");

        return ResponseEntity.ok(endpoints);
    }

    /**
     * Example: How to get token using password grant
     */
    @GetMapping("/how-to-login")
    public ResponseEntity<Map<String, Object>> howToLogin() {
        Map<String, Object> instructions = new HashMap<>();
        instructions.put("description", "OAuth2 Password Grant with Asymmetric JWT");
        instructions.put("endpoint", issuer + "/oauth2/token");
        instructions.put("method", "POST");
        instructions.put("content_type", "application/x-www-form-urlencoded");
        instructions.put("authentication", "Basic Auth with client credentials");

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Basic base64(client_id:client_secret)");
        headers.put("Content-Type", "application/x-www-form-urlencoded");
        instructions.put("headers", headers);

        Map<String, String> body = new HashMap<>();
        body.put("grant_type", "password");
        body.put("username", "your_username");
        body.put("password", "your_password");
        body.put("scope", "read write");
        instructions.put("body", body);

        String curlExample = String.format(
            "curl -X POST %s/oauth2/token \\\n" +
            "  -u %s:%s \\\n" +
            "  -H \"Content-Type: application/x-www-form-urlencoded\" \\\n" +
            "  -d \"grant_type=password&username=YOUR_USERNAME&password=YOUR_PASSWORD&scope=read write\"",
            issuer, clientId, clientSecret
        );
        instructions.put("curl_example", curlExample);

        return ResponseEntity.ok(instructions);
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        Map<String, String> response = new HashMap<>();
        response.put("status", "UP");
        response.put("auth_type", "OAuth2 with Asymmetric RSA JWT");
        response.put("jwt_signing", "RSA 2048-bit");
        response.put("issuer", issuer);
        return ResponseEntity.ok(response);
    }

    /**
     * Get public JWK Set endpoint (for token verification)
     */
    @GetMapping("/public-keys-info")
    public ResponseEntity<Map<String, String>> publicKeysInfo() {
        Map<String, String> response = new HashMap<>();
        response.put("jwks_uri", issuer + "/oauth2/jwks");
        response.put("description", "Public keys for verifying asymmetric JWT tokens");
        response.put("algorithm", "RS256 (RSA Signature with SHA-256)");
        return ResponseEntity.ok(response);
    }
}
