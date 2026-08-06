package com.docuai.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Cache Redis du bloc de structure attendue rendu par {@code PromptBuilder}
 * (Markdown listant titres/niveaux/tableaux d'un Document Type) — recalculé
 * sinon à chaque message envoyé dans une conversation
 * ({@link ConversationService#generateAssistantReply}) alors qu'il ne change
 * qu'à l'extraction ou l'édition manuelle de la structure. Même style que
 * {@code TokenBlacklistService} (docuai-security) : StringRedisTemplate
 * direct plutôt que l'abstraction {@code @Cacheable}, pour une éviction
 * explicite et prévisible au moment où la structure est modifiée.
 */
@Service
public class DocumentTypeContextCacheService {

    private static final String KEY_PREFIX = "docuai:doctype:structure-block:";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redisTemplate;

    public DocumentTypeContextCacheService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<String> get(UUID documentTypeId) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(KEY_PREFIX + documentTypeId));
    }

    public void put(UUID documentTypeId, String structureBlock) {
        redisTemplate.opsForValue().set(KEY_PREFIX + documentTypeId, structureBlock, TTL);
    }

    public void evict(UUID documentTypeId) {
        redisTemplate.delete(KEY_PREFIX + documentTypeId);
    }
}
