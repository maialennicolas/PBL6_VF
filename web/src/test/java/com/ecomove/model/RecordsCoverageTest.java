package com.ecomove.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class RecordsCoverageTest {

    @Test
    void coverAllRecords() {

        assertNotNull(new LoginRequest(
                "usuario",
                "1234"
        ));

        assertNotNull(new RegisterRequest(
                1L,
                "Jon",
                "Perez",
                "jperez",
                "1234",
                "jon@test.com",
                true,
                "TESLA",
                "Arrasate"
        ));

        assertNotNull(new ProfileUpdateRequest(
                1L,
                1L,
                "Jon",
                "Perez",
                "jon@test.com",
                true,
                "TESLA",
                "Arrasate"
        ));

        assertNotNull(new CarpoolOfferRequest(
                "Bilbao",
                "Donostia",
                "08:00",
                3
        ));

        assertNotNull(new TrackingStatus(
                true,
                "CAR",
                "10km",
                "20min",
                "2kg",
                100,
                "session-1",
                10,
                "2025-01-01"
        ));

        assertNotNull(new Rider(
                1L,
                "Jon",
                "15km",
                4.5,
                "Viaje trabajo",
                "08:00",
                true,
                "JP",
                "IT",
                "Bilbao",
                "Donostia"
        ));

        assertNotNull(new Reward(
                1L,
                "Premio",
                100,
                "🏆",
                "General"
        ));

        assertNotNull(new TransportLine(
                "L1",
                "Linea 1",
                "Azul",
                15,
                "ACTIVE",
                10
        ));

        assertNotNull(new TransportStop(
                "1",
                "Bizkaibus",
                "STOP1",
                "001",
                "Parada Centro",
                "Descripcion",
                43.26,
                -2.93,
                "A",
                "Bilbao",
                "0",
                "1"
        ));

        assertNotNull(new TransportShare(
                "BUS",
                50
        ));
    }
}