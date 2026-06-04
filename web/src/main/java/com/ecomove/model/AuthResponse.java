package com.ecomove.model;

public record AuthResponse(
        boolean ok,
        String message,
        UserProfile user
) {}
