package com.docuai.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Corps de {@code POST /documents/generate} : point d'entrée unique de la
 * rédaction à partir d'un Document Type (a remplacé {@link CreateDocumentRequest},
 * qui ne produisait qu'un squelette vide), à côté de l'import de fichier — un
 * Document Type déjà structuré, optionnellement rempli par l'IA à partir
 * d'une description en langage naturel de ce que l'utilisateur veut obtenir.
 * {@code description} vide ou absente : {@code DocumentService#generateContent}
 * n'appelle pas l'IA et produit un squelette vide, comme l'ancien
 * {@link CreateDocumentRequest}.
 */
@Data
public class GenerateDocumentRequest {
    @NotNull
    private UUID documentTypeId;
    @NotBlank
    private String name;
    @Size(min = 10, message = "Décrivez ce que vous souhaitez obtenir (10 caractères minimum), ou laissez vide pour un squelette vierge.")
    private String description;
}
