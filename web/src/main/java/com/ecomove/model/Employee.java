package com.ecomove.model;

public record Employee(
        int rank,
        String name,
        String initials,
        String department,
        int trips,
        String co2Saved,
        int points
) {}
