package com.ecomove.model;

public record UserProfile(
        long id,
        String name,
        String initials,
        String email,
        String organization,
        String department,
        int level,
        int points,
        int trips,
        String co2Saved,
        String badge,
        long empresaID,
        String nombreUsuario,
        boolean tieneCoche,
        String modeloCocheID,
        String puebloCiudad
) {}
