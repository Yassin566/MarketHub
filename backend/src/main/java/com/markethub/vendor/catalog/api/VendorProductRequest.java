package com.markethub.vendor.catalog.api;
import jakarta.validation.constraints.*; import java.math.BigDecimal;
public record VendorProductRequest(@NotBlank @Size(min=2,max=180) String name,@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=200) String slug,@Size(max=4000) String description,@NotNull @DecimalMin(value="0.0000") @Digits(integer=15,fraction=4) BigDecimal price,@NotBlank @Pattern(regexp="[A-Z]{3}") String currency,@PositiveOrZero Long expectedVersion){}
