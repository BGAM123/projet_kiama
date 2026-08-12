package com.docuai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Corps de {@code POST /document-types/generate} : description en langage naturel du type de document souhaité. */
@Data
public class GenerateDocumentTypeRequest {
    @NotBlank
    private String description;
    @NotBlank
    private String name;
    @NotNull
    private UUID categoryId;
}
