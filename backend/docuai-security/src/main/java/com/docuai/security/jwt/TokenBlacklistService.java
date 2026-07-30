package com.docuai.security.jwt;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Liste noire des refresh tokens révoqués (déconnexion), stockée dans Redis
 * avec une expiration alignée sur la durée de vie restante du token —
 * cf. prompt maître section 8 : "le jeton de rafraîchissement doit être
 * révocable côté serveur via une liste noire Redis".
 */
@Service
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "docuai:jwt:blacklist:";

    private final StringRedisTemplate redisTemplate;

    public TokenBlacklistService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void blacklist(String token, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return; // Déjà expiré, inutile de le stocker.
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + token, "revoked", ttl);
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + token));
    }
}
