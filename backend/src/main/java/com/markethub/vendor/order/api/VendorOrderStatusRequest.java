package com.markethub.vendor.order.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VendorOrderStatusRequest(
        @NotBlank
        @Pattern(regexp = "PROCESSING|FULFILLED")
        String status,
        @NotBlank
        @Size(max = 500)
        String note
) {}
