package com.docuai.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** Expose le {@link WebClient.Builder} partagé par tous les adaptateurs {@code provider.*} (timeout de réponse commun). */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    @Bean
    public WebClient.Builder aiWebClientBuilder() {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder().clientConnector(new ReactorClientHttpConnector(httpClient));
    }
}
