package com.docuai.api.service;

import com.docuai.ai.dto.GeneratedSectionContent;
import com.docuai.core.model.StructureNode;
import com.docuai.core.model.TableColumnDef;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Un nœud sans correspondance dans la réponse IA doit retomber exactement sur
 * le rendu du squelette vide — c'est ce qui rend une génération partielle
 * (l'IA a oublié une section) inoffensive plutôt que de produire un document
 * cassé.
 */
class DocumentContentHtmlBuilderTest {

    private final DocumentContentHtmlBuilder builder = new DocumentContentHtmlBuilder();

    @Test
    void build_injectsGeneratedParagraph_intoAPlaceholderNode() {
        StructureNode placeholder = StructureNode.builder().id("n1").type("paragraph_placeholder").label("Contexte").build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1").content("Un paragraphe rédigé par l'IA.").build();

        String html = builder.build(List.of(placeholder), Map.of("n1", content));

        assertThat(html).isEqualTo("<p>Un paragraphe rédigé par l'IA.</p>");
    }

    /** Un texte généré sur plusieurs paragraphes (séparés par une ligne blanche) devient plusieurs `<p>`, pas un bloc unique avec des sauts de ligne internes. */
    @Test
    void build_splitsMultiParagraphContent_intoSeparateParagraphTags() {
        StructureNode placeholder = StructureNode.builder().id("n1").type("paragraph_placeholder").label("Contexte").build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1").content("Premier paragraphe.\n\nSecond paragraphe.").build();

        String html = builder.build(List.of(placeholder), Map.of("n1", content));

        assertThat(html).isEqualTo("<p>Premier paragraphe.</p><p>Second paragraphe.</p>");
    }

    /** Sans correspondance dans la réponse IA (section oubliée, ou génération non demandée) : même paragraphe vide que le squelette. */
    @Test
    void build_fallsBackToAnEmptyParagraph_whenNoContentWasGenerated() {
        StructureNode placeholder = StructureNode.builder().id("n1").type("paragraph_placeholder").label("Contexte").build();

        assertThat(builder.build(List.of(placeholder), Map.of())).isEqualTo("<p></p>");
    }

    /** Une section "paragraph" (extraction déterministe d'un fichier importé) porte déjà son texte réel dans le label : jamais écrasée par une génération, même si l'IA a répondu pour son id. */
    @Test
    void build_neverOverwritesAnAlreadyExtractedParagraph() {
        StructureNode extracted = StructureNode.builder().id("n1").type("paragraph").label("Texte déjà extrait du fichier importé.").build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1").content("Texte halluciné par l'IA.").build();

        String html = builder.build(List.of(extracted), Map.of("n1", content));

        assertThat(html).isEqualTo("<p>Texte déjà extrait du fichier importé.</p>");
    }

    @Test
    void build_fillsATable_withGeneratedRows_boundedToTheExpectedColumnCount() {
        StructureNode table = StructureNode.builder().id("n1").type("table").label("Résultats")
                .tableColumns(List.of(TableColumnDef.builder().name("Indicateur").build(), TableColumnDef.builder().name("Valeur").build()))
                .suggestedRowCount(2).build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1")
                .rows(List.of(List.of("CA", "420k", "colonne en trop"), List.of("Marge"))).build();

        String html = builder.build(List.of(table), Map.of("n1", content));

        assertThat(html)
                .contains("<th><p>Indicateur</p></th><th><p>Valeur</p></th>")
                .contains("<td><p>CA</p></td><td><p>420k</p></td>")
                // Ligne incomplète : la colonne manquante devient une cellule vide, pas une erreur.
                .contains("<td><p>Marge</p></td><td><p></p></td>")
                .doesNotContain("colonne en trop");
    }

    /** Moins de lignes générées que prévu par le plan : les lignes manquantes restent vides plutôt que de réduire le tableau. */
    @Test
    void build_padsMissingTableRows_whenGenerationReturnsFewerRowsThanExpected() {
        StructureNode table = StructureNode.builder().id("n1").type("table").label("Résultats")
                .columns(List.of("Indicateur", "Valeur")).suggestedRowCount(3).build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1").rows(List.of(List.of("CA", "420k"))).build();

        String html = builder.build(List.of(table), Map.of("n1", content));

        assertThat(html.split("<tr>", -1)).hasSize(5); // préfixe + en-tête + 3 lignes
        assertThat(html).contains("<td><p>CA</p></td><td><p>420k</p></td>")
                .contains("<td><p></p></td><td><p></p></td>"); // lignes 2 et 3, sans contenu généré
    }

    @Test
    void build_injectsGeneratedContent_intoALeafHeading() {
        StructureNode leaf = StructureNode.builder().id("n1").type("heading").level(2).label("Conclusion").build();
        GeneratedSectionContent content = GeneratedSectionContent.builder().id("n1").content("En résumé, tout va bien.").build();

        String html = builder.build(List.of(leaf), Map.of("n1", content));

        assertThat(html).isEqualTo("<h2>Conclusion</h2><p>En résumé, tout va bien.</p>");
    }

    /** Un titre qui porte des sous-sections reste un simple repère de plan : aucun contenu ne lui est jamais injecté, même généré par erreur pour son id. */
    @Test
    void build_neverInjectsContent_intoAStructuralHeadingWithChildren() {
        StructureNode child = StructureNode.builder().id("n2").type("paragraph_placeholder").label("Détail").build();
        StructureNode parent = StructureNode.builder().id("n1").type("heading").level(1).label("Introduction").children(List.of(child)).build();
        GeneratedSectionContent misdirected = GeneratedSectionContent.builder().id("n1").content("Ne doit jamais apparaître.").build();

        String html = builder.build(List.of(parent), Map.of("n1", misdirected));

        assertThat(html).isEqualTo("<h1>Introduction</h1><p></p>");
    }

    @Test
    void build_matchesTheSkeletonRendering_whenNothingWasGenerated() {
        StructureNode cover = StructureNode.builder().id("cover").type("cover").label("Rapport").build();
        StructureNode heading = StructureNode.builder().id("n1").type("heading").level(1).label("Introduction").build();

        assertThat(builder.build(List.of(cover, heading), Map.of()))
                .isEqualTo("<h1 style=\"text-align: center\">Rapport</h1><div data-page-break></div><h1>Introduction</h1><p></p>");
    }
}
