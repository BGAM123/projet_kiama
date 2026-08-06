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
    private Boolean isDefault;
    private Boolean active;
}
