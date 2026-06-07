package pbl6.arquitectura1.Worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskWorkerTest {

    @Test
    void mismaPosicionDebeDarCeroMetros() {

        double distancia =
                TaskWorker.haversineMeters(
                        43.263,
                        -2.935,
                        43.263,
                        -2.935);

        assertEquals(0.0, distancia, 0.01);
    }

    @Test
    void distanciaEntreDosPuntosDebeSerPositiva() {

        double distancia =
                TaskWorker.haversineMeters(
                        43.263,
                        -2.935,
                        43.264,
                        -2.935);

        assertTrue(distancia > 0);
    }
}