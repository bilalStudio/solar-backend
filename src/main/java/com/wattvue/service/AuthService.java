package com.wattvue.service;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.wattvue.dto.AuthResponse;
import com.wattvue.dto.LoginRequest;
import com.wattvue.dto.RegisterRequest;
import com.wattvue.model.User;
import com.wattvue.repository.UserRepository;
import com.wattvue.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final EmailService emailService;

    @Value("${google.oauth.client-id:}")
    private String googleClientId;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // ─── LOGIN ──────────────────────────────────────────────────────────────────
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        String token = jwtUtil.generateToken(user.getEmail());
        return buildAuthResponse(user, token);
    }

    // ─── REGISTER ───────────────────────────────────────────────────────────────
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already in use: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role("USER")
                .authProvider("LOCAL")
                .isActive(true)
                .build();

        user = userRepository.save(user);
        String token = jwtUtil.generateToken(user.getEmail());
        return buildAuthResponse(user, token);
    }

    // ─── FORGOT PASSWORD ────────────────────────────────────────────────────────
    public void forgotPassword(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);

        // Always return success — don't reveal whether email exists (security)
        if (userOpt.isEmpty()) {
            log.info("Forgot-password requested for non-existent email: {}", email);
            return;
        }

        User user = userOpt.get();

        if ("GOOGLE".equals(user.getAuthProvider())) {
            throw new RuntimeException("This account uses Google sign-in. Please log in with Google.");
        }

        String resetToken = UUID.randomUUID().toString();
        user.setResetToken(resetToken);
        user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
        userRepository.save(user);

        String resetLink = frontendUrl + "/reset-password?token=" + resetToken;

        try {
            emailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetLink);
            log.info("Password reset email sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send password reset email: {}", e.getMessage());
            // Still don't expose the failure to the caller
        }
    }

    // ─── RESET PASSWORD ─────────────────────────────────────────────────────────
    public void resetPassword(String token, String newPassword) {
        User user = userRepository.findByResetToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid or expired reset token"));

        if (user.getResetTokenExpiry() == null ||
            user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Reset token has expired. Please request a new one.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setResetToken(null);
        user.setResetTokenExpiry(null);
        userRepository.save(user);

        log.info("Password reset for user: {}", user.getEmail());
    }

    // ─── GOOGLE OAUTH LOGIN ─────────────────────────────────────────────────────
    public AuthResponse googleLogin(String idTokenString) {
        if (googleClientId == null || googleClientId.isEmpty()) {
            throw new RuntimeException("Google OAuth is not configured on the server");
        }

        try {
            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(googleClientId))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                throw new RuntimeException("Invalid Google ID token");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String name = (String) payload.get("name");
            String pictureUrl = (String) payload.get("picture");
            String googleId = payload.getSubject();

            User user = userRepository.findByEmail(email).orElseGet(() ->
                    userRepository.save(User.builder()
                            .name(name != null ? name : email)
                            .email(email)
                            .password(null)
                            .role("USER")
                            .authProvider("GOOGLE")
                            .googleId(googleId)
                            .pictureUrl(pictureUrl)
                            .isActive(true)
                            .build())
            );

            // Update Google info if user already existed
            if (!"GOOGLE".equals(user.getAuthProvider())) {
                user.setAuthProvider("GOOGLE");
                user.setGoogleId(googleId);
            }
            if (pictureUrl != null) user.setPictureUrl(pictureUrl);
            userRepository.save(user);

            String token = jwtUtil.generateToken(user.getEmail());
            return buildAuthResponse(user, token);

        } catch (Exception e) {
            log.error("Google OAuth verification failed: {}", e.getMessage());
            throw new RuntimeException("Google login failed: " + e.getMessage());
        }
    }

    // ─── Helper ─────────────────────────────────────────────────────────────────
    private AuthResponse buildAuthResponse(User user, String token) {
        AuthResponse.UserInfo userInfo = AuthResponse.UserInfo.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole())
                .authProvider(user.getAuthProvider())
                .pictureUrl(user.getPictureUrl())
                .build();

        return AuthResponse.builder()
                .token(token)
                .user(userInfo)
                .build();
    }
}
