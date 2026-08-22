package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiModelConfigDTO {
    private UUID id;
    private String provider;
    private String modelName;
    private String apiKeyRef;
    /** true si une clé API réelle est stockée (chiffrée) pour ce fournisseur — jamais la clé elle-même. */
    private Boolean hasStoredApiKey;
    /** Aperçu masqué ("•••• ab12") si {@code hasStoredApiKey}, sinon null — jamais la clé en clair. */
    private String apiKeyPreview;
    private Boolean isDefault;
    private Boolean active;
}
