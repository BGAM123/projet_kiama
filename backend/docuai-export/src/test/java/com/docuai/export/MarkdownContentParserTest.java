package com.docuai.export;

import com.docuai.export.DocumentBlocks.Block;
import com.docuai.export.DocumentBlocks.HeadingBlock;
import com.docuai.export.DocumentBlocks.ListBlock;
import com.docuai.export.DocumentBlocks.ParagraphBlock;
import com.docuai.export.DocumentBlocks.RuleBlock;
import com.docuai.export.DocumentBlocks.Segment;
import com.docuai.export.DocumentBlocks.TableBlock;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le parseur reste le contrat des documents créés avant l'éditeur type Word
 * (contenu assemblé section par section en Markdown) : tout ce que l'ancien
 * éditeur savait produire doit continuer d'être reconnu, sinon la mise en forme
 * de ces documents ressortirait en caractères bruts à l'export.
 */
class MarkdownContentParserTest {

    @Test
    void parse_recognisesHeadingsParagraphsAndTables() {
        List<Block> blocks = MarkdownContentParser.parse("""
                # Introduction
                Un paragraphe.

                | Indicateur | Valeur |
                | --- | --- |
                | CA | 420k |""");

        assertThat(blocks).hasSize(3);
        assertThat(blocks.get(0)).isInstanceOf(HeadingBlock.class);
        assertThat(((HeadingBlock) blocks.get(0)).level()).isEqualTo(1);
        assertThat(DocumentBlocks.plainText(((ParagraphBlock) blocks.get(1)).segments())).isEqualTo("Un paragraphe.");
        assertThat(((TableBlock) blocks.get(2)).rows()).hasSize(2); // séparateur exclu
    }

    /** La première ligne d'un tableau Markdown est son en-tête : c'est ce qui la fait ressortir en gras/tramé à l'export. */
    @Test
    void parse_marksTheFirstTableRow_asHeader() {
        TableBlock table = (TableBlock) MarkdownContentParser.parse("""
                | Indicateur | Valeur |
                | --- | --- |
                | CA | 420k |""").get(0);

        assertThat(table.rows().get(0).cells()).allMatch(DocumentBlocks.TableCell::header);
        assertThat(table.rows().get(1).cells()).noneMatch(DocumentBlocks.TableCell::header);
    }

    @Test
    void parse_groupsBulletItems_intoASingleList_withNestingDepth() {
        List<Block> blocks = MarkdownContentParser.parse("""
                - Premier
                - Deuxième
                  - Imbriqué""");

        assertThat(blocks).hasSize(1);
        ListBlock list = (ListBlock) blocks.get(0);
        assertThat(list.ordered()).isFalse();
        assertThat(list.items()).extracting(item -> DocumentBlocks.plainText(item.segments()))
                .containsExactly("Premier", "Deuxième", "Imbriqué");
        assertThat(list.items()).extracting(DocumentBlocks.ListItem::depth).containsExactly(0, 0, 1);
    }

    /** Une liste numérotée qui suit une liste à puces ne doit pas être absorbée par elle : les deux rendus diffèrent. */
    @Test
    void parse_splitsBulletAndOrderedLists_intoDistinctBlocks() {
        List<Block> blocks = MarkdownContentParser.parse("""
                - Puce
                1. Numéro""");

        assertThat(blocks).hasSize(2);
        assertThat(((ListBlock) blocks.get(0)).ordered()).isFalse();
        assertThat(((ListBlock) blocks.get(1)).ordered()).isTrue();
    }

    @Test
    void parse_recognisesHorizontalRule_withoutConfusingItWithATableSeparator() {
        assertThat(MarkdownContentParser.parse("---")).singleElement().isInstanceOf(RuleBlock.class);
    }

    @Test
    void inline_splitsBoldItalicCodeAndLinks() {
        List<Segment> segments =
                MarkdownContentParser.inline("Du **gras**, de l'*italique*, du `code` et un [lien](https://x.io).");

        assertThat(segments).filteredOn(Segment::bold).extracting(Segment::text).containsExactly("gras");
        assertThat(segments).filteredOn(Segment::italic).extracting(Segment::text).containsExactly("italique");
        assertThat(segments).filteredOn(Segment::code).extracting(Segment::text).containsExactly("code");
        assertThat(segments).filteredOn(s -> s.href() != null)
                .extracting(Segment::text, Segment::href)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("lien", "https://x.io"));
    }

    @Test
    void plainText_stripsMarks_keepingTheReadableText() {
        assertThat(MarkdownContentParser.plainText("Un **résultat** `mesuré`")).isEqualTo("Un résultat mesuré");
    }

    @Test
    void inline_leavesTextUntouched_whenThereIsNoMark() {
        assertThat(MarkdownContentParser.inline("Texte simple."))
                .singleElement()
                .extracting(Segment::text, Segment::bold)
                .containsExactly("Texte simple.", false);
    }
}
