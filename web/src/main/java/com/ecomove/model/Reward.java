package com.ecomove.model;

public record Reward(
        long id,
        String title,
        int points,
        String emoji,
        String category
) {}
