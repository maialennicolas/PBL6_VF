package pbl6.arquitectura1.Worker;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import pbl6.arquitectura1.Gestor.ParadasLoader;
import pbl6.arquitectura1.Publisher.KafkaStreamConfig;

public class WorkerKP {

    ConnectionFactory factory;
    ParadasLoader paradasLoader;

    public WorkerKP() {
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
        paradasLoader = new ParadasLoader();
    }

    public void suscribir() {
        try (Connection connection = factory.newConnection()) {
            Channel channel = connection.createChannel();

            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_FANOUT, "fanout", true);
            channel.exchangeDeclare(KafkaStreamConfig.EXCHANGE_EMAITZA, "direct", true);

            channel.queueDeclare(KafkaStreamConfig.QUEUE_KP, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_KP, KafkaStreamConfig.EXCHANGE_FANOUT, "");

            channel.queueDeclare(KafkaStreamConfig.QUEUE_EMAITZA, true, false, false, null);
            channel.queueBind(KafkaStreamConfig.QUEUE_EMAITZA, KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA);

            // SEKUENTZIALA: basicQos(1) eta hari pool-ik gabe
            channel.basicQos(1);
            channel.basicConsume(KafkaStreamConfig.QUEUE_KP, false, new MiConsumer(channel));

            System.out.println("[WorkerKP - Sekuentziala] Esperando mensajes...");
            synchronized (this) {
                try { wait(); } catch (InterruptedException e) { e.printStackTrace(); }
            }
            channel.close();
        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public synchronized void parar() { notify(); }

    String clasificar(double velocidad, double lat, double lon) {
        if (!paradasLoader.cercaDeParada(lat, lon)) return null;
        if (velocidad >= 15.0 && velocidad <= 60.0) return "BUS";
        if (velocidad > 60.0 && velocidad <= 200.0) return "TREN";
        return null;
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
        double velocidad = Double.parseDouble(p[3]);
        double lat       = Double.parseDouble(p[5]);
        double lon       = Double.parseDouble(p[6]);
        String timestamp = p[7];

        String clasificacion = clasificar(velocidad, lat, lon);

        if (clasificacion != null) {
            String resultado = userId + " " + empresaId + " " + clasificacion + " " + lat + " " + lon + " " + timestamp;
            synchronized (factory) {
                try (Connection conn = factory.newConnection(); Channel ch = conn.createChannel()) {
                    ch.basicPublish(KafkaStreamConfig.EXCHANGE_EMAITZA, KafkaStreamConfig.QUEUE_EMAITZA, null, resultado.getBytes());
                }
            }
            String horaActual = new java.text.SimpleDateFormat("HH:mm:ss.SSS").format(new java.util.Date());
            System.out.println("[" + horaActual + "] [WorkerKP] USER " + userId + " → " + clasificacion);
        }
    }

    public static void main(String[] args) {
        Scanner teclado = new Scanner(System.in);
        System.out.println("Pulsa ENTER para parar.");
        WorkerKP worker = new WorkerKP();
        new Thread(() -> { teclado.nextLine(); worker.parar(); teclado.close(); }).start();
        worker.suscribir();
    }
}
