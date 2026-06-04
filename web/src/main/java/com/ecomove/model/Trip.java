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
        String status,
        String tripTypeIcon,
        boolean carpool,
        String carpoolId,
        int passengers,
        String carpoolRole,
        String co2Consumed,
        String co2Saved
) {}