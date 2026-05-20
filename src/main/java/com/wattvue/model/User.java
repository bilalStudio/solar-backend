package com.wattvue.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(unique = true, nullable = false)
    private String email;

    // Nullable because Google OAuth users don't have a password
    @Column(name = "password")
    private String password;

    @Column(nullable = false)
    @Builder.Default
    private String role = "USER";

    // LOCAL or GOOGLE
    @Column(name = "auth_provider", nullable = false)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "google_id")
    private String googleId;

    @Column(name = "picture_url", length = 500)
    private String pictureUrl;

    // Password reset
    @Column(name = "reset_token")
    private String resetToken;

    @Column(name = "reset_token_expiry")
    private LocalDateTime resetTokenExpiry;

    @Column(name = "created_at")
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;
}
