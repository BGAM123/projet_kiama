package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Correspond exactement au type frontend {@code GeneratedDocument}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeneratedDocumentDTO {
    private UUID id;
    private UUID conversationId;
    private UUID documentTypeId;
    private UUID userId;
    private String status;
    private String language;
    private String tone;
    private String targetLength;
    private String contentPivot;
    private String content;
    private List<GenerationSectionDTO> sections;
    private String createdAt;
    private String updatedAt;
}
