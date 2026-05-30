package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import com.wattvue.dto.FieldAuthRequest;
import com.wattvue.model.FieldUser;
import com.wattvue.repository.FieldUserRepository;
import com.wattvue.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/field/auth")
@RequiredArgsConstructor
public class FieldAuthController {

    private final FieldUserRepository fieldUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, Object>>> login(@RequestBody FieldAuthRequest req) {
        FieldUser user = fieldUserRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));

        if (!user.getIsActive()) {
            throw new RuntimeException("Account is disabled. Contact your administrator.");
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtUtil.generateToken(user.getEmail(), "TECHNICIAN");

        return ResponseEntity.ok(ApiResponse.success("Login successful", Map.of(
                "token", token,
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()
        )));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<Map<String, Object>>> register(@RequestBody FieldAuthRequest req) {
        if (fieldUserRepository.existsByEmail(req.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        FieldUser user = FieldUser.builder()
                .name(req.getEmail().split("@")[0])
                .email(req.getEmail())
                .password(passwordEncoder.encode(req.getPassword()))
                .role("TECHNICIAN")
                .isActive(true)
                .build();

        user = fieldUserRepository.save(user);

        String token = jwtUtil.generateToken(user.getEmail(), "TECHNICIAN");

        return ResponseEntity.ok(ApiResponse.success("Registration successful", Map.of(
                "token", token,
                "id", user.getId(),
                "name", user.getName(),
                "email", user.getEmail(),
                "role", user.getRole()
        )));
    }
}
