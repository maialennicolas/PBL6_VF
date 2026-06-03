package com.ecomove.model;

import java.util.List;

public record RouteRecommendation(
        String from,
        String to,
        String duration,
        String distance,
        String co2,
        List<RouteStep> steps
) {}
