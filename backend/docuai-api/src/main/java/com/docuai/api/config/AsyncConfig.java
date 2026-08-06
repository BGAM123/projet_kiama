package com.docuai.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Active {@code @Async} — utilisé par
 * {@link com.docuai.api.event.UserWelcomeEmailListener} pour envoyer l'e-mail
 * de bienvenue sans bloquer la requête HTTP {@code POST /api/v1/users} sur
 * l'appel SMTP. Pool dédié et volontairement petit : l'envoi d'e-mail n'est
 * pas un chemin à haut débit dans cette application.
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties({GenerationProperties.class, ConversationProperties.class})
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("docuai-async-");
        executor.initialize();
        return executor;
    }

    /**
     * Pool dédié au travail de génération (Bloc 6) : le contrôleur retourne
     * immédiatement un {@code SseEmitter}, la séquence d'appels IA
     * (un par section, potentiellement plusieurs dizaines de secondes au
     * total) tourne sur ce pool plutôt que sur le thread de requête HTTP.
     * Séparé de {@code taskExecutor} (e-mails) pour qu'un pic de générations
     * ne retarde jamais l'envoi des e-mails transactionnels, et inversement.
     */
    @Bean(name = "generationExecutor")
    public Executor generationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("docuai-generation-");
        executor.initialize();
        return executor;
    }
}
