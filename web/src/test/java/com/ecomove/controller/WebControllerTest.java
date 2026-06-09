package com.ecomove.controller;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WebControllerTest {

    @Test
    void indexReturnsForward() {

        WebController controller = new WebController();

        assertEquals(
                "forward:/index.html",
                controller.index()
        );
    }
}