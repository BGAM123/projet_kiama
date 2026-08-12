package com.docuai.ai.service;

import com.docuai.ai.dto.SectionImprovement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parser ne doit jamais faire échouer une amélioration pour un problème de
 * format : tout ce qu'il ne sait pas lire redevient une suggestion en texte
 * brut, sans score.
 */
class SectionImprovementResponseParserTest {

    private final SectionImprovementResponseParser parser = new SectionImprovementResponseParser();

    @Test
    void parse_readsContentAndConfidence_fromStrictJson() {
        SectionImprovement result = parser.parse("{\"content\": \"Texte amélioré.\", \"confidence\": 87}");

        assertThat(result.getContent()).isEqualTo("Texte amélioré.");
        assertThat(result.getConfidence()).isEqualTo(87d);
    }

    @Test
    void parse_readsJson_wrappedInMarkdownCodeFence() {
        SectionImprovement result = parser.parse("""
                ```json
                {"content": "Texte amélioré.", "confidence": 62.5}
                ```""");

        assertThat(result.getContent()).isEqualTo("Texte amélioré.");
        assertThat(result.getConfidence()).isEqualTo(62.5d);
    }

    @Test
    void parse_fallsBackToRawText_whenResponseIsNotJson() {
        SectionImprovement result = parser.parse("Texte amélioré, sans aucun JSON.");

        assertThat(result.getContent()).isEqualTo("Texte amélioré, sans aucun JSON.");
        assertThat(result.getConfidence()).isNull();
    }

    @Test
    void parse_fallsBackToRawText_whenJsonHasNoContentField() {
        String raw = "{\"confidence\": 90}";

        SectionImprovement result = parser.parse(raw);

        assertThat(result.getContent()).isEqualTo(raw);
        assertThat(result.getConfidence()).isNull();
    }

    @Test
    void parse_acceptsConfidenceAsString_andClampsOutOfRangeValues() {
        assertThat(parser.parse("{\"content\": \"x\", \"confidence\": \"78%\"}").getConfidence()).isEqualTo(78d);
        assertThat(parser.parse("{\"content\": \"x\", \"confidence\": 140}").getConfidence()).isEqualTo(100d);
        assertThat(parser.parse("{\"content\": \"x\", \"confidence\": -5}").getConfidence()).isZero();
        assertThat(parser.parse("{\"content\": \"x\", \"confidence\": \"élevée\"}").getConfidence()).isNull();
    }

    @Test
    void parse_returnsEmptyContent_forBlankResponse() {
        assertThat(parser.parse("   ").getContent()).isEmpty();
        assertThat(parser.parse(null).getContent()).isEmpty();
    }
}
