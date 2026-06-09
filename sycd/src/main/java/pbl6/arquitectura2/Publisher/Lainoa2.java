package pbl6.arquitectura2.Publisher;

import com.rabbitmq.client.*;
import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Lainoa2 {

    // System.out eta System.err guztiak ordezkatzeko Loggerra definitu dugu
    private static final Logger LOGGER = Logger.getLogger(Lainoa2.class.getName());
    private final ConnectionFactory factory;

    public Lainoa2() throws Exception {
        this.factory = TLSConfig.crearFactory(); // ← TLS
    }

    public void suscribir() {
        // Try-with-resources egiturak automatikoki ixten ditu konexioa eta kanala
        try (Connection connection = factory.newConnection();
                Channel channel = connection.createChannel()) {

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);
            String cola = channel.queueDeclare().getQueue();
            channel.queueBind(cola, CO2StreamConfig.EXCHANGE_LAINOA2, "");
            channel.basicConsume(cola, true, new MiConsumer(channel));

            LOGGER.info("╔══════════════════════════════════════════════════════╗");
            LOGGER.info("║          LAINOA 2 — Google Cloud  [TLS aktibo]       ║");
            LOGGER.info("║          Esperando resultados CO2...                 ║");
            LOGGER.info("╚══════════════════════════════════════════════════════╝");

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    LOGGER.log(Level.SEVERE, "Hilo interrumpido inesperadamente", e);
                    // InterruptedException baten ostean, hariaren egoera berrezarri behar da
                    Thread.currentThread().interrupt();
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error en la suscripción de mensajes", e);
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void parar() {
        notifyAll();
    }

    public class MiConsumer extends DefaultConsumer {

        public MiConsumer(Channel channel) {
            super(channel);
        }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {

            // SimpleDateFormat ez denez Thread-Safe, metodoaren barruan instantziatu behar
            // da (L48-ko akatsa)
            SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

            String mensaje = new String(body, StandardCharsets.UTF_8);
            String[] p = mensaje.split(" ");
            if (p.length < 9) {
                LOGGER.log(Level.WARNING, "[Lainoa2] Mensaje inválido: {0}", mensaje);
                return;
            }

            int userId = Integer.parseInt(p[0]);
            int empresaId = Integer.parseInt(p[1]);
            String mota = p[2];
            double distanciaKm = Double.parseDouble(p[3]);
            double co2Gramos = Double.parseDouble(p[4]);
            double co2Ahorrado = Double.parseDouble(p[5]);
            String lat = p[6];
            String lon = p[7];
            long ts = Long.parseLong(p[8]);

            String fecha = sdf.format(new Date(ts));
            String co2KG = String.format("%.3f", co2Gramos / 1000.0);
            String ahorKG = String.format("%.3f", co2Ahorrado / 1000.0);

            // Inprimatze kontsola lerro bakoitza LOGGER.info eta String.format bidez
            // babestu da
            LOGGER.info("");
            LOGGER.info("┌─ [Lainoa2] Resultado CO2 recibido ─────────────────┐");
            LOGGER.info(String.format("│  userId=%-4d  empresa=%-4d  Fecha: %s", userId, empresaId, fecha));
            LOGGER.info(String.format("│  Garraio mota : %-12s", mota));
            LOGGER.info(String.format("│  Distancia    : %.2f km", distanciaKm));
            LOGGER.info(String.format("│  CO2 emitido  : %s kg  (%.1f g)", co2KG, co2Gramos));
            LOGGER.info(String.format("│  CO2 ahorrado : %s kg  vs coche medio europeo", ahorKG));
            LOGGER.info(String.format("│  Koord.       : lat=%s  lon=%s", lat, lon));
            LOGGER.info("└────────────────────────────────────────────────────┘");
            LOGGER.info(String.format("[Lainoa2][GCP] → Escribiendo en BigQuery: userId=%d mota=%s co2=%.1fg",
                    userId, mota, co2Gramos));
        }
    }

    public static void main(String[] args) {
        LOGGER.info("[Lainoa2] Servicio iniciado.");
        try {
            Lainoa2 lainoa2 = new Lainoa2();
            lainoa2.suscribir();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Lainoa2] Error TLS gertatu da", e);
        }
    }
}