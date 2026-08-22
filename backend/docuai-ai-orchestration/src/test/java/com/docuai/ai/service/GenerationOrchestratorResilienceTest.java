package com.docuai.ai.service;

import com.docuai.ai.dto.Chunk;
import com.docuai.ai.dto.GenerationRequest;
import com.docuai.ai.dto.GenerationResult;
import com.docuai.ai.enums.AiProvider;
import com.docuai.ai.exception.AiProviderException;
import com.docuai.ai.port.AiProviderPort;
import com.docuai.ai.security.ApiKeyCipherService;
import com.docuai.core.model.AiModelConfig;
import com.docuai.core.repository.AiModelConfigRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Vérifie que {@code @Retry}/{@code @CircuitBreaker} sur
 * {@link GenerationOrchestrator#generate} sont réellement actifs (proxy
 * Spring AOP, pas d'auto-invocation) plutôt que la configuration
 * {@code resilience4j.*} orpheline d'avant cette étape — un vrai contexte
 * Spring est nécessaire ici, un test Mockito pur ne verrait jamais ce
 * câblage puisque les annotations ne sont interceptées qu'à travers un
 * proxy.
 */
@SpringBootTest(classes = GenerationOrchestratorResilienceTest.TestApp.class)
@TestPropertySource(properties = {
        "resilience4j.retry.instances.ai-provider.max-attempts=3",
        "resilience4j.retry.instances.ai-provider.wait-duration=5ms",
        "resilience4j.circuitbreaker.instances.ai-provider.sliding-window-size=10",
        "resilience4j.circuitbreaker.instances.ai-provider.minimum-number-of-calls=10",
        "resilience4j.circuitbreaker.instances.ai-provider.failure-rate-threshold=90"
})
class GenerationOrchestratorResilienceTest {

    /** Contexte minimal : autoconfiguration Resilience4j/AOP réelle, sans scan ni datasource (docuai-core amène spring-data-jpa transitivement). */
    @SpringBootApplication(scanBasePackages = "com.docuai.ai.service.__none__")
    @EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class, HibernateJpaAutoConfiguration.class, JpaRepositoriesAutoConfiguration.class})
    static class TestApp {
        @Bean
        AiProviderFactory providerFactory() {
            return mock(AiProviderFactory.class);
        }

        @Bean
        AiModelConfigRepository aiModelConfigRepository() {
            return mock(AiModelConfigRepository.class);
        }

        @Bean
        ApiKeyCipherService apiKeyCipherService() {
            ApiKeyCipherService service = mock(ApiKeyCipherService.class);
            when(service.isConfigured()).thenReturn(false);
            return service;
        }

        @Bean
        GenerationOrchestrator generationOrchestrator(AiProviderFactory factory, AiModelConfigRepository repository,
                                                       ApiKeyCipherService apiKeyCipherService) {
            return new GenerationOrchestrator(factory, repository, apiKeyCipherService);
        }
    }

    @Autowired
    private GenerationOrchestrator orchestrator;
    @Autowired
    private AiProviderFactory providerFactory;
    @Autowired
    private AiModelConfigRepository aiModelConfigRepository;

    @BeforeEach
    void resetMocks() {
        reset(providerFactory, aiModelConfigRepository);
        AiModelConfig defaultConfig = AiModelConfig.builder().fournisseur("MISTRAL").nomModele("mistral-small-latest").build();
        when(aiModelConfigRepository.findByEstDefautTrueAndActifTrue()).thenReturn(Optional.of(defaultConfig));
        when(providerFactory.isAvailable(AiProvider.MISTRAL)).thenReturn(true);
    }

    @Test
    void transientFailure_isRetried_thenSucceeds() {
        AiProviderPort adapter = mock(AiProviderPort.class);
        when(providerFactory.resolve(AiProvider.MISTRAL)).thenReturn(adapter);
        AtomicInteger attempts = new AtomicInteger();
        when(adapter.generate(any())).thenAnswer(invocation -> {
            if (attempts.getAndIncrement() == 0) {
                throw new AiProviderException("Timeout réseau simulé.");
            }
            return GenerationResult.builder().content("ok").provider("MISTRAL").build();
        });

        GenerationResult result = orchestrator.generate(GenerationRequest.builder().userPrompt("bonjour").build());

        assertThat(result.getContent()).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    void persistentFailure_exhaustsRetries_thenFallsBackCleanly() {
        AiProviderPort adapter = mock(AiProviderPort.class);
        when(providerFactory.resolve(AiProvider.MISTRAL)).thenReturn(adapter);
        when(adapter.generate(any())).thenThrow(new AiProviderException("Fournisseur indisponible."));
        GenerationRequest request = GenerationRequest.builder().userPrompt("bonjour").build();

        assertThatThrownBy(() -> orchestrator.generate(request))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("momentanément indisponible");

        verify(adapter, times(3)).generate(any());
    }

    /**
     * Sémantique réactive différente du cas bloquant : {@code adapter.streamGenerate(...)}
     * n'est appelée QU'UNE FOIS (le point de jonction AOP n'est invoqué qu'à
     * la construction du Flux) — les tentatives suivantes sont des
     * RE-SOUSCRIPTIONS à ce même Flux, pas de nouveaux appels de méthode.
     * {@code Flux.defer(...)} est indispensable ici pour simuler un flux
     * "froid" qui réévalue son comportement à chaque souscription (comme le
     * ferait un vrai appel WebClient) — sans lui, un unique {@code Flux.error(...)}
     * échouerait identiquement à chaque re-souscription.
     */
    @Test
    void streamTransientFailure_isRetried_thenSucceeds() {
        AiProviderPort adapter = mock(AiProviderPort.class);
        when(providerFactory.resolve(AiProvider.MISTRAL)).thenReturn(adapter);
        AtomicInteger subscriptions = new AtomicInteger();
        when(adapter.streamGenerate(any())).thenReturn(Flux.defer(() -> {
            if (subscriptions.getAndIncrement() == 0) {
                return Flux.error(new AiProviderException("Coupure réseau simulée."));
            }
            return Flux.just(Chunk.builder().content("ok").last(true).build());
        }));

        Flux<Chunk> result = orchestrator.streamGenerate(GenerationRequest.builder().userPrompt("bonjour").build());

        StepVerifier.create(result)
                .expectNextMatches(c -> "ok".equals(c.getContent()))
                .verifyComplete();
        assertThat(subscriptions.get()).isEqualTo(2);
        verify(adapter, times(1)).streamGenerate(any());
    }

    @Test
    void streamPersistentFailure_exhaustsRetries_thenFallsBackCleanly() {
        AiProviderPort adapter = mock(AiProviderPort.class);
        when(providerFactory.resolve(AiProvider.MISTRAL)).thenReturn(adapter);
        AtomicInteger subscriptions = new AtomicInteger();
        when(adapter.streamGenerate(any())).thenReturn(Flux.defer(() -> {
            subscriptions.incrementAndGet();
            return Flux.error(new AiProviderException("Fournisseur indisponible."));
        }));

        Flux<Chunk> result = orchestrator.streamGenerate(GenerationRequest.builder().userPrompt("bonjour").build());

        StepVerifier.create(result)
                .expectErrorMatches(e -> e instanceof AiProviderException && e.getMessage().contains("momentanément indisponible"))
                .verify();

        assertThat(subscriptions.get()).isEqualTo(3);
        verify(adapter, times(1)).streamGenerate(any());
    }
}
