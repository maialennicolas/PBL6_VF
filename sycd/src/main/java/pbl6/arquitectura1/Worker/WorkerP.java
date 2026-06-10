package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.util.Locale;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

public class WorkerP {

    ConnectionFactory factory;

    public WorkerP() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_PUBLIKO, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_PUBLIKO, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            // SEKUENTZIALA: basicQos(1) eta hari pool-ik gabe
            channel.basicQos(1);
            channel.basicConsume(KafkaStreamConfig.QUEUE_PUBLIKO, false, new MiConsumer(channel));

            System.out.println("[WorkerP - Sekuentziala] Esperando mensajes...");
            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            channel.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

    static String clasificar(double velocidad, double metros) {
        if (velocidad >= 30.0) return null;
        if (velocidad < 6.0)  return "OINEZ";
        if (velocidad < 15.0) return "KORRIKA";
        return metros < 5.0 ? "TXIRRINA" : "PATINETE";
    }

    public class MiConsumer extends DefaultConsumer {
        public MiConsumer(Channel channel) { super(channel); }

        @Override
        public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
            String mensaje = new String(body, "UTF-8");
            try {
                procesarMensaje(mensaje);
                synchronized (getChannel()) {
                    getChannel().basicAck(envelope.getDeliveryTag(), false);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void procesarMensaje(String mensaje) throws IOException, TimeoutException {
        String[] p = mensaje.split(" ");
        if (p.length < 8) return;

        int    userId    = Integer.parseInt(p[0]);
        int    empresaId = Integer.parseInt(p[1]);
        double metros    = Double.parseDouble(p[2]);
        double velocidad = Double.parseDouble(p[3]);
        String lat       = p[5];
        String lon       = p[6];
        String timestamp = p[7];

        String clasificacion = clasificar(velocidad, metros);

        if (clasificacion != null) {
            String resultado = userId + " " + empresaId + " " + clasificacion + " " + lat + " " + lon + " " + timestamp;
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println(String.format(Locale.US,
                    "[%s] [WorkerP - Estrategia S] USER %d → %s (dist=%.0fm, velocidad=%.2f)",
                    horaActual, userId, clasificacion, metros, velocidad));

            
        }
    }

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        System.out.println("Pulsa ENTER para parar.");
        WorkerP worker = new WorkerP();
        new Thread(() -> { teclado.nextLine(); worker.parar(); teclado.close(); }).start();
        worker.suscribir();
    }
}
