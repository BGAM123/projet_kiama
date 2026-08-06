package com.docuai.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/** Expose le {@link WebClient.Builder} partagé par tous les adaptateurs {@code provider.*} (timeout de réponse commun). */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiClientConfig {

    @Bean
    public WebClient.Builder aiWebClientBuilder() {
        HttpClient httpClient = HttpClient.create().responseTimeout(Duration.ofSeconds(60));
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .filter(forceUtf8ResponseCharset());
    }

    /**
     * OpenAI/Groq/Mistral/DeepSeek (JSON) et Ollama (NDJSON) renvoient tous
     * un {@code Content-Type} sans paramètre {@code charset} explicite. Les
     * décodeurs réactifs de Spring (String/Jackson) retombent alors sur
     * ISO-8859-1 plutôt que sur l'UTF-8 imposé par la RFC JSON, corrompant
     * les caractères accentués (ex. "é" -> "Ã©") — reproductible aussi bien
     * en appel simple (JsonNode) qu'en streaming (NDJSON/SSE). On force donc
     * explicitement {@code charset=UTF-8} sur la réponse avant qu'elle
     * n'atteigne les décodeurs, une fois pour tous les adaptateurs plutôt
     * que de contourner le problème dans chacun séparément.
     */
    private ExchangeFilterFunction forceUtf8ResponseCharset() {
        return ExchangeFilterFunction.ofResponseProcessor(response -> {
            MediaType contentType = response.headers().contentType().orElse(null);
            if (contentType == null || contentType.getCharset() != null) {
                return Mono.just(response);
            }
            return Mono.just(response.mutate()
                    .headers(headers -> headers.set(HttpHeaders.CONTENT_TYPE, contentType + ";charset=UTF-8"))
                    .build());
        });
    }
}
