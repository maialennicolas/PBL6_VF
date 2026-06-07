package com.ecomove;

import com.ecomove.model.*;
import com.ecomove.service.CsvDataService;
import com.ecomove.service.EcoMoveService;
import com.ecomove.service.RabbitMqTripPublisher;
import com.ecomove.service.UserCsvService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EcoMoveExtraTest {

    @Mock
    private UserCsvService userCsvService;

    @Mock
    private CsvDataService csv;

    @Mock
    private RabbitMqTripPublisher sycdPublisher;

    @InjectMocks
    private EcoMoveService service;

    @Test
    void getTransportLines_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getTransportLines());
    }

    @Test
    void getTransportStops_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getTransportStops(null, 10));
    }

    @Test
    void getRewards_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getRewards(null));
    }

    @Test
    void getRecentTrips_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getRecentTrips(1L));
    }

    @Test
    void getMonthlyStats_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getMonthlyStats(1L));
    }

    @Test
    void getTransportShare_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        assertNotNull(service.getTransportShare(1L));
    }

    @Test
    void getRiders_no_falla() {

        when(csv.readRows(anyString()))
                .thenReturn(List.of());

        when(userCsvService.getAllUsers())
                .thenReturn(List.of());

        assertNotNull(service.getRiders(1L));
    }
}