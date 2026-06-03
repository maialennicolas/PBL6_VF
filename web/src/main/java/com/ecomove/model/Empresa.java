package com.ecomove.model;

public record Empresa(
        long empresaID,
        String nombre,
        String ciudad,
        String descripcion
) {}
