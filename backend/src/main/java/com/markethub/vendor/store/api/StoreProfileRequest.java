package com.markethub.vendor.store.api;
import jakarta.validation.constraints.*;
public record StoreProfileRequest(@NotBlank @Size(min=2,max=160) String storeName,@NotBlank @Pattern(regexp="[a-z0-9]+(?:-[a-z0-9]+)*") @Size(max=180) String slug,@Size(max=2000) String description,@Size(max=1000) String logoUrl,@Email @Size(max=254) String supportEmail,@Size(max=40) String supportPhone){}