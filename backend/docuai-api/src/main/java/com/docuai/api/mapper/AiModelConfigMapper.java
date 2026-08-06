package com.docuai.api.mapper;

import com.docuai.api.dto.AiModelConfigDTO;
import com.docuai.core.model.AiModelConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AiModelConfigMapper {

    @Mapping(target = "provider", source = "fournisseur")
    @Mapping(target = "modelName", source = "nomModele")
    @Mapping(target = "apiKeyRef", source = "referenceCleApi")
    @Mapping(target = "isDefault", source = "estDefaut")
    @Mapping(target = "active", source = "actif")
    AiModelConfigDTO toDto(AiModelConfig config);

    List<AiModelConfigDTO> toDtoList(List<AiModelConfig> configs);
}
