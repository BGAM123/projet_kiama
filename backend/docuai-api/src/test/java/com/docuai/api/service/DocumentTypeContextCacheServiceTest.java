package com.docuai.api.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentTypeContextCacheServiceTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private DocumentTypeContextCacheService cacheService;
    private final UUID documentTypeId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        cacheService = new DocumentTypeContextCacheService(redisTemplate);
    }

    @Test
    void get_returnsEmpty_whenNotCached() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("docuai:doctype:structure-block:" + documentTypeId)).thenReturn(null);

        assertThat(cacheService.get(documentTypeId)).isEmpty();
    }

    @Test
    void put_thenGet_roundTrips() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        cacheService.put(documentTypeId, "bloc de structure");

        verify(valueOperations).set(eq("docuai:doctype:structure-block:" + documentTypeId), eq("bloc de structure"), any(Duration.class));

        when(valueOperations.get("docuai:doctype:structure-block:" + documentTypeId)).thenReturn("bloc de structure");
        assertThat(cacheService.get(documentTypeId)).contains("bloc de structure");
    }

    @Test
    void evict_deletesTheKey() {
        cacheService.evict(documentTypeId);

        verify(redisTemplate).delete("docuai:doctype:structure-block:" + documentTypeId);
    }
}
