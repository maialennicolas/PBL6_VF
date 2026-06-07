package com.ecomove.service;

import com.ecomove.model.*;
import com.ecomove.service.CsvDataService;
import com.ecomove.service.EcoMoveService;
import com.ecomove.service.RabbitMqTripPublisher;
import com.ecomove.service.UserCsvService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcoMoveServiceTest {

    @Mock
    private UserCsvService userCsvService;

    @Mock
    private CsvDataService csv;

    @Mock
    private RabbitMqTripPublisher sycdPublisher;

    @InjectMocks
    private EcoMoveService service;

    private User user;

    @BeforeEach
    void setUp() {

        user = new User(
                1L,
                1L,
                "Pepe",
                "Garcia",
                "pepe",
                "1234",
                "pepe@test.com",
                false,
                "SIN_COCHE",
                "Bilbao"
        );
    }

    @Test
    void login_ok() {

        LoginRequest request =
                new LoginRequest("pepe","1234");

        when(userCsvService.findByNombreUsuario("pepe"))
                .thenReturn(Optional.of(user));

        AuthResponse response = service.login(request);

        assertTrue(response.ok());
    }

    @Test
    void login_usuario_no_existe() {

        LoginRequest request =
                new LoginRequest("fake","1234");

        when(userCsvService.findByNombreUsuario("fake"))
                .thenReturn(Optional.empty());

        AuthResponse response = service.login(request);

        assertFalse(response.ok());
    }

    @Test
    void login_password_incorrecta() {

        LoginRequest request =
                new LoginRequest("pepe","xxxx");

        when(userCsvService.findByNombreUsuario("pepe"))
                .thenReturn(Optional.of(user));

        AuthResponse response = service.login(request);

        assertFalse(response.ok());
    }

    @Test
    void register_usuario_existente() {

        RegisterRequest request = mock(RegisterRequest.class);

        when(request.nombreUsuario())
                .thenReturn("pepe");

        when(userCsvService.findByNombreUsuario("pepe"))
                .thenReturn(Optional.of(user));

        AuthResponse response = service.register(request);

        assertFalse(response.ok());
    }

    @Test
    void getProfile_ok() {

        when(userCsvService.findById(1L))
                .thenReturn(Optional.of(user));

        assertNotNull(service.getProfile(1L));
    }
}