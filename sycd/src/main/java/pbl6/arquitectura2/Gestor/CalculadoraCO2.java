package pbl6.arquitectura2.Gestor;


import java.util.Locale;

public class CalculadoraCO2 {

    public static class ResultadoCO2 {
        public final int    userId;
        public final int    empresaId;
        public final String garraioaMota;
        public final double distanciaKm;
        public final double co2Gramos;
        public final double co2AhorradoG;
        public final String marca;
        public final String modelo;
        public final String lat;
        public final String lon;
        public final String timestamp;

        public ResultadoCO2(int userId, int empresaId, String garraioaMota,
                            double distanciaKm, double co2Gramos, double co2AhorradoG,
                            String marca, String modelo,
                            String lat, String lon, String timestamp) {
            this.userId=userId; this.empresaId=empresaId; this.garraioaMota=garraioaMota;
            this.distanciaKm=distanciaKm; this.co2Gramos=co2Gramos; this.co2AhorradoG=co2AhorradoG;
            this.marca=marca; this.modelo=modelo; this.lat=lat; this.lon=lon; this.timestamp=timestamp;
        }

        public String toLainoa2() {
            return String.format(Locale.US, "%d %d %s %.3f %.2f %.2f %s %s %s",
                    userId, empresaId, garraioaMota, distanciaKm, co2Gramos, co2AhorradoG,
                    lat, lon, timestamp);
        }

        @Override
        public String toString() {
            return String.format(Locale.US,
                    "[CO2] userId=%-4d empresa=%-4d %-10s dist=%.2f km  CO2=%.1f g  ahorro=%.1f g  (%s %s)",
                    userId, empresaId, garraioaMota, distanciaKm, co2Gramos, co2AhorradoG, marca, modelo);
        }
    }

    public static ResultadoCO2 calcular(String mensaje) {
        String[] p = mensaje.split(" ");
        if (p.length < 9) throw new IllegalArgumentException("Mensaje inválido (< 9 tokens): " + mensaje);

        int    userId      = Integer.parseInt(p[0]);
        int    empresaId   = Integer.parseInt(p[1]);
        String mota        = p[2].toUpperCase();
        double distanciaKm = Double.parseDouble(p[3].replace(',', '.'));
        String marca       = p[4];
        String modelo      = p[5];
        String lat         = p[6];
        String lon         = p[7];
        String timestamp   = p[8];

        double co2Gramos;
        double co2Referencia = EEATabla.CO2_DEFAULT_G_KM * distanciaKm;

        switch (mota) {
            case "KOTXEA": co2Gramos = EEATabla.obtenerFactor(marca, modelo) * distanciaKm; break;
            case "BUS":    co2Gramos = EEATabla.CO2_BUS_G_KM  * distanciaKm; break;
            case "TREN":   co2Gramos = EEATabla.CO2_TREN_G_KM * distanciaKm; break;
            default:       co2Gramos = 0.0; break;
        }

        double co2Ahorrado = Math.max(0.0, co2Referencia - co2Gramos);
        return new ResultadoCO2(userId, empresaId, mota, distanciaKm, co2Gramos, co2Ahorrado,
                marca, modelo, lat, lon, timestamp);
    }
}
