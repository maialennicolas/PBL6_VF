package com.ecomove.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterRequest(
        @NotNull(message = "La empresa es obligatoria")
        Long empresaID,

        @NotBlank(message = "El nombre es obligatorio")
        String nombre,

        @NotBlank(message = "Los apellidos son obligatorios")
        String apellidos,

        @NotBlank(message = "El nombre de usuario es obligatorio")
        String nombreUsuario,

        @NotBlank(message = "La contraseña es obligatoria")
        String contrasena,

        String email,

        boolean tieneCoche,

        String modeloCocheID,

        @NotBlank(message = "El pueblo o ciudad es obligatorio")
        String puebloCiudad
) {}
