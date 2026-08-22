package com.docuai.api.mapper;

import com.docuai.api.dto.AiModelConfigDTO;
import com.docuai.core.model.AiModelConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "spring")
public interface AiModelConfigMapper {

    @Mapping(target = "provider", source = "fournisseur")
    @Mapping(target = "modelName", source = "nomModele")
    @Mapping(target = "apiKeyRef", source = "referenceCleApi")
    @Mapping(target = "hasStoredApiKey", source = "cleApiChiffree", qualifiedByName = "hasValue")
    @Mapping(target = "apiKeyPreview", source = "cleApiApercu", qualifiedByName = "formatPreview")
    @Mapping(target = "isDefault", source = "estDefaut")
    @Mapping(target = "active", source = "actif")
    AiModelConfigDTO toDto(AiModelConfig config);

    List<AiModelConfigDTO> toDtoList(List<AiModelConfig> configs);

    @Named("hasValue")
    default boolean hasValue(String value) {
        return value != null && !value.isBlank();
    }

    @Named("formatPreview")
    default String formatPreview(String apercu) {
        return (apercu == null || apercu.isBlank()) ? null : "•••• " + apercu;
    }
}
