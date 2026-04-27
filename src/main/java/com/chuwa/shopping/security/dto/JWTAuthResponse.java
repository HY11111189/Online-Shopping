package com.chuwa.shopping.security.dto;

/**
 * @author b1go
 * @date 7/1/22 1:08 AM
 */
public class JWTAuthResponse {

    private String accessToken;
    private String tokenType;
    private String username;
    private long expiresIn;

    public JWTAuthResponse() {
    }

    public JWTAuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }
}
