package com.ecomove.controller;

import com.ecomove.model.*;
import com.ecomove.service.EcoMoveService;
import com.ecomove.service.SycdQueryClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.when;

@WebMvcTest(EcoMoveController.class)
class EcoMoveControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EcoMoveService service;

    @MockBean
    private SycdQueryClient sycdQueryClient;

    @Test
    void login_ok() throws Exception {

        AuthResponse response =
                new AuthResponse(true, "Login correcto", null);

        when(service.login(any(LoginRequest.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nombreUsuario":"test",
                                  "contrasena":"1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

   @Test
void register_ok() throws Exception {

    AuthResponse response =
            new AuthResponse(true, "Usuario registrado", null);

    when(service.register(any(RegisterRequest.class)))
            .thenReturn(response);

    mockMvc.perform(post("/api/auth/register")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                    {
                      "empresaID":1,
                      "nombre":"Pepe",
                      "apellidos":"Garcia",
                      "nombreUsuario":"pepe",
                      "contrasena":"1234",
                      "email":"pepe@test.com",
                      "tieneCoche":false,
                      "modeloCocheID":"",
                      "puebloCiudad":"Bilbao"
                    }
                    """))
            .andExpect(status().isOk());
}

    @Test
    void dashboard_ok() throws Exception {

        when(service.getDashboard(1L))
                .thenReturn(Mockito.mock(DashboardResponse.class));

        mockMvc.perform(get("/api/dashboard")
                        .param("userId","1"))
                .andExpect(status().isOk());
    }

    @Test
    void rewards_ok() throws Exception {

        when(service.getRewards(null))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/rewards"))
                .andExpect(status().isOk());
    }

    @Test
    void redeemReward_ok() throws Exception {

        when(service.redeemReward(1L,2L))
                .thenReturn(true);

        mockMvc.perform(post("/api/rewards/redeem")
                        .param("userId","1")
                        .param("rewardId","2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ok").value(true));
    }

    @Test
    void sycdStatus_ok() throws Exception {

        when(sycdQueryClient.status())
                .thenReturn(Map.of("status","UP"));

        mockMvc.perform(get("/api/sycd/status"))
                .andExpect(status().isOk());
    }
}