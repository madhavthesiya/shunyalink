package com.shunyalink.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final String baseUrl;
    private final String frontendUrl;
    private final String mailUsername;

    public EmailService(JavaMailSender mailSender,
                        @Value("${app.base-url}") String baseUrl,
                        @Value("${app.allowed-origin}") String frontendUrl,
                        @Value("${spring.mail.username:noreply.shunyalink@gmail.com}") String mailUsername) {
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
        this.frontendUrl = frontendUrl;
        this.mailUsername = mailUsername;
    }

    @Async
    public void sendVerificationEmail(String to, String token) {
        String link = baseUrl + "/api/v1/auth/verify?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("ShunyaLink - Verify your email");
        message.setText("Click the link below to verify your email:\n\n" + link
                + "\n\nThis link expires in 24 hours.");
        mailSender.send(message);
    }

    @Async
    public void sendPasswordResetEmail(String to, String token) {
        String link = frontendUrl + "/reset-password?token=" + token;

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("ShunyaLink - Reset your password");
        message.setText("Click the link below to reset your password:\n\n" + link
                + "\n\nThis link expires in 1 hour. If you didn't request this, ignore this email.");
        mailSender.send(message);
    }
}
