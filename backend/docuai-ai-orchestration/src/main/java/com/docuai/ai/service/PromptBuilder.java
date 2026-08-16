package com.docuai.ai.service;

import com.docuai.core.model.DocumentChunk;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
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
     * Prompt système du flux "Générer avec l'IA" sur un document déjà créé à
     * partir d'un Document Type (section "Nouveau document" de la refonte) —
     * distinct du flux squelette ({@link #buildSkeletonSystemPrompt}), qui
     * produit un plan sans aucun contenu : ici le plan est déjà fixé (celui du
     * Document Type choisi), le LLM ne fait que le remplir.
     * <p>
     * Chaque section à rédiger est identifiée par l'{@code id} de son {@link
     * StructureNode} dans le plan rendu ci-dessous — c'est ce qui permet à
     * {@link DocumentContentResponseParser} de reporter le contenu généré sur
     * le bon nœud sans dépendre de l'ordre de la réponse, et à l'appelant de
     * tolérer une réponse partielle (un {@code id} manquant reste simplement
     * vide, plutôt que de faire échouer tout le document pour une seule
     * section oubliée par le modèle).
     */
    public String buildDocumentContentSystemPrompt(String documentTypeName, String userDescription,
                                                     List<StructureNode> tree, String language, String tone) {
        StringBuilder outline = new StringBuilder();
        renderContentOutline(tree, outline, 0);

        return """
                Tu es un rédacteur professionnel qui produit le CONTENU rédigé d'un document, à partir de son plan déjà fixé et de la description de ce que l'utilisateur souhaite obtenir.

                Type de document : %s
                Réponds exclusivement dans la langue suivante : %s. Adopte un ton %s.

                Plan du document — respecte-le tel quel, ne modifie ni n'ajoute de section, les titres servent uniquement de contexte :
                %s
                Règles strictes :
                - Ne rédige QUE les sections marquées "[À rédiger]" ou "[Tableau à remplir]" ci-dessus, chacune identifiée par son id entre crochets.
                - Le contenu doit être cohérent avec la description de l'utilisateur et avec les sections voisines, jamais un texte générique interchangeable.
                - Section paragraphe : un texte fluide (plusieurs phrases si pertinent), sans reprendre le titre, dans le champ "content".
                - Section tableau : exactement le nombre de lignes indiqué, une valeur par colonne dans l'ordre donné, dans le champ "rows" (tableau de tableaux de chaînes).
                - Réponds UNIQUEMENT avec un objet JSON strict, sans texte autour, sans balise Markdown de code, conforme exactement à ce schéma :

                {
                  "sections": [
                    { "id": "identifiant-de-la-section", "content": "texte du paragraphe" },
                    { "id": "identifiant-de-la-section-tableau", "rows": [["valeur1", "valeur2"], ["valeur1", "valeur2"]] }
                  ]
                }

                Description de ce que l'utilisateur souhaite obtenir :
                %s
                """.formatted(
                documentTypeName == null ? "Document" : documentTypeName,
                language == null ? "FR" : language,
                tone == null ? "neutre" : tone.toLowerCase(),
                outline,
                userDescription);
    }

    /** Colonnes attendues d'un nœud tableau — même repli que {@code DocumentSkeletonHtmlBuilder.headersOf} : tableColumns (flux IA) sinon columns (extraction déterministe) sinon un défaut générique. */
    private List<String> columnsOf(StructureNode node) {
        if (node.getTableColumns() != null && !node.getTableColumns().isEmpty()) {
            return node.getTableColumns().stream().map(TableColumnDef::getName).toList();
        }
        if (node.getColumns() != null && !node.getColumns().isEmpty()) {
            return node.getColumns();
        }
        return List.of("Colonne 1", "Colonne 2");
    }

    /**
     * Rendu textuel du plan pour le prompt de génération de contenu : les
     * sections à rédiger portent leur {@code id} et une consigne explicite
     * ("[À rédiger]"/"[Tableau à remplir]"), les titres purement structurels
     * (qui portent des sous-sections) ne servent que de repères de contexte.
     * Une section "heading" sans enfant est elle-même une section à rédiger —
     * c'est un titre feuille, exactement comme dans {@code
     * DocumentSkeletonHtmlBuilder.appendHeading}.
     */
    private void renderContentOutline(List<StructureNode> nodes, StringBuilder sb, int depth) {
        if (nodes == null) {
            return;
        }
        String indent = "  ".repeat(depth);
        for (StructureNode node : nodes) {
            String type = node.getType() == null ? "" : node.getType();
            switch (type) {
                case "cover" -> {
                    // Page de garde : hors contenu à générer.
                }
                case "table" -> {
                    int rows = node.getSuggestedRowCount() == null ? 3 : node.getSuggestedRowCount();
                    sb.append(indent).append("- [").append(node.getId()).append("] Tableau « ").append(node.getLabel())
                            .append(" » — colonnes : ").append(String.join(", ", columnsOf(node)))
                            .append(" — [Tableau à remplir : ").append(rows).append(" lignes]\n");
                }
                case "list" -> sb.append(indent).append("- [").append(node.getId()).append("] ")
                        .append(node.getLabel()).append(" — [À rédiger]\n");
                case "paragraph_placeholder", "paragraph" -> sb.append(indent).append("- [").append(node.getId())
                        .append("] ").append(node.getLabel()).append(" — [À rédiger]\n");
                default -> {
                    int level = Math.max(1, Math.min(node.getLevel() == null ? 1 : node.getLevel(), 6));
                    boolean leaf = node.getChildren() == null || node.getChildren().isEmpty();
                    sb.append(indent).append("#".repeat(level)).append(' ').append(node.getLabel());
                    if (leaf) {
                        sb.append(" — [").append(node.getId()).append("] [À rédiger]");
                    }
                    sb.append('\n');
                }
            }
            renderContentOutline(node.getChildren(), sb, depth + 1);
        }
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
