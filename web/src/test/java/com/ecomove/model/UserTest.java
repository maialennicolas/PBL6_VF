package com.ecomove.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void recordStoresValues() {

        User user =
                new User(
                        1,
                        2,
                        "Jon",
                        "Perez",
                        "jperez",
                        "1234",
                        "jon@test.com",
                        true,
                        "TESLA",
                        "Arrasate"
                );

        assertEquals(1, user.userID());
        assertEquals(2, user.empresaID());
        assertEquals("Jon", user.nombre());
        assertTrue(user.tieneCoche());
    }
}