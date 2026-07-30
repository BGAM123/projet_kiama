package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/** Correspond exactement au type frontend {@code DocumentType} (frontend/types/index.ts). */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentTypeDTO {
    private UUID id;
    private String name;
    private String description;
    private UUID categoryId;
    private String status;
    private Integer version;
    private String createdAt;
}
