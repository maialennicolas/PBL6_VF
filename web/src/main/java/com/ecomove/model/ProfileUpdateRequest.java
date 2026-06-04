package com.ecomove.model;

public record ProfileUpdateRequest(
        long userId,
        Long empresaID,
        String nombre,
        String apellidos,
        String email,
        Boolean tieneCoche,
        String modeloCocheID,
        String puebloCiudad
) {}