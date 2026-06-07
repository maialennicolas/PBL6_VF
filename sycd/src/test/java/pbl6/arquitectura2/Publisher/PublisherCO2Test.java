package pbl6.arquitectura2.Publisher;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PublisherCO2Test {

    @Test
    void generarDatosSimuladosDebeCrearDiezMensajes() {

        List<String> datos = PublisherCO2.generarDatosSimulados();

        assertEquals(10, datos.size());
    }
}