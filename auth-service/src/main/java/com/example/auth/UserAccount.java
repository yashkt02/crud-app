package com.example.auth;

public record UserAccount(String username, String passwordHash, String role) {
}