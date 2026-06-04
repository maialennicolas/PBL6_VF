package com.ecomove.model;

public record User(
        long userID,
        long empresaID,
        String nombre,
        String apellidos,
        String nombreUsuario,
        String contrasena,
        String email,
        boolean tieneCoche,
        String modeloCocheID,
        String puebloCiudad
) {}
