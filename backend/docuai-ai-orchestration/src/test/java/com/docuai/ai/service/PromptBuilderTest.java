package com.docuai.ai.service;

import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link PromptBuilder#buildDocumentContentSystemPrompt} est le contrat entre
 * le plan du Document Type et {@link DocumentContentResponseParser} : chaque
 * section à rédiger doit apparaître dans le prompt avec son id exact, sinon le
 * modèle ne peut pas répondre avec des ids que le parseur saura reporter sur
 * le bon nœud.
 */
class PromptBuilderTest {

    private final PromptBuilder promptBuilder = new PromptBuilder();

    private List<StructureNode> sampleTree() {
        StructureNode cover = StructureNode.builder().id("cover").type("cover").label("Rapport d'audit").build();
        StructureNode context = StructureNode.builder().id("n2").type("paragraph_placeholder").label("Contexte").build();
        StructureNode table = StructureNode.builder().id("n3").type("table").label("Résultats")
                .tableColumns(List.of(TableColumnDef.builder().name("Indicateur").build(), TableColumnDef.builder().name("Valeur").build()))
                .suggestedRowCount(2).build();
        StructureNode leafHeading = StructureNode.builder().id("n4").type("heading").level(2).label("Conclusion").build();
        StructureNode parentHeading = StructureNode.builder().id("n1").type("heading").level(1).label("Introduction")
                .children(List.of(context, table)).build();
        return List.of(cover, parentHeading, leafHeading);
    }

    @Test
    void buildDocumentContentSystemPrompt_listsEachContentSection_withItsId() {
        String prompt = promptBuilder.buildDocumentContentSystemPrompt(
                "Rapport d'audit", "Un audit de la sécurité informatique.", sampleTree(), "FR", "FORMEL");

        assertThat(prompt).contains("[n2]").contains("Contexte").contains("[À rédiger]");
        assertThat(prompt).contains("[n3]").contains("Résultats").contains("colonnes : Indicateur, Valeur")
                .contains("[Tableau à remplir : 2 lignes]");
        // Un titre feuille (sans enfant) est lui-même une section à rédiger.
        assertThat(prompt).contains("[n4]").contains("Conclusion");
    }

    @Test
    void buildDocumentContentSystemPrompt_doesNotMarkStructuralHeadings_asContentToWrite() {
        String prompt = promptBuilder.buildDocumentContentSystemPrompt(
                "Rapport d'audit", "Un audit.", sampleTree(), "FR", "NEUTRE");

        // "Introduction" (n1) porte des sous-sections : c'est un repère de plan, pas une cible de rédaction.
        assertThat(prompt).doesNotContain("[n1]");
    }

    @Test
    void buildDocumentContentSystemPrompt_excludesTheCoverNode() {
        String prompt = promptBuilder.buildDocumentContentSystemPrompt(
                "Rapport d'audit", "Un audit.", sampleTree(), "FR", "NEUTRE");

        assertThat(prompt).doesNotContain("[cover]");
    }

    @Test
    void buildDocumentContentSystemPrompt_includesTheUserDescription_andLanguageTone() {
        String prompt = promptBuilder.buildDocumentContentSystemPrompt(
                "Rapport d'audit", "Une description bien précise.", sampleTree(), "EN", "PERSUASIF");

        assertThat(prompt).contains("Une description bien précise.")
                .contains("Réponds exclusivement dans la langue suivante : EN")
                .contains("ton persuasif");
    }
}
