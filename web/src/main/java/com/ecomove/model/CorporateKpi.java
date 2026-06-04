package com.ecomove.model;

public record CorporateKpi(
        String label,
        String value,
        String delta,
        String icon,
        String color
) {}
