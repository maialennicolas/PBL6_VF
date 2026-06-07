package pbl6.arquitectura1.Gestor;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ParadasLoaderTest {

    @Test
    void parsearCsvSimple() throws Exception {

        Method metodo =
                ParadasLoader.class.getDeclaredMethod(
                        "parsearCSV",
                        String.class);

        metodo.setAccessible(true);

        String[] resultado =
                (String[]) metodo.invoke(
                        null,
                        "a,b,c");

        assertEquals(3, resultado.length);
        assertEquals("a", resultado[0]);
        assertEquals("b", resultado[1]);
        assertEquals("c", resultado[2]);
    }

    @Test
    void parsearCsvConComasEntreComillas() throws Exception {

        Method metodo =
                ParadasLoader.class.getDeclaredMethod(
                        "parsearCSV",
                        String.class);

        metodo.setAccessible(true);

        String[] resultado =
                (String[]) metodo.invoke(
                        null,
                        "\"a,b\",c,d");

        assertEquals(3, resultado.length);
        assertEquals("a,b", resultado[0]);
        assertEquals("c", resultado[1]);
        assertEquals("d", resultado[2]);
    }

    @Test
    void constructorNoDebeLanzarExcepcion() {

        assertDoesNotThrow(ParadasLoader::new);
    }
}