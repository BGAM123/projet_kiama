package com.docuai.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Corps de {@code POST /documents/{id}/improve-selection} : le passage sélectionné dans l'éditeur, en texte brut. */
@Data
public class ImproveTextRequest {

    @NotBlank(message = "Sélectionnez le texte à améliorer.")
    private String text;
}
