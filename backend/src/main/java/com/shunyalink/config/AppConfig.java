package com.shunyalink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class AppConfig {

    /**
     * Define a global RestTemplate with strict timeouts to prevent
     * external service failures from cascading through our system.
     * MAANG Principle: Fail fast or timeout rather than blocking threads.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(1000); // 1 second
        factory.setReadTimeout(1000);    // 1 second
        return new RestTemplate(factory);
    }
}
