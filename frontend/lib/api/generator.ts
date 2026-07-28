// Generates realistic per-section document content for the simulated stream.
import type { GeneratedDocument, StructureNode } from '@/types';
import { structures } from './fixtures';

const filler = (pivot: string) => [
  `Dans le cadre de ${pivot}, cette section présente les éléments clés retenus après analyse.`,
  `Les données collectées ont été consolidées afin de refléter fidèlement ${pivot}.`,
  `Cette partie synthétise les informations pertinentes et propose une lecture structurée du sujet traité.`,
  `L'objectif est de fournir une vision claire et exploitable, en s'appuyant sur les éléments de contexte décrits précédemment.`,
];

function tableBlock(columns: string[]) {
  const header = columns.join(' | ');
  const sep = columns.map(() => '---').join(' | ');
  const row = columns.map((c, i) => `${c} — exemple ${i + 1}`).join(' | ');
  return `\n\n| ${header} |\n| ${sep} |\n| ${row} |\n`;
}

export function buildSectionContent(node: StructureNode, doc: GeneratedDocument): string {
  const base = filler(doc.contentPivot)[Math.floor(Math.random() * filler.length)];
  if (node.type === 'table' && node.columns?.length) {
    return `${base}${tableBlock(node.columns)}`;
  }
  if (node.type === 'list') {
    return `${base}\n\n- Premier point important à retenir.\n- Second point nécessitant un suivi.\n- Troisième point à valider en comité.`;
  }
  if (node.type === 'heading' && node.level === 1) {
    return `${base} Le contenu ci-dessous développe les aspects essentiels liés à cette partie.`;
  }
  return `${base} Les éléments présentés ici s'inscrivent dans la continuité de la structure attendue.`;
}

export function getStructureFor(documentTypeId: string): StructureNode[] {
  return structures.find((s) => s.documentTypeId === documentTypeId)?.tree ?? [];
}

// Async iterator that emits progress + content per section, mimicking SSE.
export interface StreamEvent {
  type: 'progress' | 'section' | 'done';
  sectionIndex?: number;
  total?: number;
  sectionLabel?: string;
  content?: string;
}

export async function* streamGeneration(
  doc: GeneratedDocument,
  onUpdate: (patch: Partial<GeneratedDocument>) => void,
): AsyncGenerator<StreamEvent> {
  const tree = getStructureFor(doc.documentTypeId).filter((n) => n.type !== 'cover');
  const total = tree.length;
  for (let i = 0; i < total; i++) {
    const node = tree[i];
    yield { type: 'progress', sectionIndex: i, total, sectionLabel: node.label };
    // simulate per-section latency (grows slightly)
    await new Promise((r) => setTimeout(r, 700 + i * 220));
    const content = buildSectionContent(node, doc);
    const sections = doc.sections.map((s, idx) =>
      idx === i ? { ...s, status: 'DONE' as const, content } : s,
    );
    const contentAcc = (doc.content ? doc.content + '\n\n' : '') + `## ${node.label}\n${content}`;
    onUpdate({ sections, content: contentAcc });
    yield { type: 'section', sectionIndex: i, total, sectionLabel: node.label, content };
  }
  yield { type: 'done', total };
}
