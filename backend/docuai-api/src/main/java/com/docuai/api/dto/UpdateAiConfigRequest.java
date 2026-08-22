package com.docuai.api.dto;

import lombok.Data;

@Data
public class UpdateAiConfigRequest {
    private String modelName;
    private String apiKeyRef;
    /** Nouvelle clé API en clair, chiffrée avant stockage — null/vide = ne pas modifier la clé déjà enregistrée. */
    private String apiKey;
    private Boolean isDefault;
    private Boolean active;
}
