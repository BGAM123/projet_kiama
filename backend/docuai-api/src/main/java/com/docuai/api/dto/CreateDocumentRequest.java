package com.docuai.api.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Corps de {@code POST /documents} : crée le document et ses sections vides à partir du squelette du Document Type. */
@Data
public class CreateDocumentRequest {
    @NotNull
    private UUID documentTypeId;
}
