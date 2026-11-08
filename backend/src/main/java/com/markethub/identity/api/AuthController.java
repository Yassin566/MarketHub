package com.markethub.identity.api;
import com.markethub.common.api.ApiResponse;
import com.markethub.identity.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
 private final AuthService auth;
 public AuthController(AuthService auth){this.auth=auth;}
 @PostMapping("/register") public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest r){return ApiResponse.ok(auth.register(r));}
 @PostMapping("/login") public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest r){return ApiResponse.ok(auth.login(r));}
}