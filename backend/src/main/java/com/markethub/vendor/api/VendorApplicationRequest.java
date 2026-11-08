package com.markethub.vendor.api;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record VendorApplicationRequest(@NotBlank @Size(min=2,max=160) String displayName){}