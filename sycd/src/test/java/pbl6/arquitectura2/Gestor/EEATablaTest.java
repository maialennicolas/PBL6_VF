package pbl6.arquitectura2.Gestor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EEATablaTest {

    @Test
    void debeDevolverFactorExacto() {
        double factor = EEATabla.obtenerFactor("Toyota", "Corolla");

        assertEquals(95.0, factor);
    }

    @Test
    void debeUsarMediaMarcaCuandoModeloNoExiste() {
        double factor = EEATabla.obtenerFactor("Toyota", "Inventado");

        assertEquals(105.0, factor);
    }

    @Test
    void debeUsarDefaultGlobalSiMarcaNoExiste() {
        double factor = EEATabla.obtenerFactor("MarcaRara", "ModeloRaro");

        assertEquals(EEATabla.CO2_DEFAULT_G_KM, factor);
    }

    @Test
    void debeNormalizarMayusculasYEspacios() {
        double factor = EEATabla.obtenerFactor(" toyota ", " corolla ");

        assertEquals(95.0, factor);
    }

    @Test
    void cocheElectricoDebeDarCero() {
        double factor = EEATabla.obtenerFactor("Tesla", "Model3");

        assertEquals(0.0, factor);
    }
}