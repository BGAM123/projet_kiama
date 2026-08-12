package com.docuai.ai.service;

import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.StructureNode;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Construit les prompts envoyés au fournisseur IA (section 4.3/4.4) à partir
 * de la structure attendue du Document Type ciblé (contrainte de forme en
 * sortie, cf. {@link StructuralValidator}), des chunks de documents de
 * référence retenus par le RAG ({@code SimilaritySearchService}) et des
 * consignes utilisateur (langue, ton, longueur cible).
 */
@Service
public class PromptBuilder {

    public String buildSystemPrompt(List<StructureNode> expectedStructure, String language, String tone, String targetLength) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tu es un assistant de rédaction de documents professionnels. ");
        sb.append("Réponds exclusivement dans la langue suivante : ").append(language == null ? "FR" : language).append(". ");
        sb.append("Adopte un ton ").append(tone == null ? "neutre" : tone.toLowerCase()).append(". ");
        if (targetLength != null) {
            sb.append("Vise une longueur de contenu ").append(describeTargetLength(targetLength)).append(". ");
        }
        if (expectedStructure != null && !expectedStructure.isEmpty()) {
            sb.append("\n\nStructure attendue du document (respecte l'ordre et les niveaux de titres, format Markdown) :\n");
            sb.append(renderStructureBlock(expectedStructure));
        }
        return sb.toString();
    }

    /**
     * Prompt système du bouton "Améliorer avec l'IA" (édition manuelle
     * assistée, section 2 du cahier des charges de refonte) : reformule le
     * contenu déjà écrit par l'utilisateur (ton, clarté, orthographe/grammaire)
     * sans en changer le sens ni y ajouter d'information absente — la
     * suggestion produite reste distincte du contenu retenu tant que
     * l'utilisateur ne l'applique pas explicitement (jamais d'écrasement
     * automatique, cf. DocumentSectionService#applySuggestion).
     *
     * <p>La sortie attendue est un objet JSON portant, en plus du texte, la
     * confiance auto-déclarée du modèle sur sa reformulation. Cette confiance
     * alimente la pastille par section et le score global de l'éditeur (seuils
     * §3.4) : elle oriente la relecture, elle ne mesure rien statistiquement.
     * Un modèle qui ignorerait ce format ne casse rien —
     * {@link SectionImprovementResponseParser} retombe alors sur le texte brut,
     * sans score.</p>
     */
    public String buildSectionImprovementSystemPrompt(String language, String tone) {
        return "Tu es un assistant de relecture et d'amélioration rédactionnelle de documents professionnels. "
                + "Réponds exclusivement dans la langue suivante : " + (language == null ? "FR" : language) + ". "
                + "Améliore le texte fourni par l'utilisateur : ton " + (tone == null ? "neutre" : tone.toLowerCase())
                + ", clarté, orthographe et grammaire — sans changer le sens, sans ajouter d'information absente du texte original, sans raccourcir ni développer excessivement. "
                + "Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour, sans balise Markdown de code, conforme exactement à ce schéma :\n"
                + "{\n"
                + "  \"content\": \"le texte amélioré, sans préambule ni commentaire\",\n"
                + "  \"confidence\": 0 à 100\n"
                + "}\n"
                + "\"confidence\" est ta confiance dans la reformulation proposée : élevée (>= 76) si le texte d'origine est clair et que ton intervention est sûre, "
                + "moyenne (60-75) si le texte d'origine est ambigu ou incomplet, faible (< 60) si tu as dû deviner l'intention de l'auteur.";
    }

    public String buildUserPrompt(String userInstructions, List<DocumentChunk> referenceChunks) {
        if (referenceChunks == null || referenceChunks.isEmpty()) {
            return userInstructions;
        }
        String context = referenceChunks.stream()
                .map(DocumentChunk::getContenu)
                .collect(Collectors.joining("\n---\n"));
        return "Extraits de documents de référence à utiliser comme source :\n" + context
                + "\n\nInstructions :\n" + userInstructions;
    }

    private String describeTargetLength(String targetLength) {
        return switch (targetLength.toUpperCase()) {
            case "COURT" -> "courte (quelques paragraphes)";
            case "MOYEN" -> "moyenne (une à deux pages)";
            case "LONG" -> "longue (plusieurs pages)";
            case "EXTENSIF" -> "exhaustive (couvre tous les aspects en détail)";
            default -> "adaptée au contexte";
        };
    }

    /**
     * Prompt système du flux "décrire en texte -> squelette généré par IA"
     * (Document Type, section 1 du cahier des charges de refonte) — distinct
     * de {@link #buildSystemPrompt}, qui pilote la génération de *contenu*
     * pour un Document Type déjà structuré. Ici, le LLM ne doit produire
     * qu'un arbre JSON de titres/sous-titres/sous-sous-titres/tableaux,
     * jamais de texte rédigé — {@link SkeletonContentGuard} vérifie a
     * posteriori que la consigne a été respectée.
     */
    public String buildSkeletonSystemPrompt(String userDescription) {
        return """
                Tu es un assistant qui conçoit le PLAN (squelette structurel) d'un type de document professionnel, à partir d'une description en langage naturel.

                Règles strictes :
                - Génère UNIQUEMENT une structure de titres, sous-titres, sous-sous-titres et emplacements de tableaux.
                - N'écris JAMAIS de contenu rédigé, de phrase complète, de paragraphe d'exemple ou de texte explicatif dans un label de section.
                - Chaque "label" est un intitulé court (titre de section), jamais une phrase.
                - Réponds UNIQUEMENT avec un tableau JSON strict, sans texte autour, sans balise Markdown de code, conforme exactement à ce schéma :

                [
                  {
                    "id": "identifiant-court-unique",
                    "type": "heading" | "table" | "paragraph_placeholder",
                    "level": 1 | 2 | 3,               // uniquement pour type == "heading" (1 = titre, 2 = sous-titre, 3 = sous-sous-titre)
                    "label": "Intitulé court de la section",
                    "tableColumns": [ { "name": "Nom de colonne", "type": "text" } ],  // uniquement pour type == "table"
                    "suggestedRowCount": 3,           // uniquement pour type == "table", optionnel
                    "children": []                     // sous-sections imbriquées (même schéma), optionnel
                  }
                ]

                Description du type de document souhaité par l'utilisateur :
                """ + userDescription;
    }

    /**
     * Rendu textuel de l'arbre de structure (titres Markdown `#`..`###` par
     * niveau, tableaux signalés entre crochets) — extrait de
     * {@link #buildSystemPrompt} pour être réutilisable telle quelle par
     * {@code ConversationService} (Bloc 6, chat), qui construit son propre
     * préambule mais veut ancrer ses réponses sur la même structure attendue
     * que la génération.
     */
    public String renderStructureBlock(List<StructureNode> nodes) {
        StringBuilder sb = new StringBuilder();
        for (StructureNode node : nodes) {
            if ("heading".equals(node.getType())) {
                sb.append("#".repeat(node.getLevel() == null ? 1 : node.getLevel())).append(' ').append(node.getLabel()).append('\n');
            } else if ("table".equals(node.getType())) {
                sb.append("[Tableau : ").append(node.getLabel()).append("]\n");
            }
        }
        return sb.toString();
    }
}
