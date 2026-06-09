package pbl6.arquitectura1.Gestor;

import java.util.Locale;
import java.util.Random;

public class TrayectoSimulado {

    private int userId;
    private int empresaId;
    private double velocidadConstante;
    private double velocidadActual;
    private String tipoTransporte;

    // Posición actual
    private double latActual;
    private double lonActual;

    // Destino
    private double latDestino;
    private double lonDestino;

    // Metros recorridos
    private double metrosRecorridos;

    // Trayecto terminado
    private boolean terminado;

    private Random rnd;

    /**
     * Constructor modificado para forzar comportamientos por defecto.
     *
     * @param userId ID del usuario
     * @param empresaId ID de la empresa
     * @param tipoTransporte "BUS", "TREN", "COCHE" o "PATIN"
     */
    public TrayectoSimulado(int userId, int empresaId, String tipoTransporte) {
        this.userId = userId;
        this.empresaId = empresaId;
        this.tipoTransporte = tipoTransporte.toUpperCase();
        this.rnd = new Random();
        this.metrosRecorridos = 0;
        this.terminado = false;

        configurarTrayectoSegunTransporte();

        this.velocidadActual = this.velocidadConstante;
    }

    private void configurarTrayectoSegunTransporte() {
        switch (tipoTransporte) {
            case "BUS":
                // Forzamos coordenadas reales del archivo stops.txt para activar el validador de paradas
                // Inicio: Pasaia - Pasai Antxo (renfe)
                latActual = 43.318918;
                lonActual = -1.917541;

                // Destino: Cerca de Donostia - Zurriola, Kursaal
                latDestino = 43.324713;
                lonDestino = -1.976825;

                // Rango para BUS: 15 - 60 km/h
                velocidadConstante = 35.0;
                break;

            case "TREN":
                // Inicio: Irun - Gaintxurizketa Gaina
                latActual = 43.330131;
                lonActual = -1.848650;

                // Destino: Cerca de Donostia - Renfe Geltokia
                latDestino = 43.317747;
                lonDestino = -1.977152;

                // Rango para TREN: 60 - 200 km/h
                velocidadConstante = 90.0;
                break;

            case "COCHE":
                // Coordenadas genéricas, zona Mondragón
                latActual = 43.060 + rnd.nextDouble() * 0.01;
                lonActual = -2.490 + rnd.nextDouble() * 0.01;

                // Destino lo suficientemente lejos para acumular metros rápidamente
                latDestino = latActual + 0.015;
                lonDestino = lonActual + 0.015;

                // Rango para COCHE
                velocidadConstante = 75.0;
                break;

            case "PATIN":
            default:
                latActual = 43.060 + rnd.nextDouble() * 0.005;
                lonActual = -2.490 + rnd.nextDouble() * 0.005;

                latDestino = latActual + 0.002;
                lonDestino = lonActual + 0.002;

                // Rango para PATINETE
                velocidadConstante = 22.0;
                break;
        }
    }

    public boolean haTerminado() {
        return terminado;
    }

    public String generarEvento() {
        // Movimiento progresivo hacia el destino
        double pasoLat = (latDestino - latActual) * 0.1;
        double pasoLon = (lonDestino - lonActual) * 0.1;

        latActual += pasoLat;
        lonActual += pasoLon;

        // Cálculo de distancia métrica recorrida en el paso
        double metrosPaso = Math.sqrt(
                Math.pow(pasoLat * 111000, 2) +
                        Math.pow(pasoLon * 85000, 2));

        // Forzar un incremento mínimo inicial para asegurar metros > 5.0
        if (metrosRecorridos == 0 && metrosPaso < 6.0) {
            metrosRecorridos += 6.5;
        }

        metrosRecorridos += metrosPaso;

        // Pequeña fluctuación en la velocidad
        double velocidadSimulada = velocidadConstante + (rnd.nextDouble() - 0.5) * 4.0;

        // Asegurar que las fluctuaciones no rompan los filtros del worker asignado
        if (tipoTransporte.equals("BUS") && velocidadSimulada > 60.0) {
            velocidadSimulada = 55.0;
        }

        if (tipoTransporte.equals("TREN") && velocidadSimulada <= 60.0) {
            velocidadSimulada = 65.0;
        }

        if (tipoTransporte.equals("COCHE") && velocidadSimulada <= 30.0) {
            velocidadSimulada = 35.0;
        }

        if (tipoTransporte.equals("PATIN") && velocidadSimulada >= 30.0) {
            velocidadSimulada = 28.0;
        }

        this.velocidadActual = velocidadSimulada;

        // Comprobación de llegada al destino
        double distanciaRestante = Math.sqrt(
                Math.pow(latDestino - latActual, 2) +
                        Math.pow(lonDestino - lonActual, 2));

        if (distanciaRestante < 0.0002) {
            terminado = true;
        }

        // Formato esperado por TaskWorker:
        // userId empresaId latActual lonActual velocidadSimulada metrosRecorridos terminado
        return String.format(
                Locale.US,
                "%d %d %.6f %.6f %.2f %.2f %b",
                userId,
                empresaId,
                latActual,
                lonActual,
                velocidadActual,
                metrosRecorridos,
                terminado);
    }

    public int getUserId() {
        return userId;
    }

    public int getEmpresaId() {
        return empresaId;
    }

    public double getLatActual() {
        return latActual;
    }

    public double getLonActual() {
        return lonActual;
    }

    public double getMetrosRecorridos() {
        return metrosRecorridos;
    }

    public double getVelocidad() {
        return velocidadActual;
    }
}