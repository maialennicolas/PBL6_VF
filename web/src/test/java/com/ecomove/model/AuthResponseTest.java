package com.ecomove.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthResponseTest {

    @Test
    void recordStoresValues() {

        AuthResponse response =
                new AuthResponse(
                        true,
                        "ok",
                        null
                );

        assertTrue(response.ok());
        assertEquals("ok", response.message());
        assertNull(response.user());
    }
}