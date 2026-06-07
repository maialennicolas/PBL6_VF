package pbl6.arquitectura2.Config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CO2StreamConfigTest {

    @Test
    void constantesNoDebenSerNull() {

        assertNotNull(CO2StreamConfig.EXCHANGE_CO2_FANOUT);
        assertNotNull(CO2StreamConfig.QUEUE_CO2);
        assertNotNull(CO2StreamConfig.EXCHANGE_CO2_RESULTADO);
        assertNotNull(CO2StreamConfig.QUEUE_CO2_RESULTADO);
    }
}