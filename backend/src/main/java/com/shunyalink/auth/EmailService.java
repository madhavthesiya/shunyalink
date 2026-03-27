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
                        @Value("${app.frontend-url}") String frontendUrl,
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

    @Async
    public void sendWelcomeEmail(String to, String name) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("Welcome to ShunyaLink! 🎉");
        message.setText("Hi " + name + ",\n\n"
                + "Thank you for verifying your email address! Your ShunyaLink account is now fully active.\n\n"
                + "ShunyaLink is more than just a URL shortener; it is your ultimate Productivity & Branding Platform!\n"
                + "You can securely create, share, and track all your custom shortened links, AND you can now build a beautiful, personalized 'Link-In-Bio' landing page to host your online identity.\n\n"
                + "Happy linking!\n"
                + "The ShunyaLink Team");
        mailSender.send(message);
    }

    @Async
    public void sendGoogleWelcomeEmail(String to, String name) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailUsername);
        message.setTo(to);
        message.setSubject("Welcome to ShunyaLink! 🎉");
        message.setText("Hi " + name + ",\n\n"
                + "Welcome aboard! You have successfully registered for ShunyaLink using your Google account.\n\n"
                + "ShunyaLink is more than just a URL shortener; it is your ultimate Productivity & Branding Platform!\n"
                + "Your account is now fully active. You can securely create, share, and track all your custom shortened links, AND you can now build a beautiful, personalized 'Link-In-Bio' landing page to host your online identity.\n\n"
                + "Happy linking!\n"
                + "The ShunyaLink Team");
        mailSender.send(message);
    }
}
