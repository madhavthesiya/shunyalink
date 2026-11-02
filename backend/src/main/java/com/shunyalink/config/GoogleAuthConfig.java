package com.shunyalink.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class GoogleAuthConfig {

    /**
     * One shared, thread-safe verifier: it caches Google's signing certificates, which a
     * per-request instance would re-download on every sign-in.
     */
    @Bean
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${app.google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }
}
