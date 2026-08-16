package com.docuai.ai.service;

import com.docuai.ai.dto.GeneratedSectionContent;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Contrairement à {@link SectionImprovementResponseParser}, une réponse non
 * conforme est ici une vraie erreur (déclenche le retry côté appelant) : il
 * n'y a pas de repli en texte brut sensé pour un objet censé couvrir
 * plusieurs sections identifiées par id.
 */
class DocumentContentResponseParserTest {

    private final DocumentContentResponseParser parser = new DocumentContentResponseParser();

    @Test
    void parse_readsParagraphAndTableSections_fromStrictJson() {
        List<GeneratedSectionContent> sections = parser.parse("""
                {
                  "sections": [
                    { "id": "n2", "content": "Un paragraphe rédigé." },
                    { "id": "n3", "rows": [["CA", "420k"], ["Marge", "12%"]] }
                  ]
                }""");

        assertThat(sections).hasSize(2);
        assertThat(sections.get(0).getId()).isEqualTo("n2");
        assertThat(sections.get(0).getContent()).isEqualTo("Un paragraphe rédigé.");
        assertThat(sections.get(1).getRows()).containsExactly(List.of("CA", "420k"), List.of("Marge", "12%"));
    }

    @Test
    void parse_readsJson_wrappedInMarkdownCodeFence() {
        List<GeneratedSectionContent> sections = parser.parse("""
                ```json
                {"sections": [{"id": "n1", "content": "Texte."}]}
                ```""");

        assertThat(sections).singleElement().extracting(GeneratedSectionContent::getContent).isEqualTo("Texte.");
    }

    @Test
    void parse_throws_forBlankResponse() {
        assertThatThrownBy(() -> parser.parse("  "))
                .isInstanceOf(DocumentContentResponseParser.DocumentContentParseException.class);
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(DocumentContentResponseParser.DocumentContentParseException.class);
    }

    @Test
    void parse_throws_whenResponseIsNotJson() {
        assertThatThrownBy(() -> parser.parse("Voici le contenu, sans JSON."))
                .isInstanceOf(DocumentContentResponseParser.DocumentContentParseException.class);
    }

    @Test
    void parse_throws_whenSectionsFieldIsMissing() {
        assertThatThrownBy(() -> parser.parse("{\"other\": []}"))
                .isInstanceOf(DocumentContentResponseParser.DocumentContentParseException.class);
    }

    @Test
    void parse_toleratesAnEmptySectionsArray() {
        assertThat(parser.parse("{\"sections\": []}")).isEmpty();
    }
}
