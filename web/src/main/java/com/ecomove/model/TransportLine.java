package com.ecomove.model;

public record TransportLine(
        String id,
        String name,
        String color,
        int minutes,
        String status,
        int stops
) {}
