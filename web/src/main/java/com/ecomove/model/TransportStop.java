package com.ecomove.model;

public record TransportStop(
        String paradaID,
        String proveedor,
        String stopID,
        String stopCode,
        String nombre,
        String descripcion,
        double latitud,
        double longitud,
        String zona,
        String municipio,
        String locationType,
        String accesible
) {}
