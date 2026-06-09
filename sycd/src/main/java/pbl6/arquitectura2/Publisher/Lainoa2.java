package pbl6.arquitectura2.Publisher;


import com.rabbitmq.client.*;

import pbl6.arquitectura2.Config.CO2StreamConfig;
import pbl6.arquitectura2.Config.TLSConfig;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class Lainoa2 {

    private final ConnectionFactory factory;

    public Lainoa2() throws Exception {
        this.factory = TLSConfig.crearFactory();   // ← TLS
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(CO2StreamConfig.EXCHANGE_LAINOA2, "fanout", true);
            String cola = channel.queueDeclare().getQueue();
            channel.queueBind(cola, CO2StreamConfig.EXCHANGE_LAINOA2, "");
            channel.basicConsume(cola, true, new MiConsumer(channel));

            System.out.println("╔══════════════════════════════════════════════════════╗");
            System.out.println("║          LAINOA 2 — Google Cloud  [TLS aktibo]       ║");
            System.out.println("║          Esperando resultados CO2...                 ║");
            System.out.println("╚══════════════════════════════════════════════════════╝");

            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            channel.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public synchronized void parar() { notify(); }

    public class MiConsumer extends DefaultConsumer {
        private static final SimpleDateFormat SDF = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope,
                AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, "UTF-8");
            String[] p = mensaje.split(" ");
            if (p.length < 9) { System.out.println("[Lainoa2] Mensaje inválido: " + mensaje); return; }

            int    userId      = Integer.parseInt(p[0]);
            int    empresaId   = Integer.parseInt(p[1]);
            String mota        = p[2];
            double distanciaKm = Double.parseDouble(p[3]);
            double co2Gramos   = Double.parseDouble(p[4]);
            double co2Ahorrado = Double.parseDouble(p[5]);
            String lat = p[6], lon = p[7];
            long   ts  = Long.parseLong(p[8]);

            String fecha  = SDF.format(new Date(ts));
            String co2KG  = String.format("%.3f", co2Gramos / 1000.0);
            String ahorKG = String.format("%.3f", co2Ahorrado / 1000.0);

            System.out.println();
            System.out.println("┌─ [Lainoa2] Resultado CO2 recibido ─────────────────┐");
            System.out.printf ("│  userId=%-4d  empresa=%-4d  Fecha: %s%n", userId, empresaId, fecha);
            System.out.printf ("│  Garraio mota : %-12s%n", mota);
            System.out.printf ("│  Distancia    : %.2f km%n", distanciaKm);
            System.out.printf ("│  CO2 emitido  : %s kg  (%.1f g)%n", co2KG, co2Gramos);
            System.out.printf ("│  CO2 ahorrado : %s kg  vs coche medio europeo%n", ahorKG);
            System.out.printf ("│  Koord.       : lat=%s  lon=%s%n", lat, lon);
            System.out.println("└────────────────────────────────────────────────────┘");
            System.out.printf("[Lainoa2][GCP] → Escribiendo en BigQuery: userId=%d mota=%s co2=%.1fg%n",
                    userId, mota, co2Gramos);
        }
    }

    public static void main(String[] args) {
        System.out.println("[Lainoa2] Servicio iniciado. Pulsa ENTER para parar.");
        try {
            Lainoa2 lainoa2 = new Lainoa2();
            Scanner teclado = new Scanner(System.in);
            new Thread(() -> { teclado.nextLine(); lainoa2.parar(); }).start();
            lainoa2.suscribir();
            teclado.close();
        } catch (Exception e) {
            System.err.println("[Lainoa2] Error TLS: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
