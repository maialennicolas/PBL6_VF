package com.ecomove.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebController {

    @GetMapping({"/", "/app", "/login", "/register"})
    public String index() {
        return "forward:/index.html";
    }
}
