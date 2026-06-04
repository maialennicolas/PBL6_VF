package pbl6.arquitectura1.Gestor;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class Medidor {

    private final String nombre;
    private final AtomicInteger mensajes    = new AtomicInteger(0);
    private final AtomicLong    tiempoTotal = new AtomicLong(0);
    private final long          inicio      = System.currentTimeMillis();

    public Medidor(String nombre) {
        this.nombre = nombre;
    }

    public void registrar(long tiempoMensajeMs) {
        int total = mensajes.incrementAndGet();
        tiempoTotal.addAndGet(tiempoMensajeMs);

        if (total % 5 == 0) {
            long elapsed      = System.currentTimeMillis() - inicio;
            double throughput = (total * 1000.0) / elapsed;
            double latMedia   = tiempoTotal.get() / (double) total;

            System.out.printf(
                "[%s] mensajes=%-4d | throughput=%.2f msg/s | latencia_media=%.1f ms%n",
                nombre, total, throughput, latMedia);
        }
    }
}