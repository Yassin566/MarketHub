package com.markethub.customer.cart.api;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record PutCartItemRequest(@NotNull UUID productId, @Min(1) @Max(1000) long quantity) {}
