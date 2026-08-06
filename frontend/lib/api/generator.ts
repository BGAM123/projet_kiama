// Suivi de génération en direct — branché sur le vrai flux SSE backend
// (GET /api/v1/generations/{id}/stream, Bloc 6). Remplace l'ancienne
// simulation locale (contenu de section généré côté client avec des phrases
// génériques) : le contenu vient désormais réellement du fournisseur IA
// configuré côté serveur.
import type { GeneratedDocument, StructureNode } from '@/types';
import { structures } from './fixtures';
import { ApiError, API_BASE_URL, getCurrentAccessToken } from './http';

export function getStructureFor(documentTypeId: string): StructureNode[] {
  return structures.find((s) => s.documentTypeId === documentTypeId)?.tree ?? [];
}

// Contrat d'événement émis par le backend (GenerationStreamService), un objet
// JSON par ligne SSE. `delta` porte un fragment incrémental de texte (pas le
// contenu accumulé — au client de concaténer) : la section s'écrit
// progressivement, comme chez Claude, plutôt que d'apparaître d'un bloc à la
// fin. `progress` est émis à CHAQUE tentative de génération d'une section
// (pas seulement la première) — y compris lors d'un retry correctif après
// violation de contrainte — et sert de signal pour réinitialiser l'aperçu en
// direct avant que les `delta` de la nouvelle tentative n'arrivent. `error`
// (code/message) est émis quand le verrou Redis anti-génération concurrente
// refuse la demande (GENERATION_IN_PROGRESS) — le flux se termine alors
// directement après, sans événement `done`.
export interface StreamEvent {
  type: 'progress' | 'delta' | 'section' | 'done' | 'error';
  sectionIndex?: number;
  total?: number;
  sectionLabel?: string;
  content?: string;
  code?: string;
  message?: string;
}

/**
 * Lit une réponse SSE via `fetch` (pas `EventSource`, qui ne permet pas
 * d'en-tête `Authorization` personnalisé — indispensable ici, le flux est
 * protégé par JWT comme le reste de l'API). Chaque événement backend est une
 * simple ligne `data:{...}` suivie d'une ligne vide ; les payloads JSON
 * multi-lignes (aucun ici en pratique, le backend sérialise en une ligne)
 * sont malgré tout reconstitués en concaténant les fragments `data:` d'un
 * même événement, conformément à la spec SSE.
 */
async function* readSseEvents(url: string): AsyncGenerator<StreamEvent> {
  const token = getCurrentAccessToken();
  let response: Response;
  try {
    response = await fetch(url, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
  } catch (e) {
    throw new ApiError('NETWORK_ERROR', e instanceof Error ? e.message : 'Connexion au flux de génération impossible.', url);
  }
  if (!response.ok || !response.body) {
    throw new ApiError('STREAM_FAILED', `Le flux de génération a échoué (HTTP ${response.status}).`, url);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });

      let separatorIndex: number;
      while ((separatorIndex = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, separatorIndex);
        buffer = buffer.slice(separatorIndex + 2);
        const dataLines = rawEvent
          .split('\n')
          .filter((line) => line.startsWith('data:'))
          .map((line) => line.slice(5).trim());
        if (!dataLines.length) continue;
        try {
          yield JSON.parse(dataLines.join('\n')) as StreamEvent;
        } catch {
          // Ligne malformée (ne devrait pas arriver, le backend sérialise
          // toujours du JSON valide) — ignorée plutôt que de casser le flux.
        }
      }
    }
  } finally {
    reader.releaseLock();
  }
}

/**
 * Consomme le flux réel de génération et met à jour `sections`/`content` au
 * fil des événements, comme le faisait l'ancienne simulation — `onUpdate`
 * permet à l'appelant (chat/page.tsx) de refléter la progression en direct
 * sans attendre la fin du flux.
 */
export async function* streamGeneration(
  doc: GeneratedDocument,
  onUpdate: (patch: Partial<GeneratedDocument>) => void,
): AsyncGenerator<StreamEvent> {
  let sections = doc.sections;
  let content = doc.content ?? '';

  for await (const evt of readSseEvents(`${API_BASE_URL}/generations/${doc.id}/stream`)) {
    if (evt.type === 'progress' && evt.sectionIndex !== undefined) {
      const idx = evt.sectionIndex;
      // content: '' réinitialise l'aperçu en direct — un "progress" arrive à
      // chaque tentative (pas seulement la 1ère), donc aussi avant un retry
      // correctif : sans ce reset, le nouveau texte s'ajouterait à l'ancien
      // au lieu de le remplacer.
      sections = sections.map((s, i) => (i === idx ? { ...s, status: 'GENERATING', content: '' } : s));
      onUpdate({ sections });
    } else if (evt.type === 'delta' && evt.sectionIndex !== undefined) {
      const idx = evt.sectionIndex;
      const piece = evt.content ?? '';
      sections = sections.map((s, i) => (i === idx ? { ...s, content: (s.content ?? '') + piece } : s));
      onUpdate({ sections });
    } else if (evt.type === 'section' && evt.sectionIndex !== undefined) {
      const idx = evt.sectionIndex;
      const sectionContent = evt.content ?? '';
      sections = sections.map((s, i) => (i === idx ? { ...s, status: sectionContent ? 'DONE' : 'FAILED', content: sectionContent } : s));
      if (sectionContent) {
        content = (content ? content + '\n\n' : '') + `## ${evt.sectionLabel ?? sections[idx]?.label ?? ''}\n${sectionContent}`;
      }
      onUpdate({ sections, content });
    }
    yield evt;
  }
}
