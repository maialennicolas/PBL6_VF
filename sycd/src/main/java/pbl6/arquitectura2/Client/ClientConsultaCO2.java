package pbl6.arquitectura2.Client;

import com.rabbitmq.client.*;
import pbl6.arquitectura2.Config.TLSConfig;

import java.util.Scanner;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ClientConsultaCO2 {

    private static final String COLA_PETICIONES = "q.co2.consultas";
    private final Connection connection;
    private final Channel channel;

    public ClientConsultaCO2() throws Exception {
        ConnectionFactory factory = TLSConfig.crearFactory();
        this.connection = factory.newConnection();
        this.channel = connection.createChannel();
    }

    public String enviarPeticion(String tipoConsulta, String idParam) throws Exception {
        final String corrId = UUID.randomUUID().toString();
        String replyQueueName = channel.queueDeclare().getQueue();

        AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
                .correlationId(corrId).replyTo(replyQueueName).build();

        channel.basicPublish("", COLA_PETICIONES, props,
                (tipoConsulta + " " + idParam).getBytes("UTF-8"));

        final CompletableFuture<String> response = new CompletableFuture<>();
        String ctag = channel.basicConsume(replyQueueName, true, (consumerTag, delivery) -> {
            if (delivery.getProperties().getCorrelationId().equals(corrId)) {
                response.complete(new String(delivery.getBody(), "UTF-8"));
            }
        }, consumerTag -> {});

        try {
            String resultado = response.get(10, TimeUnit.SECONDS);
            channel.basicCancel(ctag);
            return resultado;
        } catch (TimeoutException e) {
            channel.basicCancel(ctag);
            return "⚠ Sin respuesta (timeout 10s). ¿Está arrancado ResultWorkerCO2?";
        }
    }

    public void cerrar() throws Exception {
        if (channel != null && channel.isOpen()) channel.close();
        if (connection != null && connection.isOpen()) connection.close();
    }

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== CLIENTE DE CONSULTAS CO2 [TLS aktibo] ===");
        try {
            ClientConsultaCO2 cliente = new ClientConsultaCO2();
            boolean seguir = true;
            while (seguir) {
                System.out.println("\n1) Consultar usuario  2) Consultar empresa  3) Salir");
                System.out.print("> ");
                String opcion = sc.nextLine().trim();
                switch (opcion) {
                    case "1":
                        System.out.print("ID usuario: ");
                        System.out.println("\n[RESULTADO]\n" + cliente.enviarPeticion("USER", sc.nextLine().trim()));
                        break;
                    case "2":
                        System.out.println("a) Datos empresa  b) Usuario en empresa");
                        System.out.print("  > ");
                        String sub = sc.nextLine().trim().toLowerCase();
                        if (sub.equals("a")) {
                            System.out.print("ID empresa: ");
                            System.out.println("\n[RESULTADO]\n" + cliente.enviarPeticion("EMPRESA", sc.nextLine().trim()));
                        } else if (sub.equals("b")) {
                            System.out.print("ID empresa: "); String e = sc.nextLine().trim();
                            System.out.print("ID usuario: "); String u = sc.nextLine().trim();
                            System.out.println("\n[RESULTADO]\n" + cliente.enviarPeticion("EMPRESA_USER", e + ":" + u));
                        }
                        break;
                    case "3":
                        seguir = false;
                        cliente.cerrar();
                        System.out.println("Cliente cerrado de forma segura.");
                        break;
                    default:
                        System.out.println("Opción incorrecta.");
                }
            }
        } catch (Exception e) {
            System.err.println("Error en el cliente: " + e.getMessage());
            e.printStackTrace();
        } finally {
            sc.close();
        }
    }
}