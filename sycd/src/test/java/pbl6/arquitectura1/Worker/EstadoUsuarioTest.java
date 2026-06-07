package pbl6.arquitectura1.Worker;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EstadoUsuarioTest {

    @Test
    void registrarPuntoDebeGuardarPuntos() {

        EstadoUsuario estado = new EstadoUsuario();

        estado.registrarPunto(
                new Punto(
                        43.26,
                        -2.93,
                        10,
                        1000));

        assertEquals(1, estado.puntos.size());
    }

    @Test
    void puntoDuplicadoDebeSobrescribir() {

        EstadoUsuario estado = new EstadoUsuario();

        estado.registrarPunto(
                new Punto(
                        43.26,
                        -2.93,
                        10,
                        1000));

        estado.registrarPunto(
                new Punto(
                        43.26,
                        -2.93,
                        20,
                        1000));

        assertEquals(1, estado.puntos.size());

        Punto p = estado.puntos.get(0);

        assertEquals(20, p.velocidadKmh);
    }

    @Test
    void resumenBasicoDebeCalcularse() {

        EstadoUsuario estado = new EstadoUsuario();

        estado.userId = 1;
        estado.empresaId = 2;
        estado.timestampInicio = 1000;

        estado.registrarPunto(
                new Punto(
                        43.263,
                        -2.935,
                        10,
                        1000));

        estado.registrarPunto(
                new Punto(
                        43.264,
                        -2.935,
                        15,
                        2000));

        ResumenViaje resumen =
                estado.calcularResumen();

        assertEquals(1, resumen.userId);
        assertEquals(2, resumen.empresaId);

        assertTrue(resumen.distanciaMetros > 0);
        assertTrue(resumen.velocidadMaxKmh > 0);
    }
}