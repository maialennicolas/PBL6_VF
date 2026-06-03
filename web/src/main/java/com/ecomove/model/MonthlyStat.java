package com.ecomove.model;

public record MonthlyStat(
        String month,
        double co2,
        int km
) {}
