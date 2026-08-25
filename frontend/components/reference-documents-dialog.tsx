'use client';

import { useRef, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { ArrowUp, ChevronDown, FileText, Loader2, Paperclip, Sparkles, X } from 'lucide-react';
import { cn } from '@/lib/utils';
import { useDocumentReferences } from '@/lib/hooks/queries';
import { addDocumentReference, deleteDocumentReference, generateAtCursor } from '@/lib/api/client';
import type { ReferenceDocument } from '@/types';
import type { WordEditorHandle } from '@/components/word-editor';

// Alignés sur DocumentReferenceService (backend) : mêmes extensions acceptées,
// même plafond par défaut (docuai.generation.max-reference-documents).
const MAX_FILE_SIZE_MB = 25;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;
const ALLOWED_FILE_PATTERN = /\.(docx?|pdf|md|txt)$/i;
const MAX_REFERENCE_DOCUMENTS = 10;

const EXTENSION_BADGE: Record<string, string> = {
  doc: 'DOC', docx: 'DOCX', pdf: 'PDF', md: 'MD', txt: 'TXT',
};

function extensionOf(fileName: string): string {
  const match = /\.([a-z0-9]+)$/i.exec(fileName);
  return match ? match[1].toLowerCase() : '';
}

/**
 * Composer IA flottant, ancré au-dessus du bas de l'éditeur (jamais dans le
 * flux — voir son wrapper `pointer-events-none` dans documents/[id]/page.tsx) :
 * à peine visible au repos, il se dévoile en pointant le curseur vers le bas
 * de l'éditeur, s'ouvre en plein au clic, et reste visible pendant la
 * génération même si le curseur s'éloigne — pour que l'utilisateur voie que
 * l'IA travaille sans jamais bloquer la navigation dans le document (le
 * panneau ne couvre qu'une bande étroite en bas, centrée, le reste de
 * l'éditeur restant toujours cliquable/scrollable).
 */
export function ReferenceDocumentsDialog({
  documentId,
  editorRef,
}: {
  documentId: string;
  editorRef: React.RefObject<WordEditorHandle | null>;
}) {
  const [hovering, setHovering] = useState(false);
  const [expanded, setExpanded] = useState(false);
  const [instruction, setInstruction] = useState('');
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const qc = useQueryClient();

  const { data: references } = useDocumentReferences(documentId);

  const uploadMutation = useMutation({
    mutationFn: (file: File) => addDocumentReference(documentId, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['document-refs', documentId] }),
    onError: (e: Error) => toast.error("Impossible d'importer ce document", { description: e.message }),
  });

  const deleteMutation = useMutation({
    mutationFn: (referenceId: string) => deleteDocumentReference(documentId, referenceId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['document-refs', documentId] }),
    onError: (e: Error) => toast.error('Impossible de retirer ce document', { description: e.message }),
  });

  const generateMutation = useMutation({
    mutationFn: () => generateAtCursor(documentId, instruction.trim()),
    onSuccess: ({ data }) => {
      editorRef.current?.insertAtCursor(data.contentHtml);
      setInstruction('');
      toast.success('Contenu généré et inséré.');
    },
    onError: (e: Error) => toast.error('La génération a échoué', { description: e.message }),
  });

  const pending = generateMutation.isPending;
  // Partiellement visible au survol OU pendant la génération (même si le curseur s'est éloigné) — jamais au repos.
  const peeking = hovering || pending;
  const count = references?.length ?? 0;
  const atLimit = count >= MAX_REFERENCE_DOCUMENTS;
  const canSend = instruction.trim().length > 0 && !pending;

  function handleFiles(files: FileList | null) {
    if (!files || !files.length) return;
    for (const file of Array.from(files)) {
      if (!ALLOWED_FILE_PATTERN.test(file.name)) {
        toast.error('Format non supporté', { description: `${file.name} — formats acceptés : .docx, .pdf, .md, .txt` });
        continue;
      }
      if (file.size > MAX_FILE_SIZE_BYTES) {
        toast.error('Fichier trop volumineux', { description: `${file.name} dépasse ${MAX_FILE_SIZE_MB} Mo.` });
        continue;
      }
      uploadMutation.mutate(file);
    }
  }

  function handleSend() {
    if (!canSend) return;
    // Réduit immédiatement au bandeau "peek" : pendant le traitement, le
    // panneau reste seulement partiellement visible (indicateur d'activité),
    // au lieu de rester ouvert en plein sur le document.
    setExpanded(false);
    generateMutation.mutate();
  }

  return (
    <div
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      onDragOver={(e) => { e.preventDefault(); if (!atLimit) { setHovering(true); setDragging(true); } }}
      onDragLeave={() => setDragging(false)}
      onDrop={(e) => { e.preventDefault(); setDragging(false); if (!atLimit) { setExpanded(true); handleFiles(e.dataTransfer.files); } }}
      className={cn(
        'pointer-events-auto w-full max-w-lg overflow-hidden rounded-2xl border backdrop-blur-md transition-all duration-300 ease-out',
        expanded
          ? 'border-border bg-card/95 shadow-xl'
          : dragging
            ? 'h-16 border-accent bg-accent/10 shadow-lg'
            : peeking
              ? 'h-12 border-border/60 bg-card/75 shadow-md'
              : 'h-2.5 border-transparent bg-card/20 shadow-none hover:h-3',
      )}
    >
      {!expanded ? (
        <button
          type="button"
          aria-label="Ouvrir l'assistant IA"
          onClick={() => setExpanded(true)}
          className="flex h-full w-full items-center justify-center gap-2 text-xs text-muted-foreground"
        >
          {peeking &&
            (pending ? (
              <>
                <Loader2 className="h-3.5 w-3.5 animate-spin text-primary" /> Génération en cours…
              </>
            ) : (
              <>
                <Sparkles className="h-3.5 w-3.5 text-primary" /> Demander à l&apos;IA…
              </>
            ))}
        </button>
      ) : (
        <div>
          <div className="flex items-center justify-between px-3 pt-2">
            <span className="flex items-center gap-1.5 text-xs font-medium text-muted-foreground">
              <Sparkles className="h-3.5 w-3.5 text-primary" /> Assistant IA
            </span>
            <button
              type="button"
              aria-label="Réduire"
              onClick={() => setExpanded(false)}
              className="rounded-full p-1 text-muted-foreground hover:bg-muted"
            >
              <ChevronDown className="h-4 w-4" />
            </button>
          </div>

          {count > 0 && (
            <div className="flex flex-wrap gap-2 p-3 pb-0">
              {references!.map((ref: ReferenceDocument) => {
                const ext = extensionOf(ref.fileName);
                return (
                  <div
                    key={ref.id}
                    className="group relative flex w-36 items-center gap-2 rounded-xl border border-border bg-muted/40 p-2"
                  >
                    <div className="flex h-8 w-8 flex-none items-center justify-center rounded-lg bg-primary/10 text-primary">
                      <FileText className="h-4 w-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate text-xs font-medium">{ref.fileName}</p>
                      <span className="mt-0.5 inline-block rounded bg-secondary px-1.5 py-0.5 text-[10px] font-semibold uppercase text-secondary-foreground">
                        {EXTENSION_BADGE[ext] ?? (ext || 'Fichier')}
                      </span>
                    </div>
                    <button
                      type="button"
                      aria-label={`Retirer ${ref.fileName}`}
                      disabled={deleteMutation.isPending}
                      onClick={() => deleteMutation.mutate(ref.id)}
                      className="absolute -right-1.5 -top-1.5 flex h-5 w-5 items-center justify-center rounded-full border border-border bg-background text-muted-foreground opacity-0 shadow-sm transition-opacity hover:text-destructive group-hover:opacity-100 disabled:opacity-50"
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </div>
                );
              })}
            </div>
          )}

          <textarea
            autoFocus
            rows={2}
            value={instruction}
            onChange={(e) => setInstruction(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSend();
              }
              if (e.key === 'Escape') setExpanded(false);
            }}
            placeholder="Décrivez ce que l'IA doit rédiger — inséré à la position du curseur."
            className="w-full resize-none bg-transparent px-4 py-2.5 text-sm placeholder:text-muted-foreground focus:outline-none"
          />

          <div className="flex items-center justify-between px-3 pb-3">
            <button
              type="button"
              aria-label="Attacher un document de référence"
              disabled={atLimit || uploadMutation.isPending}
              onClick={() => inputRef.current?.click()}
              title={atLimit ? `Limite de ${MAX_REFERENCE_DOCUMENTS} documents atteinte` : 'Attacher un document de référence'}
              className="flex h-8 w-8 items-center justify-center rounded-full border border-border text-muted-foreground transition-colors hover:bg-muted disabled:cursor-not-allowed disabled:opacity-50"
            >
              {uploadMutation.isPending ? <Loader2 className="h-4 w-4 animate-spin" /> : <Paperclip className="h-4 w-4" />}
            </button>
            <input
              ref={inputRef}
              type="file"
              multiple
              className="hidden"
              accept=".doc,.docx,.pdf,.md,.txt"
              onChange={(e) => { handleFiles(e.target.files); e.target.value = ''; }}
            />

            <button
              type="button"
              aria-label="Générer et insérer"
              disabled={!canSend}
              onClick={handleSend}
              className="flex h-8 w-8 items-center justify-center rounded-full bg-accent text-accent-foreground transition-opacity hover:bg-accent/90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {pending ? <Loader2 className="h-4 w-4 animate-spin" /> : <ArrowUp className="h-4 w-4" />}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
