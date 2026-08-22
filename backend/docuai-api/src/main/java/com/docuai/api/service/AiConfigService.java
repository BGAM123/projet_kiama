package com.docuai.api.service;

import com.docuai.ai.security.ApiKeyCipherService;
import com.docuai.api.dto.AiModelConfigDTO;
import com.docuai.api.dto.UpdateAiConfigRequest;
import com.docuai.api.exception.BusinessException;
import com.docuai.api.exception.NotFoundException;
import com.docuai.api.mapper.AiModelConfigMapper;
import com.docuai.core.model.AiModelConfig;
import com.docuai.core.repository.AiModelConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Service applicatif : lecture/édition des configurations de fournisseurs IA (Bloc 8). */
@Service
public class AiConfigService {

    private final AiModelConfigRepository aiModelConfigRepository;
    private final AiModelConfigMapper aiModelConfigMapper;
    private final ApiKeyCipherService apiKeyCipherService;

    public AiConfigService(AiModelConfigRepository aiModelConfigRepository, AiModelConfigMapper aiModelConfigMapper,
                            ApiKeyCipherService apiKeyCipherService) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.aiModelConfigMapper = aiModelConfigMapper;
        this.apiKeyCipherService = apiKeyCipherService;
    }

    @Transactional(readOnly = true)
    public List<AiModelConfigDTO> listAll() {
        return aiModelConfigMapper.toDtoList(aiModelConfigRepository.findAll());
    }

    @Transactional
    public AiModelConfigDTO update(UUID id, UpdateAiConfigRequest request) {
        AiModelConfig config = findEntity(id);

        if (request.getModelName() != null) {
            config.setNomModele(request.getModelName());
        }
        if (request.getApiKeyRef() != null) {
            config.setReferenceCleApi(request.getApiKeyRef());
        }
        if (request.getApiKey() != null && !request.getApiKey().isBlank()) {
            if (!apiKeyCipherService.isConfigured()) {
                throw BusinessException.badRequest("AI_CREDENTIALS_ENCRYPTION_NOT_CONFIGURED",
                        "Impossible d'enregistrer une clé API : docuai.ai.credentials-encryption-key n'est pas configurée côté serveur.");
            }
            String plainKey = request.getApiKey().trim();
            config.setCleApiChiffree(apiKeyCipherService.encrypt(plainKey));
            config.setCleApiApercu(lastChars(plainKey, 4));
        }
        if (request.getActive() != null) {
            config.setActif(request.getActive());
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            // saveAndFlush (pas save) : "config" est déjà géré par le contexte de
            // persistance depuis findEntity() ci-dessus, donc sans flush explicite
            // ici, Hibernate écrirait son UPDATE (est_defaut=TRUE) avant celui-ci
            // (est_defaut=FALSE) au commit — deux lignes à TRUE en même temps,
            // ce qui viole l'index unique partiel ux_ai_model_config_defaut
            // (un seul fournisseur par défaut à la fois, cf. V1__init_schema.sql).
            aiModelConfigRepository.findAll().forEach(other -> {
                if (!other.getId().equals(id) && Boolean.TRUE.equals(other.getEstDefaut())) {
                    other.setEstDefaut(false);
                    aiModelConfigRepository.saveAndFlush(other);
                }
            });
            config.setEstDefaut(true);
        } else if (request.getIsDefault() != null) {
            config.setEstDefaut(false);
        }

        return aiModelConfigMapper.toDto(aiModelConfigRepository.save(config));
    }

    private AiModelConfig findEntity(UUID id) {
        return aiModelConfigRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("AI_CONFIG_NOT_FOUND", "Configuration IA introuvable."));
    }

    private String lastChars(String value, int count) {
        return value.length() <= count ? value : value.substring(value.length() - count);
    }
}
