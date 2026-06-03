package com.ecomove.model;

public record LocationTrackRequest(
        long userId,
        String sessionId,
        double latitude,
        double longitude,
        Double accuracy,
        Double speed,
        Double heading,
        Double altitude,
        String timestamp
) {}