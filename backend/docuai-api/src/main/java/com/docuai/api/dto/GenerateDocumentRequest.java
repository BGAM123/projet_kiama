package com.docuai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Corps de {@code POST /documents/generate} : troisième point d'entrée de la
 * rédaction, à côté de {@link CreateDocumentRequest} (squelette vide) et de
 * l'import de fichier — un Document Type déjà structuré, rempli par l'IA à
 * partir d'une description en langage naturel de ce que l'utilisateur veut
 * obtenir.
 */
@Data
public class GenerateDocumentRequest {
    @NotNull
    private UUID documentTypeId;
    @NotBlank
    private String name;
    @NotBlank
    @Size(min = 10, message = "Décrivez ce que vous souhaitez obtenir (10 caractères minimum).")
    private String description;
}
