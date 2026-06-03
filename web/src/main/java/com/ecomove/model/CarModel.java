package com.ecomove.model;

public record CarModel(
        String modeloCocheID,
        String marca,
        String modelo,
        String tipo,
        double emisionesKgKm
) {}
