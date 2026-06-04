package com.ecomove.model;

public record TrackingStatus(
        boolean active,
        String mode,
        String distance,
        String duration,
        String co2Saved,
        int points,
        String sessionId,
        int samples,
        String lastTimestamp
) {}