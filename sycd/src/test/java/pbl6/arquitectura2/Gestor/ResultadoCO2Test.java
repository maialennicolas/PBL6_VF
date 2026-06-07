package pbl6.arquitectura2.Gestor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ResultadoCO2Test {

    @Test
    void toLainoa2DebeGenerarFormatoCorrecto() {

        CalculadoraCO2.ResultadoCO2 r =
                new CalculadoraCO2.ResultadoCO2(
                        1,
                        2,
                        "BUS",
                        "BUS",
                        10.5,
                        500,
                        200,
                        30,
                        false,
                        1,
                        "43.2",
                        "-2.9",
                        "123456",
                        "SESSION1");

        String texto = r.toLainoa2();

        assertTrue(texto.contains("1 2 BUS"));
        assertTrue(texto.contains("SESSION1"));
    }

    @Test
    void toStringDebeContenerDatosPrincipales() {

        CalculadoraCO2.ResultadoCO2 r =
                new CalculadoraCO2.ResultadoCO2(
                        1,
                        2,
                        "BUS",
                        "BUS",
                        10,
                        1000,
                        500,
                        20,
                        false,
                        1,
                        "43",
                        "-2",
                        "123",
                        "ABC");

        String texto = r.toString();

        assertTrue(texto.contains("user=1"));
        assertTrue(texto.contains("empresa=2"));
        assertTrue(texto.contains("BUS"));
    }
}