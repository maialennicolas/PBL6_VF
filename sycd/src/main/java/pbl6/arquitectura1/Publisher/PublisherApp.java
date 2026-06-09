package pbl6.arquitectura1.Publisher;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

import pbl6.arquitectura1.Gestor.TrayectoSimulado;

/**
 * PublisherApp - Lee usuarios desde CSV y simula sensores publicando datos.
 *
 * CSV esperado:
 * userID,empresaID,nombre,apellidos,modeloCocheID,puebloCiudad
 *
 * Formato mensaje enviado a RabbitMQ:
 * userId empresaId lat lon velocidad metrosRecorridos terminado
 */
public class PublisherApp {

    static final String EXCHANGE_STREAM = "stream_garraioa";
    static final String ROUTING_TAREA = "tarea";

    static final int INTERVALO_MS = 300;

    // Solo lee los 10 primeros usuarios del CSV
    static final int MAX_USUARIOS_CSV = 10;

    // Tipos simulados para probar los workers
    static final String[] TIPOS_TRANSPORTE = {
            "BUS",
            "TREN",
            "COCHE",
            "PATIN"
    };

    ConnectionFactory factory;
    Channel channel;

    HiloPublicador hiloPublicador;

    String rutaCsv;

    public PublisherApp(String rutaCsv) {
        this.rutaCsv = rutaCsv;
        try {
            this.factory = pbl6.arquitectura1.Config.TLSConfig.crearFactory();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void iniciar() {
        try (Connection connection = factory.newConnection()) {
            channel = connection.createChannel();
            channel.exchangeDeclare(EXCHANGE_STREAM, "direct", true);

            hiloPublicador = new HiloPublicador();
            hiloPublicador.start();

            synchronized (this) {
                try {
                    wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            channel.close();

        } catch (IOException | TimeoutException e) {
            e.printStackTrace();
        }
    }

    public void parar() {
        if (hiloPublicador != null) {
            hiloPublicador.interrupt();
        }

        synchronized (this) {
            notify();
        }
    }

    public class HiloPublicador extends Thread {

        @Override
        public void run() {

            try {
                Random rnd = new Random();

                ArrayList<UsuarioCsv> usuarios = leerUsuariosDesdeCsv(rutaCsv);
                ArrayList<TrayectoUsuario> trayectos = new ArrayList<>();

                for (int i = 0; i < usuarios.size(); i++) {
                    UsuarioCsv usuario = usuarios.get(i);

                    // Asignamos tipos de transporte de forma rotativa:
                    // usuario 1 -> BUS, usuario 2 -> TREN, usuario 3 -> COCHE, usuario 4 -> PATIN...
                    String tipoTransporte = TIPOS_TRANSPORTE[i % TIPOS_TRANSPORTE.length];

                    TrayectoSimulado trayecto = new TrayectoSimulado(
                            usuario.userId,
                            usuario.empresaId,
                            tipoTransporte
                    );

                    trayectos.add(new TrayectoUsuario(usuario, trayecto, tipoTransporte));
                }

                System.out.println("\n[Publisher] Usuarios cargados desde CSV: " + usuarios.size());

                while (!isInterrupted() && !trayectos.isEmpty()) {

                    // Elegir trayecto aleatorio para que lleguen mezclados
                    int indice = rnd.nextInt(trayectos.size());

                    TrayectoUsuario trayectoUsuario = trayectos.get(indice);
                    UsuarioCsv usuario = trayectoUsuario.usuario;
                    TrayectoSimulado trayecto = trayectoUsuario.trayecto;
                    String tipoTransporte = trayectoUsuario.tipoTransporte;

                    String mensaje = trayecto.generarEvento();

                    channel.basicPublish(
                            EXCHANGE_STREAM,
                            ROUTING_TAREA,
                            null,
                            mensaje.getBytes(StandardCharsets.UTF_8)
                    );

                    System.out.println(
                            "\n========== EVENTO ==========" +
                                    "\nUser ID: " + usuario.userId +
                                    "\nNombre: " + usuario.nombre + " " + usuario.apellidos +
                                    "\nEmpresa ID: " + usuario.empresaId +
                                    "\nModelo coche ID: " + usuario.modeloCocheId +
                                    "\nPueblo/Ciudad: " + usuario.puebloCiudad +
                                    "\nTipo simulado: " + tipoTransporte +
                                    "\nLatitud: " + trayecto.getLatActual() +
                                    "\nLongitud: " + trayecto.getLonActual() +
                                    "\nVelocidad: " + String.format("%.2f", trayecto.getVelocidad()) + " km/h" +
                                    "\nMetros recorridos: " + String.format("%.2f", trayecto.getMetrosRecorridos()) + " m" +
                                    "\nTrayecto terminado: " + trayecto.haTerminado() +
                                    "\nMensaje enviado: " + mensaje +
                                    "\n============================"
                    );

                    if (trayecto.haTerminado()) {
                        System.out.println(
                                "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>" +
                                        "\nUSER " + usuario.userId + " - " +
                                        usuario.nombre + " " + usuario.apellidos +
                                        " HA LLEGADO A SU DESTINO" +
                                        "\nTipo simulado: " + tipoTransporte +
                                        "\n>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
                        );

                        trayectos.remove(indice);
                    }

                    Thread.sleep(INTERVALO_MS);
                }

                System.out.println("\nTODOS LOS USUARIOS HAN TERMINADO SUS TRAYECTOS");

            } catch (IOException e) {
                System.err.println("[Publisher] Error leyendo CSV o publicando mensaje.");
                System.err.println("[Publisher] Ruta CSV usada: " + rutaCsv);
                System.err.println("[Publisher] Directorio actual: " + System.getProperty("user.dir"));
                e.printStackTrace();

            } catch (InterruptedException e) {
                System.out.println("[Publisher] Hilo interrumpido.");
            }
        }
    }

    private ArrayList<UsuarioCsv> leerUsuariosDesdeCsv(String rutaCsv) throws IOException {
        ArrayList<UsuarioCsv> usuarios = new ArrayList<>();

        Path path = Path.of(rutaCsv);

        try (BufferedReader br = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {

            String linea;
            boolean primeraLinea = true;

            while ((linea = br.readLine()) != null) {

                linea = linea.trim();

                if (linea.isEmpty()) {
                    continue;
                }

                // Quitar BOM si el CSV lo trae
                if (primeraLinea) {
                    linea = linea.replace("\uFEFF", "");
                }

                // Saltar cabecera
                if (primeraLinea) {
                    primeraLinea = false;

                    String lineaMinuscula = linea.toLowerCase();

                    if (lineaMinuscula.startsWith("userid")
                            || lineaMinuscula.startsWith("user_id")) {
                        continue;
                    }
                }

                // Soporta CSV separado por coma o por punto y coma
                String separador = linea.contains(";") ? ";" : ",";
                String[] p = linea.split(separador, -1);

                if (p.length < 6) {
                    System.out.println("[Publisher] Línea CSV inválida: " + linea);
                    continue;
                }

                int userId = Integer.parseInt(p[0].trim());
                int empresaId = Integer.parseInt(p[1].trim());
                String nombre = p[2].trim();
                String apellidos = p[3].trim();
                int modeloCocheId = Integer.parseInt(p[4].trim());
                String puebloCiudad = p[5].trim();

                UsuarioCsv usuario = new UsuarioCsv(
                        userId,
                        empresaId,
                        nombre,
                        apellidos,
                        modeloCocheId,
                        puebloCiudad
                );

                usuarios.add(usuario);

                // Leer solo los 10 primeros usuarios
                if (usuarios.size() >= MAX_USUARIOS_CSV) {
                    break;
                }
            }
        }

        return usuarios;
    }

    static class UsuarioCsv {

        int userId;
        int empresaId;
        String nombre;
        String apellidos;
        int modeloCocheId;
        String puebloCiudad;

        UsuarioCsv(int userId,
                   int empresaId,
                   String nombre,
                   String apellidos,
                   int modeloCocheId,
                   String puebloCiudad) {

            this.userId = userId;
            this.empresaId = empresaId;
            this.nombre = nombre;
            this.apellidos = apellidos;
            this.modeloCocheId = modeloCocheId;
            this.puebloCiudad = puebloCiudad;
        }
    }

    static class TrayectoUsuario {

        UsuarioCsv usuario;
        TrayectoSimulado trayecto;
        String tipoTransporte;

        TrayectoUsuario(UsuarioCsv usuario,
                        TrayectoSimulado trayecto,
                        String tipoTransporte) {

            this.usuario = usuario;
            this.trayecto = trayecto;
            this.tipoTransporte = tipoTransporte;
        }
    }

    public static void main(String[] args) {

        String rutaCsv;

        if (args.length > 0) {
            rutaCsv = args[0];
        } else {
            // Cambia esta ruta si tienes el CSV en otro sitio
            rutaCsv = "sycd/usuarios_modelos_reales.csv";
        }

        System.out.println("Publicando trayectos aleatorios desde CSV...");
        System.out.println("CSV usado: " + rutaCsv);
        System.out.println("Directorio actual: " + System.getProperty("user.dir"));
        System.out.println("Pulsa ENTER para parar.");

        PublisherApp publisher = new PublisherApp(rutaCsv);

        new Thread(() -> {
            Scanner teclado = new Scanner(System.in);
            teclado.nextLine();
            publisher.parar();
            teclado.close();
        }).start();

        publisher.iniciar();
    }
}