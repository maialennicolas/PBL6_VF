package pbl6.arquitectura2.Publisher;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class PublisherCO2 {

    static final int INTERVALO_MS = 500;

    private final ConnectionFactory factory;
    private final String rutaCsv;
    private final boolean modoSimulacion;

    public PublisherCO2(String rutaCsv) throws Exception {
        this.rutaCsv = rutaCsv;
        this.modoSimulacion = (rutaCsv == null || rutaCsv.isBlank());
        this.factory = TLSConfig.crearFactory();   // ← TLS
    }

    public void publicar() {
        try (Connection connection = factory.newConnection();
             Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_CO2_FANOUT, "fanout", true);

            List<String> mensajes = modoSimulacion
                    ? generarDatosSimulados()
                    : leerDesdeCSV(rutaCsv);

            System.out.println("[PublisherCO2] Enviando " + mensajes.size() + " ibilbideak... [TLS aktibo]");
            System.out.println("──────────────────────────────────────────────────────");

            for (String msg : mensajes) {
                channel.basicPublish(
                        CO2StreamConfig.EXCHANGE_CO2_FANOUT, "",
                        null, msg.getBytes(StandardCharsets.UTF_8));
                String[] p = msg.split(" ");
                System.out.printf(Locale.US, "[PublisherCO2] → userId=%-4s %-10s dist=%.2f km  %s %s%n",
                        p[0], p[2], Double.parseDouble(p[3].replace(',', '.')), p[4], p[5]);
                Thread.sleep(INTERVALO_MS);
            }
            System.out.println("[PublisherCO2] ✔ Todos los ibilbideak enviados.");

        } catch (Exception e) {
            e.printStackTrace();
            Thread.currentThread().interrupt();
        }
        
    }

    private List<String> leerDesdeCSV(String ruta) throws IOException {
        List<String> mensajes = new ArrayList<>();
        try (BufferedReader br = Files.newBufferedReader(Path.of(ruta), StandardCharsets.UTF_8)) {
            String linea; boolean primera = true;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim().replace("\uFEFF", "");
                if (linea.isEmpty()) continue;
                if (primera) { primera = false;
                    if (linea.toLowerCase().startsWith("userid") || linea.toLowerCase().startsWith("user")) continue; }
                String sep = linea.contains(";") ? ";" : ",";
                String[] p = linea.split(sep, -1);
                if (p.length < 9) continue;
                double distRaw = Double.parseDouble(p[3].trim());
                double distKm  = distRaw > 10.0 ? distRaw / 1000.0 : distRaw;
                mensajes.add(p[0].trim() + " " + p[1].trim() + " " + p[2].trim().toUpperCase()
                        + " " + String.format("%.4f", distKm)
                        + " " + p[4].trim() + " " + p[5].trim()
                        + " " + p[6].trim() + " " + p[7].trim() + " " + p[8].trim());
            }
        }
        return mensajes;
    }

    static List<String> generarDatosSimulados() {
        long now = System.currentTimeMillis();
        List<String> mensajes = new ArrayList<>();
        Object[][] datos = {
            {1,  1, "BUS",      5.2,  "-",          "-",           "43.318918", "-1.917541"},
            {2,  1, "TREN",    12.8,  "-",          "-",           "43.317747", "-1.977152"},
            {3,  2, "KOTXEA",   8.5,  "VOLKSWAGEN", "GOLF",        "43.062000", "-2.490500"},
            {4,  2, "PATINETE", 2.3,  "-",          "-",           "43.061500", "-2.491000"},
            {5,  1, "BUS",      6.7,  "-",          "-",           "43.320000", "-1.960000"},
            {6,  3, "TREN",    25.1,  "-",          "-",           "43.330131", "-1.848650"},
            {7,  3, "KOTXEA",  15.3,  "SEAT",       "LEON",        "43.063000", "-2.492000"},
            {8,  2, "OINEZ",    0.8,  "-",          "-",           "43.060800", "-2.490200"},
            {9,  1, "KOTXEA",  10.2,  "TOYOTA",     "COROLLA",     "43.061200", "-2.490800"},
            {10, 3, "KORRIKA",  3.1,  "-",          "-",           "43.062500", "-2.491500"},
        };
        for (Object[] d : datos) {
            mensajes.add(d[0] + " " + d[1] + " " + d[2] + " "
                    + String.format(Locale.US, "%.4f", d[3]) + " "
                    + d[4] + " " + d[5] + " " + d[6] + " " + d[7] + " "
                    + (now - (long)(Math.random() * 3_600_000L)));
        }
        return mensajes;
    }

    public static void main(String[] args) {
        String rutaCsv = args.length > 0 ? args[0] : null;
        System.out.println("[PublisherCO2] Modo: " + (rutaCsv == null ? "SIMULACIÓN" : "CSV: " + rutaCsv));
        try {
            PublisherCO2 publisher = new PublisherCO2(rutaCsv);
            Scanner teclado = new Scanner(System.in);
            Thread hilo = new Thread(publisher::publicar);
            hilo.start();
            teclado.nextLine();
            hilo.interrupt();
            teclado.close();
        } catch (Exception e) {
            System.err.println("[PublisherCO2] Error TLS: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
