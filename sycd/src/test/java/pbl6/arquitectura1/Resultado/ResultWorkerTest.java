package pbl6.arquitectura1.Resultado;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class ResultWorkerTest {

    @Test
    void prioridadBusDebeSerTres() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "prioridad",
                        String.class);

        metodo.setAccessible(true);

        int resultado =
                (int) metodo.invoke(worker, "BUS");

        assertEquals(3, resultado);
    }

    @Test
    void prioridadKotxeaDebeSerDos() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "prioridad",
                        String.class);

        metodo.setAccessible(true);

        int resultado =
                (int) metodo.invoke(worker, "KOTXEA");

        assertEquals(2, resultado);
    }

    @Test
    void prioridadOinezDebeSerUno() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "prioridad",
                        String.class);

        metodo.setAccessible(true);

        int resultado =
                (int) metodo.invoke(worker, "OINEZ");

        assertEquals(1, resultado);
    }

    @Test
    void prioridadNullDebeSerCero() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "prioridad",
                        String.class);

        metodo.setAccessible(true);

        int resultado =
                (int) metodo.invoke(worker, new Object[]{null});

        assertEquals(0, resultado);
    }

    @Test
    void clasificacionDebeExtraerseCorrectamente() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "clasificacion",
                        String.class);

        metodo.setAccessible(true);

        String resultado =
                (String) metodo.invoke(
                        worker,
                        "1 10 BUS 43.2 -2.9 12345");

        assertEquals("BUS", resultado);
    }

    @Test
    void clasificacionMensajeVacioDebeSerCadenaVacia() throws Exception {

        ResultWorker worker = new ResultWorker();

        Method metodo =
                ResultWorker.class.getDeclaredMethod(
                        "clasificacion",
                        String.class);

        metodo.setAccessible(true);

        String resultado =
                (String) metodo.invoke(worker, "");

        assertEquals("", resultado);
    }
}