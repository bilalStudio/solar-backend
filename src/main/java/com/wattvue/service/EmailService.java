package com.wattvue.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.util.Base64;
import java.time.Duration;

/**
 * Email service using Resend.com HTTP API.
 * Works on Railway free tier (no SMTP port needed).
 * Set RESEND_API_KEY environment variable in Railway.
 * Get free API key at https://resend.com
 */
@Service
@Slf4j
public class EmailService {

    @Value("${app.resend.api-key:}")
    private String resendApiKey;

    @Value("${app.mail.from:onboarding@resend.dev}")
    private String fromEmail;

    @Value("${app.mail.from-name:WattVue Solar}")
    private String fromName;

    private boolean isEmailConfigured() {
        return resendApiKey != null && !resendApiKey.isBlank();
    }

    public void sendPasswordResetEmail(String toEmail, String userName, String resetLink) throws Exception {
        if (!isEmailConfigured()) {
            log.warn("Email not configured (RESEND_API_KEY missing). Reset link: {}", resetLink);
            return;
        }
        String html = buildResetEmailHtml(userName, resetLink);
        sendViaResend(toEmail, null, "Reset your WattVue password", null, html, null, null);
    }

    public void sendReportEmail(String toEmail, String cc, String subject, String body,
                                String attachmentPath, String attachmentName) throws Exception {
        if (!isEmailConfigured()) {
            throw new RuntimeException("Email is not configured. Please set RESEND_API_KEY in Railway environment variables.");
        }
        sendViaResend(toEmail, cc, subject, body, null, attachmentPath, attachmentName);
    }

    private void sendViaResend(String toEmail, String cc, String subject,
                                String textBody, String htmlBody,
                                String attachmentPath, String attachmentName) throws Exception {

        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"from\":\"").append(escapeJson(fromName)).append(" <").append(escapeJson(fromEmail)).append(">\",");
        json.append("\"to\":[\"").append(escapeJson(toEmail)).append("\"],");

        if (cc != null && !cc.isBlank()) {
            String[] ccList = cc.split(",");
            json.append("\"cc\":[");
            for (int i = 0; i < ccList.length; i++) {
                if (i > 0) json.append(",");
                json.append("\"").append(escapeJson(ccList[i].trim())).append("\"");
            }
            json.append("],");
        }

        json.append("\"subject\":\"").append(escapeJson(subject)).append("\",");

        if (htmlBody != null && !htmlBody.isBlank()) {
            json.append("\"html\":\"").append(escapeJson(htmlBody)).append("\"");
        } else {
            json.append("\"text\":\"").append(escapeJson(textBody != null ? textBody : "")).append("\"");
        }

        // Attach PDF if provided
        if (attachmentPath != null) {
            File file = new File(attachmentPath);
            if (file.exists()) {
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Content = Base64.getEncoder().encodeToString(fileBytes);
                String filename = attachmentName != null ? attachmentName : file.getName();
                json.append(",\"attachments\":[{");
                json.append("\"filename\":\"").append(escapeJson(filename)).append("\",");
                json.append("\"content\":\"").append(base64Content).append("\"");
                json.append("}]");
            }
        }

        json.append("}");

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            log.info("Email sent via Resend to {} (status {})", toEmail, response.statusCode());
        } else {
            log.error("Resend API error {}: {}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to send email. Status: " + response.statusCode() + " — " + response.body());
        }
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }

    private String buildResetEmailHtml(String userName, String resetLink) {
        return """
                <!DOCTYPE html>
                <html>
                <body style="font-family: Arial, sans-serif; background:#f5f7fa; padding:24px;">
                  <div style="max-width:560px; margin:0 auto; background:#fff; border-radius:8px; padding:32px;">
                    <h2 style="color:#25A1AB; margin-top:0;">Reset your WattVue password</h2>
                    <p>Hi %s,</p>
                    <p>We received a request to reset your password. Click the button below to set a new one:</p>
                    <div style="text-align:center; margin:28px 0;">
                      <a href="%s" style="background:#25A1AB; color:#fff; padding:12px 24px; text-decoration:none; border-radius:6px; display:inline-block;">Reset Password</a>
                    </div>
                    <p style="color:#666; font-size:13px;">This link expires in 1 hour. If you didn't request a reset, you can safely ignore this email.</p>
                    <hr style="border:none; border-top:1px solid #e8edf3; margin:24px 0;" />
                    <p style="color:#999; font-size:12px;">WattVue Solar Performance Intelligence Platform</p>
                  </div>
                </body>
                </html>
                """.formatted(userName, resetLink);
    }
}
