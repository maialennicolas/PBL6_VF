// ─── ParadasLoader.java ─────────────────────────────────────────────────────
package pbl6.arquitectura1.Gestor;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Carga las paradas de bus/tren desde los ficheros stops.txt (GTFS).
 * Los ficheros deben estar en src/main/resources/paradas/
 *
 * Columnas esperadas (bizkaibus / ekialdebus):
 *   stop_id, stop_code, stop_name, [stop_desc,] stop_lat, stop_lon, ...
 *
 * Se detecta la posición de stop_lat/stop_lon leyendo la cabecera.
 */
public class ParadasLoader {

    public static class Parada {
        public final double lat;
        public final double lon;
        public final String nombre;

        public Parada(double lat, double lon, String nombre) {
            this.lat    = lat;
            this.lon    = lon;
            this.nombre = nombre;
        }
    }

    private static final String[] FICHEROS = {
        "/paradas/bizkaibus_stops.txt",
        "/paradas/ekialdebus_stops.txt",
        "/paradas/euskotren_stops.txt"
    };

    /** Radio en metros para considerar que el usuario está "en una parada" */
    public static final double RADIO_METROS = 50.0;

    private final List<Parada> paradas = new ArrayList<>();

    public ParadasLoader() {
        for (String ruta : FICHEROS) {
            cargar(ruta);
        }
        System.out.println("[ParadasLoader] Total paradas cargadas: " + paradas.size());
    }

    private void cargar(String ruta) {
        try (InputStream is = getClass().getResourceAsStream(ruta)) {
            if (is == null) {
                System.err.println("[ParadasLoader] No encontrado: " + ruta);
                return;
            }
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String cabecera = br.readLine();
            if (cabecera == null) return;

            String[] cols = cabecera.split(",");
            int idxLat = -1, idxLon = -1, idxNombre = -1;
            for (int i = 0; i < cols.length; i++) {
                String c = cols[i].trim().replace("\"", "");
                if (c.equals("stop_lat"))  idxLat    = i;
                if (c.equals("stop_lon"))  idxLon    = i;
                if (c.equals("stop_name")) idxNombre = i;
            }
            if (idxLat == -1 || idxLon == -1) {
                System.err.println("[ParadasLoader] Columnas lat/lon no encontradas en: " + ruta);
                return;
            }

            String linea;
            int cargadas = 0;
            while ((linea = br.readLine()) != null) {
                // Manejo básico de campos entre comillas con comas internas
                String[] p = parsearCSV(linea);
                if (p.length <= Math.max(idxLat, idxLon)) continue;
                try {
                    double lat    = Double.parseDouble(p[idxLat].trim());
                    double lon    = Double.parseDouble(p[idxLon].trim());
                    String nombre = (idxNombre >= 0 && idxNombre < p.length)
                                    ? p[idxNombre].trim().replace("\"", "")
                                    : "?";
                    paradas.add(new Parada(lat, lon, nombre));
                    cargadas++;
                } catch (NumberFormatException ignored) { }
            }
            System.out.println("[ParadasLoader] " + ruta + " → " + cargadas + " paradas");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Devuelve true si (lat, lon) está a menos de RADIO_METROS de alguna parada */
    public boolean cercaDeParada(double lat, double lon) {
        for (Parada p : paradas) {
            if (distanciaMetros(lat, lon, p.lat, p.lon) <= RADIO_METROS) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fórmula de Haversine — distancia en metros entre dos coordenadas.
     */
    private static double distanciaMetros(double lat1, double lon1,
                                          double lat2, double lon2) {
        final double R = 6_371_000.0; // radio Tierra en metros
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    /** Parser CSV mínimo que respeta campos entre comillas con comas internas */
    private static String[] parsearCSV(String linea) {
        List<String> campos = new ArrayList<>();
        StringBuilder sb    = new StringBuilder();
        boolean enComillas  = false;
        for (char c : linea.toCharArray()) {
            if (c == '"') {
                enComillas = !enComillas;
            } else if (c == ',' && !enComillas) {
                campos.add(sb.toString());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        campos.add(sb.toString());
        return campos.toArray(new String[0]);
    }
}