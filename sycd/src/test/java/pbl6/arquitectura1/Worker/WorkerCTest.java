package pbl6.arquitectura1.Worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WorkerCTest {

    @Test
    void distanciaPequenaNoEsCoche() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        200,
                        50,
                        50,
                        50,
                        10,
                        "0","0","0","",0);

        assertFalse(WorkerC.clasificarKotxea(r));
    }

    @Test
    void velocidadMaximaAltaEsCoche() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        500,
                        10,
                        40,
                        10,
                        10,
                        "0","0","0","",0);

        assertTrue(WorkerC.clasificarKotxea(r));
    }

    @Test
    void velocidadMediaUrbanaEsCoche() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        1500,
                        20,
                        20,
                        20,
                        10,
                        "0","0","0","",0);

        assertTrue(WorkerC.clasificarKotxea(r));
    }

    @Test
    void velocidadFinalAltaEsCoche() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        400,
                        10,
                        10,
                        35,
                        10,
                        "0","0","0","",0);

        assertTrue(WorkerC.clasificarKotxea(r));
    }

    @Test
    void velocidadesBajasNoEsCoche() {

        ResumenWorker r =
                new ResumenWorker(
                        1,1,
                        600,
                        10,
                        12,
                        10,
                        10,
                        "0","0","0","",0);

        assertFalse(WorkerC.clasificarKotxea(r));
    }
}