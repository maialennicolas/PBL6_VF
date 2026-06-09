package com.ecomove.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthRequestTest {

    @Test
    void recordStoresValues() {

        AuthRequest request =
                new AuthRequest(
                        "test@test.com",
                        "1234",
                        "Jon"
                );

        assertEquals("test@test.com", request.email());
        assertEquals("1234", request.password());
        assertEquals("Jon", request.name());
    }
}