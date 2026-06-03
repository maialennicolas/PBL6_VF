package com.ecomove.model;

public record Rider(
        long id,
        String name,
        String distance,
        double rating,
        String trip,
        String time,
        boolean electric,
        String initials,
        String department
) {}
