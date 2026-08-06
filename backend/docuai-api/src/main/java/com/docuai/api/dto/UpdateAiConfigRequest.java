package com.docuai.api.dto;

import lombok.Data;

@Data
public class UpdateAiConfigRequest {
    private String modelName;
    private String apiKeyRef;
    private Boolean isDefault;
    private Boolean active;
}
