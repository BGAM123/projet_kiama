package com.docuai.api.service;

import com.docuai.api.dto.AiModelConfigDTO;
import com.docuai.api.dto.UpdateAiConfigRequest;
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

    public AiConfigService(AiModelConfigRepository aiModelConfigRepository, AiModelConfigMapper aiModelConfigMapper) {
        this.aiModelConfigRepository = aiModelConfigRepository;
        this.aiModelConfigMapper = aiModelConfigMapper;
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
        if (request.getActive() != null) {
            config.setActif(request.getActive());
        }
        if (Boolean.TRUE.equals(request.getIsDefault())) {
            aiModelConfigRepository.findAll().forEach(other -> {
                if (!other.getId().equals(id) && Boolean.TRUE.equals(other.getEstDefaut())) {
                    other.setEstDefaut(false);
                    aiModelConfigRepository.save(other);
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
}
