package pbl6.arquitectura1.Worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResumenWorkerTest {

    @Test
    void parseFormatoNuevo() {

        String mensaje =
                "1 2 1000 20 40 30 120 43.2 -2.9 12345 sesion1 10";

        ResumenWorker r = ResumenWorker.parse(mensaje);

        assertNotNull(r);
        assertEquals(1, r.userId);
        assertEquals(2, r.empresaId);
        assertEquals(1000, r.distanciaMetros);
        assertEquals(20, r.velocidadMediaKmh);
        assertEquals("sesion1", r.sessionId);
    }

    @Test
    void parseFormatoAntiguo() {

        String mensaje =
                "1 2 500 15 60 43.2 -2.9 12345";

        ResumenWorker r = ResumenWorker.parse(mensaje);

        assertNotNull(r);
        assertEquals(500, r.distanciaMetros);
        assertEquals(15, r.velocidadFinalKmh);
    }

    @Test
    void parseInvalidoDevuelveNull() {

        assertNull(
                ResumenWorker.parse("hola")
        );
    }

    @Test
    void resultadoDebeGenerarCadenaCorrecta() {

        ResumenWorker r =
                new ResumenWorker(
                        1,2,
                        100,
                        10,
                        20,
                        15,
                        60,
                        "43.2",
                        "-2.9",
                        "12345",
                        "abc",
                        5);

        String resultado =
                r.resultado("BUS");

        assertEquals(
                "1 2 BUS 43.2 -2.9 12345 abc",
                resultado);
    }
    
}