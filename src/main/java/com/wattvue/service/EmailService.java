package com.wattvue.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromEmail;

    @Value("${app.mail.from-name}")
    private String fromName;

    @Value("${spring.mail.host:smtp.gmail.com}")
    private String mailHost;

    @Value("${spring.mail.port:587}")
    private String mailPort;

    @Value("${spring.mail.username:}")
    private String mailUsername;

    @Value("${spring.mail.password:}")
    private String mailPassword;

    @Value("${app.mail.transport:nodemailer}")
    private String mailTransport;

    @Value("${app.mail.nodemailer.script:mailer/send-mail.js}")
    private String nodemailerScript;

    private boolean isEmailConfigured() {
        return mailUsername != null && !mailUsername.isBlank()
                && mailPassword != null && !mailPassword.isBlank();
    }

    /**
     * Send password-reset email with a reset link.
     */
    public void sendPasswordResetEmail(String toEmail, String userName, String resetLink) throws Exception {
        if (!isEmailConfigured()) {
            log.warn("Email not configured (MAIL_USERNAME or MAIL_PASSWORD missing). Reset link: {}", resetLink);
            return;
        }

        if (useNodemailer()) {
            Map<String, Object> payload = basePayload(toEmail, null, "Reset your WattVue password");
            payload.put("html", buildResetEmailHtml(userName, resetLink));
            sendWithNodemailer(payload);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(new InternetAddress(fromEmail, fromName));
        helper.setTo(toEmail);
        helper.setSubject("Reset your WattVue password");
        helper.setText(buildResetEmailHtml(userName, resetLink), true);

        mailSender.send(message);
    }

    /**
     * Send a report email with PDF attachment.
     */
    public void sendReportEmail(String toEmail, String cc, String subject, String body,
                                 String attachmentPath, String attachmentName) throws Exception {
        if (!isEmailConfigured()) {
            throw new RuntimeException("Email is not configured on the server. Please set MAIL_USERNAME and MAIL_PASSWORD environment variables.");
        }

        if (useNodemailer()) {
            Map<String, Object> payload = basePayload(toEmail, cc, subject);
            payload.put("text", body);
            payload.put("attachmentPath", attachmentPath);
            payload.put("attachmentName", attachmentName);
            sendWithNodemailer(payload);
            return;
        }

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());

        helper.setFrom(new InternetAddress(fromEmail, fromName));
        helper.setTo(toEmail);
        if (cc != null && !cc.isEmpty()) {
            helper.setCc(cc.split(","));
        }
        helper.setSubject(subject);
        helper.setText(body, false);

        if (attachmentPath != null) {
            File file = new File(attachmentPath);
            if (file.exists()) {
                helper.addAttachment(attachmentName != null ? attachmentName : file.getName(),
                        new FileSystemResource(file));
            }
        }

        mailSender.send(message);
    }

    private boolean useNodemailer() {
        return "nodemailer".equalsIgnoreCase(mailTransport);
    }

    private Map<String, Object> basePayload(String toEmail, String cc, String subject) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("host", mailHost);
        payload.put("port", mailPort);
        payload.put("username", mailUsername);
        payload.put("password", mailPassword);
        payload.put("fromEmail", fromEmail);
        payload.put("fromName", fromName);
        payload.put("toEmail", toEmail);
        payload.put("cc", cc);
        payload.put("subject", subject);
        return payload;
    }

    private void sendWithNodemailer(Map<String, Object> payload) throws Exception {
        File script = new File(nodemailerScript);
        if (!script.isAbsolute()) {
            script = new File(System.getProperty("user.dir"), nodemailerScript);
        }
        if (!script.exists()) {
            throw new RuntimeException("Nodemailer script not found: " + script.getAbsolutePath());
        }

        Process process = new ProcessBuilder("node", script.getAbsolutePath())
                .redirectErrorStream(false)
                .start();

        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(toJson(payload).getBytes(StandardCharsets.UTF_8));
        }

        boolean finished = process.waitFor(Duration.ofSeconds(30).toMillis(), TimeUnit.MILLISECONDS);
        String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);

        if (!finished) {
            process.destroyForcibly();
            throw new RuntimeException("Nodemailer timed out while sending email");
        }
        if (process.exitValue() != 0) {
            throw new RuntimeException("Nodemailer failed: " + (stderr.isBlank() ? stdout : stderr).trim());
        }
        log.info("Email sent via Nodemailer to {}", payload.get("toEmail"));
    }

    private String toJson(Map<String, Object> payload) {
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : payload.entrySet()) {
            Object value = entry.getValue();
            if (value == null || value.toString().isBlank()) continue;
            if (!first) json.append(",");
            json.append("\"").append(escapeJson(entry.getKey())).append("\":");
            json.append("\"").append(escapeJson(value.toString())).append("\"");
            first = false;
        }
        return json.append("}").toString();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
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
