import type { JSONContent } from '@tiptap/react';

/**
 * Pont entre l'éditeur riche (document ProseMirror) et le format réellement
 * stocké dans `DocumentSection.userContent` : du Markdown.
 *
 * Le Markdown n'est pas un choix esthétique — c'est ce que
 * `MarkdownContentParser` (docuai-export) relit pour produire de vrais titres,
 * listes, tableaux et enrichissements dans le DOCX et le PDF. La barre d'outils
 * n'expose donc que ce qui traverse ce pont : tout ce qui est ici a un rendu
 * réel dans le document exporté, et rien de ce qui est proposé à l'utilisateur
 * n'est silencieusement perdu à la sauvegarde.
 *
 * Hors périmètre volontaire (aucune représentation Markdown, donc aucun rendu
 * à l'export) : police, taille de police, couleur de texte, surlignage et
 * alignement.
 */

const INLINE_PATTERN = /\*\*(.+?)\*\*|\*(.+?)\*|`(.+?)`|\[([^\]]+)\]\(([^)\s]+)\)/g;

// --------------------------------------------------------------------------
// Document ProseMirror -> Markdown (sauvegarde)
// --------------------------------------------------------------------------

function inlineToMarkdown(nodes: JSONContent[] | undefined): string {
  if (!nodes) return '';
  return nodes
    .map((node) => {
      if (node.type === 'hardBreak') return ' ';
      if (node.type !== 'text' || !node.text) return '';
      const marks = node.marks ?? [];
      let text = node.text;
      // `code` d'abord et seul : du gras à l'intérieur d'un code inline
      // n'aurait pas de sens et ressortirait en astérisques littérales.
      if (marks.some((m) => m.type === 'code')) {
        text = `\`${text}\``;
      } else {
        if (marks.some((m) => m.type === 'bold')) text = `**${text}**`;
        if (marks.some((m) => m.type === 'italic')) text = `*${text}*`;
      }
      const link = marks.find((m) => m.type === 'link');
      const href = link?.attrs?.href as string | undefined;
      if (href) text = `[${text}](${href})`;
      return text;
    })
    .join('');
}

function listToMarkdown(list: JSONContent, depth: number): string {
  const ordered = list.type === 'orderedList';
  const indent = '  '.repeat(depth);
  const lines: string[] = [];

  (list.content ?? []).forEach((item, index) => {
    const [first, ...rest] = item.content ?? [];
    lines.push(`${indent}${ordered ? `${index + 1}. ` : '- '}${inlineToMarkdown(first?.content)}`);
    rest.forEach((child) => {
      if (child.type === 'bulletList' || child.type === 'orderedList') {
        lines.push(listToMarkdown(child, depth + 1));
      } else {
        lines.push(`${'  '.repeat(depth + 1)}${inlineToMarkdown(child.content)}`);
      }
    });
  });
  return lines.join('\n');
}

function escapeCell(text: string): string {
  return text.replace(/\r?\n/g, ' ').replace(/\|/g, '\\|').trim();
}

function tableToMarkdown(table: JSONContent): string {
  const rows = (table.content ?? []).map((row) =>
    (row.content ?? []).map((cell) =>
      escapeCell((cell.content ?? []).map((block) => inlineToMarkdown(block.content)).join(' ')),
    ),
  );
  if (rows.length === 0) return '';

  const columnCount = Math.max(...rows.map((r) => r.length));
  const pad = (row: string[]) => Array.from({ length: columnCount }, (_, i) => row[i] ?? '');
  const [header, ...body] = rows;
  return [
    `| ${pad(header).join(' | ')} |`,
    `| ${Array.from({ length: columnCount }, () => '---').join(' | ')} |`,
    ...body.map((row) => `| ${pad(row).join(' | ')} |`),
  ].join('\n');
}

function blockToMarkdown(node: JSONContent, depth = 0): string[] {
  switch (node.type) {
    case 'heading':
      return [`${'#'.repeat(Math.min(Math.max(Number(node.attrs?.level ?? 1), 1), 6))} ${inlineToMarkdown(node.content)}`];
    case 'paragraph':
      return [inlineToMarkdown(node.content)];
    case 'bulletList':
    case 'orderedList':
      return [listToMarkdown(node, depth)];
    case 'horizontalRule':
      return ['---'];
    case 'table':
      return [tableToMarkdown(node)];
    default:
      return (node.content ?? []).flatMap((child) => blockToMarkdown(child, depth));
  }
}

/** Document de l'éditeur -> Markdown stockable. Les blocs vides sont écartés : une ligne blanche ne sépare que du contenu réel. */
export function docToMarkdown(doc: JSONContent | undefined): string {
  if (!doc) return '';
  return (doc.content ?? [])
    .flatMap((node) => blockToMarkdown(node))
    .filter((block) => block.trim().length > 0)
    .join('\n\n');
}

// --------------------------------------------------------------------------
// Markdown -> HTML (chargement dans l'éditeur)
// --------------------------------------------------------------------------

/**
 * Le HTML produit ici est injecté tel quel dans l'éditeur et dans l'aperçu des
 * sections : les guillemets sont échappés au même titre que les chevrons, pour
 * qu'une URL de lien ne puisse pas s'échapper de son attribut `href`.
 */
function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

/** Seuls les schémas d'un lien de document sont rendus cliquables — un `javascript:` collé dans une section ne doit pas devenir exécutable dans l'aperçu. */
function safeHref(href: string): boolean {
  return /^(https?:\/\/|mailto:|\/|#)/i.test(href.trim());
}

function inlineToHtml(text: string): string {
  let result = '';
  let cursor = 0;
  INLINE_PATTERN.lastIndex = 0;
  let match = INLINE_PATTERN.exec(text);
  while (match) {
    result += escapeHtml(text.slice(cursor, match.index));
    const [, bold, italic, code, label, href] = match;
    if (bold !== undefined) result += `<strong>${escapeHtml(bold)}</strong>`;
    else if (italic !== undefined) result += `<em>${escapeHtml(italic)}</em>`;
    else if (code !== undefined) result += `<code>${escapeHtml(code)}</code>`;
    else result += safeHref(href) ? `<a href="${escapeHtml(href)}">${escapeHtml(label)}</a>` : escapeHtml(label);
    cursor = match.index + match[0].length;
    match = INLINE_PATTERN.exec(text);
  }
  return result + escapeHtml(text.slice(cursor));
}

const HEADING = /^(#{1,6})\s+(.*)$/;
const BULLET_ITEM = /^([ \t]*)[-*+]\s+(.*)$/;
const ORDERED_ITEM = /^([ \t]*)\d+[.)]\s+(.*)$/;
const HORIZONTAL_RULE = /^(-{3,}|\*{3,}|_{3,})$/;
const TABLE_ROW = /^\|.*\|\s*$/;
const TABLE_SEPARATOR = /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$/;

/** Une tabulation ou deux espaces valent un niveau — même convention que MarkdownContentParser côté export. */
function indentDepth(indent: string): number {
  return Math.floor(indent.split('').reduce((total, c) => total + (c === '\t' ? 2 : 1), 0) / 2);
}

function splitCells(line: string): string[] {
  return line
    .trim()
    .slice(1, -1)
    .split(/(?<!\\)\|/)
    .map((cell) => cell.replace(/\\\|/g, '|').trim());
}

/** Suite d'items de même nature -> `<ul>`/`<ol>` imbriqués selon leur indentation. */
function itemsToHtml(items: { depth: number; text: string }[], ordered: boolean, depth = 0): string {
  const tag = ordered ? 'ol' : 'ul';
  let html = `<${tag}>`;
  let index = 0;
  while (index < items.length) {
    const item = items[index];
    index++;
    const nested: { depth: number; text: string }[] = [];
    while (index < items.length && items[index].depth > depth) {
      nested.push(items[index]);
      index++;
    }
    html += `<li><p>${inlineToHtml(item.text)}</p>`;
    if (nested.length > 0) html += itemsToHtml(nested, ordered, depth + 1);
    html += '</li>';
  }
  return `${html}</${tag}>`;
}

/**
 * Markdown stocké -> HTML chargé dans l'éditeur. Volontairement limité au même
 * sous-ensemble que {@link docToMarkdown} et que le parseur d'export : un
 * contenu écrit avant l'éditeur riche (texte brut) reste lisible, chaque ligne
 * devenant un paragraphe.
 */
export function markdownToHtml(markdown: string | undefined): string {
  if (!markdown || !markdown.trim()) return '';
  const lines = markdown.split('\n');
  const html: string[] = [];
  let paragraph: string[] = [];

  const flushParagraph = () => {
    if (paragraph.length > 0) {
      html.push(`<p>${inlineToHtml(paragraph.join(' '))}</p>`);
      paragraph = [];
    }
  };

  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    const trimmed = line.trim();
    const heading = HEADING.exec(trimmed);

    if (heading) {
      flushParagraph();
      html.push(`<h${heading[1].length}>${inlineToHtml(heading[2])}</h${heading[1].length}>`);
      i++;
    } else if (HORIZONTAL_RULE.test(trimmed)) {
      flushParagraph();
      html.push('<hr>');
      i++;
    } else if (TABLE_ROW.test(trimmed)) {
      flushParagraph();
      const rows: string[][] = [];
      while (i < lines.length && TABLE_ROW.test(lines[i].trim())) {
        if (!TABLE_SEPARATOR.test(lines[i].trim())) rows.push(splitCells(lines[i]));
        i++;
      }
      if (rows.length > 0) {
        const [header, ...body] = rows;
        const headerHtml = `<tr>${header.map((c) => `<th><p>${inlineToHtml(c)}</p></th>`).join('')}</tr>`;
        const bodyHtml = body
          .map((row) => `<tr>${row.map((c) => `<td><p>${inlineToHtml(c)}</p></td>`).join('')}</tr>`)
          .join('');
        html.push(`<table><tbody>${headerHtml}${bodyHtml}</tbody></table>`);
      }
    } else if (BULLET_ITEM.test(line) || ORDERED_ITEM.test(line)) {
      flushParagraph();
      const ordered = ORDERED_ITEM.test(line);
      const items: { depth: number; text: string }[] = [];
      while (i < lines.length) {
        const match = (ordered ? ORDERED_ITEM : BULLET_ITEM).exec(lines[i]);
        if (!match) break;
        items.push({ depth: indentDepth(match[1]), text: match[2].trim() });
        i++;
      }
      html.push(itemsToHtml(items, ordered));
    } else if (trimmed.length === 0) {
      flushParagraph();
      i++;
    } else {
      paragraph.push(trimmed);
      i++;
    }
  }
  flushParagraph();
  return html.join('');
}
