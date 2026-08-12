package com.docuai.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;
import java.util.UUID;

/** Correspond exactement au type frontend {@code DocumentStructure}. */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DocumentStructureDTO {
    private UUID id;
    private UUID documentTypeId;
    private List<StructureNodeDTO> tree;
    private Boolean hasToc;
    private String headerText;
    private String footerText;
    private String source;
}
