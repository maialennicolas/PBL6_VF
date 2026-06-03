package com.ecomove.model;

public record CarpoolOfferRequest(
        String from,
        String to,
        String time,
        int seats
) {}
