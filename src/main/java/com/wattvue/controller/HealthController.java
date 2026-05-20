package com.wattvue.controller;

import com.wattvue.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.success("Healthy", Map.of(
                "status", "UP",
                "service", "WattVue Solar Backend",
                "version", "1.0.0"
        ));
    }
}
