package com.docuai.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parseur est le contrat entre l'éditeur de sections du frontend (qui
 * sérialise en Markdown) et les exporteurs DOCX/PDF : tout ce que la barre
 * d'outils sait produire doit être reconnu ici, sinon la mise en forme
 * ressortirait en caractères bruts dans le document livré.
 */
class MarkdownContentParserTest {

    @Test
    void parse_recognisesHeadingsParagraphsAndTables() {
        List<MarkdownContentParser.Block> blocks = MarkdownContentParser.parse("""
                # Introduction
                Un paragraphe.

                | Indicateur | Valeur |
                | --- | --- |
                | CA | 420k |""");

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0)).isInstanceOf(MarkdownContentParser.HeadingBlock.class);
        assertThat(((MarkdownContentParser.HeadingBlock) blocks.get(0)).level()).isEqualTo(1);
        assertThat(((MarkdownContentParser.ParagraphBlock) blocks.get(1)).text()).isEqualTo("Un paragraphe.");
        assertThat(((MarkdownContentParser.TableBlock) blocks.get(2)).rows()).hasSize(2); // séparateur exclu
    }

    @Test
    void parse_groupsBulletItems_intoASingleList_withNestingDepth() {
        List<MarkdownContentParser.Block> blocks = MarkdownContentParser.parse("""
                - Premier
                - Deuxième
                  - Imbriqué""");

        assertThat(blocks).hasSize(1);
        MarkdownContentParser.ListBlock list = (MarkdownContentParser.ListBlock) blocks.get(0);
        assertThat(list.ordered()).isFalse();
        assertThat(list.items()).extracting(MarkdownContentParser.ListItem::text)
                .containsExactly("Premier", "Deuxième", "Imbriqué");
        assertThat(list.items()).extracting(MarkdownContentParser.ListItem::depth).containsExactly(0, 0, 1);
    }

    /** Une liste numérotée qui suit une liste à puces ne doit pas être absorbée par elle : les deux rendus diffèrent. */
    @Test
    void parse_splitsBulletAndOrderedLists_intoDistinctBlocks() {
        List<MarkdownContentParser.Block> blocks = MarkdownContentParser.parse("""
                - Puce
                1. Numéro""");

        assertThat(blocks).hasSize(2);
        assertThat(((MarkdownContentParser.ListBlock) blocks.get(0)).ordered()).isFalse();
        assertThat(((MarkdownContentParser.ListBlock) blocks.get(1)).ordered()).isTrue();
    }

    @Test
    void parse_recognisesHorizontalRule_withoutConfusingItWithATableSeparator() {
        assertThat(MarkdownContentParser.parse("---")).singleElement()
                .isInstanceOf(MarkdownContentParser.RuleBlock.class);
    }

    @Test
    void inline_splitsBoldItalicCodeAndLinks() {
        List<MarkdownContentParser.Segment> segments =
                MarkdownContentParser.inline("Du **gras**, de l'*italique*, du `code` et un [lien](https://x.io).");

        assertThat(segments).filteredOn(MarkdownContentParser.Segment::bold)
                .extracting(MarkdownContentParser.Segment::text).containsExactly("gras");
        assertThat(segments).filteredOn(MarkdownContentParser.Segment::italic)
                .extracting(MarkdownContentParser.Segment::text).containsExactly("italique");
        assertThat(segments).filteredOn(MarkdownContentParser.Segment::code)
                .extracting(MarkdownContentParser.Segment::text).containsExactly("code");
        assertThat(segments).filteredOn(s -> s.href() != null)
                .extracting(MarkdownContentParser.Segment::text, MarkdownContentParser.Segment::href)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("lien", "https://x.io"));
    }

    @Test
    void plainText_stripsMarks_keepingTheReadableText() {
        assertThat(MarkdownContentParser.plainText("Un **résultat** `mesuré`"))
                .isEqualTo("Un résultat mesuré");
    }

    @Test
    void inline_leavesTextUntouched_whenThereIsNoMark() {
        assertThat(MarkdownContentParser.inline("Texte simple."))
                .singleElement()
                .extracting(MarkdownContentParser.Segment::text, MarkdownContentParser.Segment::bold)
                .containsExactly("Texte simple.", false);
    }
}
