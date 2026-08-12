package com.docuai.ai.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * Suggestion de reformulation d'une section, telle que
 * {@link com.docuai.ai.service.SectionImprovementResponseParser} l'extrait de
 * la réponse brute du fournisseur IA : le texte amélioré et la confiance que le
 * modèle déclare lui-même sur ce texte (0-100).
 *
 * <p>{@code confidence} est {@code null} quand le modèle n'a pas respecté le
 * format demandé (réponse en texte brut) — le contenu reste alors exploitable,
 * seule la pastille de confiance est absente côté éditeur. Ce score est
 * déclaratif, pas une probabilité calibrée : il alimente les seuils de
 * relecture §3.4, rien d'autre.</p>
 */
@Getter
@Builder
public class SectionImprovement {
    private final String content;
    private final Double confidence;
}
