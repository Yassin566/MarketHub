package com.markethub.vendor.inventory.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record InventoryAdjustmentRequest(
        @NotNull @Min(-1_000_000_000L) @Max(1_000_000_000L) Long quantityDelta,
        @NotBlank @Pattern(regexp = "RESTOCK|CORRECTION|DAMAGE|RETURN") String reason,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
