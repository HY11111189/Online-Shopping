package com.chuwa.redbook.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.security.*;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * RSA Key Properties for asymmetric JWT authentication
 * Generates and stores RSA key pair for signing and validating tokens
 * Keys are persisted to files and loaded on startup
 */
@Component
public class RsaKeyProperties {

    private static final Logger logger = LoggerFactory.getLogger(RsaKeyProperties.class);

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @Value("${app.oauth2.key-store-path:src/main/resources/certificates}")
    private String keyStorePath;

    private static final String PRIVATE_KEY_FILE = "private_key.pem";
    private static final String PUBLIC_KEY_FILE = "public_key.pem";

    public RsaKeyProperties() {
        // Keys will be loaded after Spring sets the properties
    }

    /**
     * Initialize keys - called after properties are set
     */
    public void init() {
        try {
            File keyDir = new File(keyStorePath);
            File privateKeyFile = new File(keyDir, PRIVATE_KEY_FILE);
            File publicKeyFile = new File(keyDir, PUBLIC_KEY_FILE);

            if (privateKeyFile.exists() && publicKeyFile.exists()) {
                logger.info("Loading existing RSA keys from: {}", keyStorePath);
                loadKeysFromFiles(privateKeyFile, publicKeyFile);
            } else {
                logger.info("Generating new RSA key pair and saving to: {}", keyStorePath);
                generateAndSaveKeys(keyDir, privateKeyFile, publicKeyFile);
            }

            logger.info("RSA keys successfully initialized");
            logger.info("Public key location: {}", publicKeyFile.getAbsolutePath());
            logger.info("Private key location: {}", privateKeyFile.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to initialize RSA keys", e);
            throw new IllegalStateException("Failed to initialize RSA keys", e);
        }
    }

    /**
     * Load keys from files
     */
    private void loadKeysFromFiles(File privateKeyFile, File publicKeyFile) throws Exception {
        // Load private key
        byte[] privateKeyBytes = Files.readAllBytes(privateKeyFile.toPath());
        String privateKeyPEM = new String(privateKeyBytes)
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] privateKeyDecoded = Base64.getDecoder().decode(privateKeyPEM);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyDecoded);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        this.privateKey = (RSAPrivateKey) keyFactory.generatePrivate(privateKeySpec);

        // Load public key
        byte[] publicKeyBytes = Files.readAllBytes(publicKeyFile.toPath());
        String publicKeyPEM = new String(publicKeyBytes)
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s", "");
        byte[] publicKeyDecoded = Base64.getDecoder().decode(publicKeyPEM);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyDecoded);
        this.publicKey = (RSAPublicKey) keyFactory.generatePublic(publicKeySpec);
    }

    /**
     * Generate new keys and save to files
     */
    private void generateAndSaveKeys(File keyDir, File privateKeyFile, File publicKeyFile) throws Exception {
        // Create directory if it doesn't exist
        if (!keyDir.exists()) {
            keyDir.mkdirs();
        }

        // Generate key pair
        KeyPair keyPair = generateRsaKey();
        this.publicKey = (RSAPublicKey) keyPair.getPublic();
        this.privateKey = (RSAPrivateKey) keyPair.getPrivate();

        // Save private key
        String privateKeyPEM = "-----BEGIN PRIVATE KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(privateKey.getEncoded()) +
                "\n-----END PRIVATE KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(privateKeyFile)) {
            fos.write(privateKeyPEM.getBytes());
        }

        // Save public key
        String publicKeyPEM = "-----BEGIN PUBLIC KEY-----\n" +
                Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(publicKey.getEncoded()) +
                "\n-----END PUBLIC KEY-----\n";
        try (FileOutputStream fos = new FileOutputStream(publicKeyFile)) {
            fos.write(publicKeyPEM.getBytes());
        }

        logger.info("RSA keys generated and saved successfully");
    }

    /**
     * Generate RSA 2048-bit key pair
     */
    private KeyPair generateRsaKey() {
        KeyPair keyPair;
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            keyPair = keyPairGenerator.generateKeyPair();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to generate RSA key pair", ex);
        }
        return keyPair;
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }

    public String getKeyStorePath() {
        return keyStorePath;
    }
}
