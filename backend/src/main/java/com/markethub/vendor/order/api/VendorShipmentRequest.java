package com.markethub.vendor.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record VendorShipmentRequest(
        @NotBlank @Size(max = 80) String carrier,
        @NotBlank @Size(max = 120) String trackingNumber,
        @Size(max = 2048) String trackingUrl,
        LocalDate estimatedDeliveryDate
) {}
