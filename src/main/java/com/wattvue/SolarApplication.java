package com.wattvue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SolarApplication {
    public static void main(String[] args) {
        SpringApplication.run(SolarApplication.class, args);
        System.out.println("\n========================================");
        System.out.println("  WattVue Solar Backend is RUNNING");
        System.out.println("  API: http://localhost:8080");
        System.out.println("  Health: http://localhost:8080/api/health");
        System.out.println("  Default login: admin@wattvue.com / admin123");
        System.out.println("========================================\n");
    }
}
