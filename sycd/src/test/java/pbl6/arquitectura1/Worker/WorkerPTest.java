package pbl6.arquitectura1.Worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerPTest {

    @Test
    void velocidadBajaEsOinez() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        100,
                        4,
                        4,
                        4,
                        10,
                        "0","0","0","",0);

        assertEquals("OINEZ", WorkerP.clasificar(r));
    }

    @Test
    void velocidadMediaEsKorrika() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        100,
                        10,
                        10,
                        10,
                        10,
                        "0","0","0","",0);

        assertEquals("KORRIKA", WorkerP.clasificar(r));
    }

    @Test
    void bicicletaDistanciaCorta() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        400,
                        18,
                        18,
                        18,
                        10,
                        "0","0","0","",0);

        assertEquals("TXIRRINA", WorkerP.clasificar(r));
    }

    @Test
    void patineteDistanciaLarga() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        700,
                        18,
                        18,
                        18,
                        10,
                        "0","0","0","",0);

        assertEquals("PATINETE", WorkerP.clasificar(r));
    }

    @Test
    void posibleCocheDevuelveNull() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        1500,
                        25,
                        25,
                        25,
                        10,
                        "0","0","0","",0);

        assertNull(WorkerP.clasificar(r));
    }
}