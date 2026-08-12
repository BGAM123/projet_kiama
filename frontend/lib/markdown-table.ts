import type { TableColumnDef } from '@/types';

/**
 * Conversions entre le tableau Markdown stocké dans `DocumentSection.userContent`
 * et le HTML manipulé par l'éditeur Tiptap.
 *
 * Le format pivot reste le Markdown (`| a | b |` + ligne de séparation) : c'est
 * exactement ce que `MarkdownContentParser` (docuai-export) reconnaît pour
 * produire un vrai tableau Word/PDF à l'export. L'éditeur n'est qu'une vue
 * confortable par-dessus ce format.
 */

const DEFAULT_ROW_COUNT = 3;

function escapeCell(text: string): string {
  return text.replace(/\r?\n/g, ' ').replace(/\|/g, '\\|').trim();
}

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;');
}

function isTableRow(line: string): boolean {
  return /^\|.*\|\s*$/.test(line.trim());
}

function isSeparatorRow(line: string): boolean {
  return /^\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)+\|?$/.test(line.trim());
}

function splitCells(line: string): string[] {
  const trimmed = line.trim();
  return trimmed
    .slice(1, -1)
    .split(/(?<!\\)\|/)
    .map((c) => c.replace(/\\\|/g, '|').trim());
}

/** Tableau HTML vide (en-tête = colonnes attendues du Document Type) prêt à être rempli ligne par ligne. */
export function buildEmptyTableHtml(columns: TableColumnDef[] | undefined, suggestedRowCount?: number): string {
  const headers = columns?.length ? columns.map((c) => c.name) : ['Colonne 1', 'Colonne 2'];
  const rowCount = Math.max(1, suggestedRowCount ?? DEFAULT_ROW_COUNT);
  const headerCells = headers.map((h) => `<th><p>${escapeHtml(h)}</p></th>`).join('');
  const bodyRow = `<tr>${headers.map(() => '<td><p></p></td>').join('')}</tr>`;
  return `<table><tbody><tr>${headerCells}</tr>${bodyRow.repeat(rowCount)}</tbody></table>`;
}

/** Markdown -> HTML, pour recharger dans l'éditeur un tableau déjà rempli. Renvoie `null` si le contenu n'est pas un tableau. */
export function markdownTableToHtml(markdown: string | undefined): string | null {
  if (!markdown) return null;
  const lines = markdown.split('\n').filter((l) => l.trim().length > 0);
  if (lines.length === 0 || !lines.every(isTableRow)) return null;

  const rows = lines.filter((l) => !isSeparatorRow(l)).map(splitCells);
  if (rows.length === 0) return null;

  const [header, ...body] = rows;
  const headerHtml = `<tr>${header.map((c) => `<th><p>${escapeHtml(c)}</p></th>`).join('')}</tr>`;
  const bodyHtml = body
    .map((row) => `<tr>${row.map((c) => `<td><p>${escapeHtml(c)}</p></td>`).join('')}</tr>`)
    .join('');
  return `<table><tbody>${headerHtml}${bodyHtml}</tbody></table>`;
}

/**
 * HTML de l'éditeur -> tableau Markdown. Utilise DOMParser (composant client
 * uniquement) plutôt qu'une dépendance de conversion supplémentaire : la forme
 * manipulée ici est toujours un unique tableau, pas du Markdown arbitraire.
 */
export function htmlToMarkdownTable(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  const table = doc.querySelector('table');
  if (!table) return '';

  const rows = Array.from(table.querySelectorAll('tr')).map((tr) =>
    Array.from(tr.querySelectorAll('th, td')).map((cell) => escapeCell(cell.textContent ?? '')),
  );
  if (rows.length === 0) return '';

  const columnCount = Math.max(...rows.map((r) => r.length));
  const pad = (row: string[]) => Array.from({ length: columnCount }, (_, i) => row[i] ?? '');

  const [header, ...body] = rows;
  const lines = [
    `| ${pad(header).join(' | ')} |`,
    `| ${Array.from({ length: columnCount }, () => '---').join(' | ')} |`,
    ...body.map((row) => `| ${pad(row).join(' | ')} |`),
  ];
  return lines.join('\n');
}
