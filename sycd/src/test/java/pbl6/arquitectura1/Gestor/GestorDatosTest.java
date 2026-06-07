package pbl6.arquitectura1.Gestor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GestorDatosTest {

    @Test
    void debeAcumularDatosHastaCompletarVentana() {

        GestorDatos gestor = new GestorDatos();

        for (int i = 1; i <= 4; i++) {
            GestorDatos.Estadisticas est =
                    gestor.calcular(1, 1, 0, 0, 0, i);

            assertFalse(est.completo);
        }
    }

    @Test
    void debeCalcularEstadisticasAlLlegarACincoDatos() {

        GestorDatos gestor = new GestorDatos();

        gestor.calcular(1,1,0,0,0,10);
        gestor.calcular(1,1,0,0,0,20);
        gestor.calcular(1,1,0,0,0,30);
        gestor.calcular(1,1,0,0,0,40);

        GestorDatos.Estadisticas est =
                gestor.calcular(1,1,0,0,0,50);

        assertTrue(est.completo);
        assertEquals(30.0, est.media);
        assertEquals(50.0, est.max);
        assertEquals(10.0, est.min);
        assertEquals(40.0, est.distantzia);
    }

    @Test
    void debeAplicarVentanaDeslizante() {

        GestorDatos gestor = new GestorDatos();

        gestor.calcular(1,1,0,0,0,1);
        gestor.calcular(1,1,0,0,0,2);
        gestor.calcular(1,1,0,0,0,3);
        gestor.calcular(1,1,0,0,0,4);
        gestor.calcular(1,1,0,0,0,5);

        GestorDatos.Estadisticas est =
                gestor.calcular(1,1,0,0,0,6);

        assertTrue(est.completo);

        assertEquals(4.0, est.media);
        assertEquals(6.0, est.max);
        assertEquals(2.0, est.min);
        assertEquals(4.0, est.distantzia);
    }

    @Test
    void usuariosDiferentesNoCompartenDatos() {

        GestorDatos gestor = new GestorDatos();

        for(int i = 0; i < 5; i++) {
            gestor.calcular(1,1,0,0,0,10);
        }

        for(int i = 0; i < 5; i++) {
            gestor.calcular(2,1,0,0,0,100);
        }

        GestorDatos.Estadisticas est1 =
                gestor.calcular(1,1,0,0,0,10);

        GestorDatos.Estadisticas est2 =
                gestor.calcular(2,1,0,0,0,100);

        assertEquals(10.0, est1.media);
        assertEquals(100.0, est2.media);
    }

    @Test
    void toStringDebeMostrarAcumulandoSiNoEstaCompleto() {

        GestorDatos.Estadisticas est =
                new GestorDatos.Estadisticas(
                        1,1,0,0,0,
                        0,0,0,0,false);

        assertTrue(est.toString().contains("acumulando"));
    }
}