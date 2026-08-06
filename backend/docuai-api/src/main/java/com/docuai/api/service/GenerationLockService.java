package com.docuai.api.service;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Verrou Redis (SETNX + TTL) empêchant deux générations concurrentes sur le
 * même document ({@link GenerationStreamService#run}). Le jeton de
 * possession (valeur stockée sous la clé) permet une libération sûre : si le
 * TTL a expiré et qu'un autre run a déjà acquis le verrou entre-temps, on ne
 * supprime pas SA clé par erreur — comparaison + suppression atomique via un
 * script Lua (même principe que le pattern Redlock "check-and-delete").
 */
@Service
public class GenerationLockService {

    private static final String KEY_PREFIX = "docuai:generation:lock:";

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> releaseScript;

    public GenerationLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.releaseScript = new DefaultRedisScript<>(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
                Long.class);
    }

    /** Tente d'acquérir le verrou pour {@code documentId} ; renvoie le jeton de possession si acquis, vide sinon (génération déjà en cours). */
    public Optional<String> tryAcquire(UUID documentId, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key(documentId), token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(token) : Optional.empty();
    }

    /** Libère le verrou uniquement si {@code token} correspond toujours au détenteur courant. */
    public void release(UUID documentId, String token) {
        redisTemplate.execute(releaseScript, List.of(key(documentId)), token);
    }

    private String key(UUID documentId) {
        return KEY_PREFIX + documentId;
    }
}
