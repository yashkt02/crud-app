package com.example.auth;

public final class AuthRequests {
    private AuthRequests() {
    }

    public record Credentials(String username, String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresIn, String username, String role) {
    }
}