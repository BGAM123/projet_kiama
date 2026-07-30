package com.docuai.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** Corps de {@code PUT /document-types/{id}/structure} (correction manuelle de l'arbre). */
@Data
public class UpdateStructureRequest {
    @NotNull
    @Valid
    private List<StructureNodeDTO> tree;
}
