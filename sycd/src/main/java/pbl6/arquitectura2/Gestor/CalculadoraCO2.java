package pbl6.arquitectura2.Gestor;

import pbl6.integracion.CsvTripResultUpdater;

import java.util.Locale;

/**
 * Arquitectura 2: recibe el resultado de transporte de la Arquitectura 1 y calcula/guarda
 * CO2 + puntos en la base de datos. El cálculo tiene en cuenta:
 * - kilómetros reales del viaje guardado por la web,
 * - modo detectado por SYCD,
 * - modelo de coche del usuario en usuarios_ecomove/coches_ecomove,
 * - carpool DRIVER/PASSENGER y número de personas.
 */
public class CalculadoraCO2 {

    public static class ResultadoCO2 {
        public final int userId;
        public final int empresaId;
        public final String sycdMode;
        public final String webMode;
        public final double distanciaKm;
        public final double co2Gramos;
        public final double co2AhorradoG;
        public final int puntos;
        public final boolean carpool;
        public final int personas;
        public final String lat;
        public final String lon;
        public final String timestamp;
        public final String sessionId;

        public ResultadoCO2(int userId,
                            int empresaId,
                            String sycdMode,
                            String webMode,
                            double distanciaKm,
                            double co2Gramos,
                            double co2AhorradoG,
                            int puntos,
                            boolean carpool,
                            int personas,
                            String lat,
                            String lon,
                            String timestamp,
                            String sessionId) {
            this.userId = userId;
            this.empresaId = empresaId;
            this.sycdMode = sycdMode;
            this.webMode = webMode;
            this.distanciaKm = distanciaKm;
            this.co2Gramos = co2Gramos;
            this.co2AhorradoG = co2AhorradoG;
            this.puntos = puntos;
            this.carpool = carpool;
            this.personas = personas;
            this.lat = lat;
            this.lon = lon;
            this.timestamp = timestamp;
            this.sessionId = sessionId;
        }

        public String toLainoa2() {
            return String.format(Locale.US,
                    "%d %d %s %.4f %.2f %.2f %s %s %s %s %s %d %s %d",
                    userId,
                    empresaId,
                    sycdMode,
                    distanciaKm,
                    co2Gramos,
                    co2AhorradoG,
                    safe(lat),
                    safe(lon),
                    safe(timestamp),
                    safe(sessionId),
                    safeToken(webMode),
                    puntos,
                    carpool,
                    personas);
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "[CO2-Arq2] user=%d empresa=%d modo=%s dist=%.2f km emitido=%.2f kg ahorrado=%.2f kg puntos=%d carpool=%s personas=%d session=%s",
                    userId,
                    empresaId,
                    webMode,
                    distanciaKm,
                    co2Gramos / 1000.0,
                    co2AhorradoG / 1000.0,
                    puntos,
                    carpool,
                    personas,
                    sessionId);
        }
    }

    /**
     * Formato recibido desde Arquitectura 1 ResultWorker:
     * userId empresaId clasificacion lat lon timestamp sessionId
     */
    public static ResultadoCO2 calcularYActualizar(String mensaje) {
        String[] p = mensaje.trim().split("\\s+");
        if (p.length < 7) {
            throw new IllegalArgumentException("Mensaje inválido de Arquitectura 1 (<7 tokens): " + mensaje);
        }

        int userId = Integer.parseInt(p[0]);
        int empresaId = Integer.parseInt(p[1]);
        String sycdMode = p[2];
        String lat = p[3];
        String lon = p[4];
        String timestamp = p[5];
        String sessionId = p[6];

        CsvTripResultUpdater.UpdateSummary summary = CsvTripResultUpdater.updateResultAndGet(
                userId,
                sessionId,
                sycdMode,
                lat,
                lon,
                timestamp);

        if (summary == null) {
            throw new IllegalStateException("No se ha podido actualizar el viaje para user=" + userId + " session=" + sessionId);
        }

        return new ResultadoCO2(
                userId,
                empresaId,
                sycdMode,
                summary.webMode(),
                summary.km(),
                summary.co2ConsumidoKg() * 1000.0,
                summary.co2AhorradoKg() * 1000.0,
                summary.puntos(),
                summary.carpool(),
                summary.personas(),
                lat,
                lon,
                timestamp,
                summary.sessionId());
    }

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static String safeToken(String value) {
        return safe(value).replaceAll("\\s+", "_");
    }
}
