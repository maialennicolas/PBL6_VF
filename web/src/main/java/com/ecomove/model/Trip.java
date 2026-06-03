package com.ecomove.model;

public record Trip(
        long id,
        String sessionId,
        String from,
        String to,
        String km,
        String co2,
        String mode,
        String duration,
        String date,
        String icon,
        String points,
        String status
) {}