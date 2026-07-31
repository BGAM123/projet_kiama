package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.UUID;

/** Correspond exactement au type frontend {@code ReferenceDocument}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ReferenceDocumentDTO {
    private UUID id;
    private UUID conversationId;
    private String fileName;
    private String storagePath;
    private String importedAt;
}
