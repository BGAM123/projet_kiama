package com.docuai.api.service;

import com.docuai.core.model.StructureNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Traduit le squelette d'un Document Type en document HTML prêt à être ouvert
 * dans l'éditeur type Word : c'est ce que l'utilisateur voit dès la création du
 * document, et ce qu'il modifie ensuite librement avant export.
 * <p>
 * Simple façade de {@link DocumentContentHtmlBuilder} sans aucun contenu
 * généré (correspondance vide) — même rendu, même règles de mise en forme, que
 * le document parte d'un squelette vierge ({@code DocumentService#create}) ou
 * du contenu produit par l'IA ({@code DocumentContentGenerationService}).
 */
@Component
public class DocumentSkeletonHtmlBuilder {

    private final DocumentContentHtmlBuilder documentContentHtmlBuilder;

    public DocumentSkeletonHtmlBuilder(DocumentContentHtmlBuilder documentContentHtmlBuilder) {
        this.documentContentHtmlBuilder = documentContentHtmlBuilder;
    }

    /**
     * @param tree arbre {@code document_structure.arbre_json} du Document Type
     * @return document HTML complet, jamais {@code null} — un squelette vide
     *         donne un paragraphe vide, pour que l'éditeur ait toujours un
     *         point d'insertion.
     */
    public String build(List<StructureNode> tree) {
        return documentContentHtmlBuilder.build(tree, Map.of());
    }
}
