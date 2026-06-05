package com.ecomove.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

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
        @Size(min = 12, message = "La contraseña debe tener al menos 12 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$",
                message = "La contraseña debe incluir mayúscula, minúscula, número y carácter especial"
        )
        String contrasena,

        String email,

        boolean tieneCoche,

        String modeloCocheID,

        @NotBlank(message = "El pueblo o ciudad es obligatorio")
        String puebloCiudad
) {}
