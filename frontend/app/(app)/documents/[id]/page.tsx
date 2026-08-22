'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Check,
  Download,
  FileDown,
  FileText,
  Loader2,
  Save,
} from 'lucide-react';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { DocumentStatusBadge } from '@/components/status-badge';
import { SectionSuggestionDiff } from '@/components/section-suggestion-diff';
import { ConfidencePill } from '@/components/confidence-legend';
import {
  WordEditor,
  type EditorSelection,
  type OutlineItem,
  type WordEditorHandle,
} from '@/components/word-editor';
import { useDocument } from '@/lib/hooks/queries';
import {
  updateDocumentContent,
  improveDocumentSelection,
  finalizeDocument,
  exportDocumentFile,
  type ExportFormat,
} from '@/lib/api/client';
import { markdownToHtml } from '@/lib/markdown-editor';
import { cn } from '@/lib/utils';
import type { AppDocument, DocumentSection } from '@/types';

const AUTOSAVE_DEBOUNCE_MS = 1200;

/**
 * Rédaction d'un document : le squelette du Document Type choisi s'ouvre
 * directement dans un éditeur type Word, se modifie librement (mise en forme,
 * tableaux, images) et s'exporte en DOCX ou PDF.
 *
 * Le sommaire de gauche est construit à partir des titres réellement présents
 * dans le document — pas d'une liste de sections figée : renommer un titre dans
 * l'éditeur le renomme dans le sommaire, en ajouter un l'y fait apparaître.
 */
export default function DocumentEditPage() {
  const params = useParams<{ id: string }>();
  const qc = useQueryClient();
  const { data: doc, isLoading } = useDocument(params.id);

  const editorRef = useRef<WordEditorHandle>(null);
  const pendingRef = useRef<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [outline, setOutline] = useState<OutlineItem[]>([]);
  const [activePos, setActivePos] = useState<number | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [dirty, setDirty] = useState(false);

  /**
   * Capturé une seule fois : l'éditeur possède son propre état à partir de ce
   * HTML initial. Le réinjecter à chaque réponse serveur remonterait le
   * composant et ferait sauter le curseur en pleine frappe.
   */
  const initialHtmlRef = useRef<string | null>(null);
  if (doc && initialHtmlRef.current === null) {
    initialHtmlRef.current = initialHtmlOf(doc);
  }

  // Résolu côté serveur (nom du Document Type, ou nom du fichier importé
  // quand le document n'en a pas) — jamais recalculé côté client.
  const title = doc?.title ?? 'Document';

  const save = useCallback(async () => {
    if (pendingRef.current === null) return;
    const html = pendingRef.current;
    pendingRef.current = null;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    await updateDocumentContent(params.id, html);
    // Le cache n'est délibérément pas réécrit avec la réponse : `doc` ne doit
    // pas changer d'identité pendant l'édition, sous peine de remonter
    // l'éditeur (cf. initialHtmlRef).
    setSavedAt(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }));
    setDirty(false);
  }, [params.id]);

  const handleChange = useCallback(
    (html: string) => {
      pendingRef.current = html;
      setDirty(true);
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        save().catch((e: Error) =>
          toast.error('Échec de la sauvegarde automatique', { description: e.message }),
        );
      }, AUTOSAVE_DEBOUNCE_MS);
    },
    [save],
  );

  // Une sauvegarde en attente au démontage serait perdue en silence.
  useEffect(
    () => () => {
      if (timerRef.current) clearTimeout(timerRef.current);
      if (pendingRef.current !== null) {
        updateDocumentContent(params.id, pendingRef.current).catch(() => undefined);
      }
    },
    [params.id],
  );

  useEffect(() => {
    if (!dirty) return;
    const warn = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener('beforeunload', warn);
    return () => window.removeEventListener('beforeunload', warn);
  }, [dirty]);

  const saveMutation = useMutation({
    mutationFn: save,
    onSuccess: () => toast.success('Document sauvegardé.'),
    onError: (e: Error) => toast.error('La sauvegarde a échoué', { description: e.message }),
  });

  const finalizeMutation = useMutation({
    mutationFn: async () => {
      await save();
      return (await finalizeDocument(params.id)).data;
    },
    onSuccess: (updated) => {
      qc.setQueryData<AppDocument>(['document', params.id], (old: AppDocument | undefined) =>
        // `contentHtml` volontairement repris de l'état local : la réponse
        // serveur est identique, mais la remplacer ferait changer l'identité
        // du HTML initial de l'éditeur.
        old ? { ...updated, contentHtml: old.contentHtml } : updated,
      );
      toast.success('Document validé — vous pouvez l\'exporter.');
    },
    onError: (e: Error) => toast.error('La validation a échoué', { description: e.message }),
  });

  /**
   * Suggestion IA en attente de décision. Ses bornes sont celles de la
   * sélection au moment de la demande : c'est ce qui permet de l'appliquer au
   * bon endroit même si le curseur a bougé pendant la comparaison.
   */
  const [suggestion, setSuggestion] = useState<
    { selection: EditorSelection; text: string; confidence?: number } | null
  >(null);

  const improveMutation = useMutation({
    mutationFn: async (selection: EditorSelection) => ({
      selection,
      result: (await improveDocumentSelection(params.id, selection.text)).data,
    }),
    onSuccess: ({ selection, result }) =>
      setSuggestion({
        selection,
        text: result.aiSuggestedContent,
        confidence: result.confidenceScore,
      }),
    onError: (e: Error) => toast.error("L'amélioration IA a échoué", { description: e.message }),
  });

  function acceptSuggestion() {
    if (!suggestion) return;
    const { from, to } = suggestion.selection;
    editorRef.current?.replaceRange(from, to, suggestion.text);
    setSuggestion(null);
    toast.success('Suggestion appliquée.');
  }

  const exportMutation = useMutation({
    mutationFn: async (format: ExportFormat) => {
      // L'export est réassemblé côté serveur depuis le contenu stocké : sans
      // ce vidage, un paragraphe écrit il y a moins d'une seconde manquerait
      // dans le fichier téléchargé.
      pendingRef.current = editorRef.current?.getHtml() ?? pendingRef.current;
      await save();
      return { blob: await exportDocumentFile(params.id, format), format };
    },
    onSuccess: ({ blob, format }) => {
      const extension = format === 'Markdown' ? 'md' : format.toLowerCase();
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = `${title}.${extension}`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success(`Export ${format} terminé.`);
    },
    onError: (e: Error) => toast.error("L'export a échoué", { description: e.message }),
  });

  function goToHeading(item: OutlineItem) {
    setActivePos(item.pos);
    editorRef.current?.goTo(item.pos);
  }

  if (isLoading) {
    return (
      <div className="space-y-4 p-6 lg:p-8">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-[70vh] w-full" />
      </div>
    );
  }

  if (!doc) {
    return (
      <div className="mx-auto max-w-5xl p-6 text-center lg:p-8">
        <p className="text-muted-foreground">Document introuvable.</p>
        <Button asChild variant="outline" className="mt-4">
          <Link href="/history">Retour à l&apos;historique</Link>
        </Button>
      </div>
    );
  }

  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col gap-3 p-4 lg:p-6">
      <header className="flex flex-wrap items-center gap-2">
        <Button asChild variant="ghost" size="sm">
          <Link href="/history">
            <ArrowLeft className="mr-2 h-4 w-4" /> Historique
          </Link>
        </Button>
        <FileText className="h-5 w-5 text-primary" />
        <h1 className="truncate text-base font-semibold">{title}</h1>
        <DocumentStatusBadge status={doc.status} />
        <span className="text-xs text-muted-foreground">
          {dirty ? 'Modifications non enregistrées…' : savedAt ? `Enregistré à ${savedAt}` : 'Sauvegarde automatique active'}
        </span>

        <div className="ml-auto flex items-center gap-2">
          <Button variant="outline" size="sm" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
            {saveMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
            Sauvegarder
          </Button>
          <Button
            size="sm"

            disabled={finalizeMutation.isPending || doc.status === 'FINALISE'}
            onClick={() => finalizeMutation.mutate()}
          >
            {finalizeMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Check className="mr-2 h-4 w-4" />}
            {doc.status === 'FINALISE' ? 'Document validé' : 'Valider le document'}
          </Button>
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" disabled={exportMutation.isPending}>
                {exportMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileDown className="mr-2 h-4 w-4" />}
                Exporter
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end">
              <DropdownMenuItem onClick={() => exportMutation.mutate('DOCX')}>
                <FileDown className="mr-2 h-4 w-4" /> Word (.docx)
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => exportMutation.mutate('PDF')}>
                <FileDown className="mr-2 h-4 w-4" /> PDF (.pdf)
              </DropdownMenuItem>
              <DropdownMenuItem onClick={() => exportMutation.mutate('Markdown')}>
                <FileDown className="mr-2 h-4 w-4" /> Markdown (.md)
              </DropdownMenuItem>
              {doc.exportUrl && (
                <>
                  <DropdownMenuSeparator />
                  <DropdownMenuItem asChild>
                    <a href={doc.exportUrl} target="_blank" rel="noopener noreferrer">
                      <Download className="mr-2 h-4 w-4" /> Version archivée (DOCX)
                    </a>
                  </DropdownMenuItem>
                </>
              )}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      </header>

      <div className="grid min-h-0 flex-1 gap-3 lg:grid-cols-[248px_1fr]">
        <Card className="hidden min-h-0 flex-col overflow-hidden lg:flex">
          <div className="border-b border-border px-3 py-2">
            <p className="text-sm font-medium">Plan du document</p>
            <p className="text-xs text-muted-foreground">Construit à partir des titres</p>
          </div>
          <nav className="scrollbar-thin flex-1 overflow-y-auto p-2">
            {outline.length === 0 ? (
              <p className="px-2 py-4 text-xs text-muted-foreground">
                Aucun titre — utilisez les styles « Titre 1 » à « Titre 6 » pour structurer le document.
              </p>
            ) : (
              outline.map((item) => (
                <button
                  key={item.pos}
                  type="button"
                  onClick={() => goToHeading(item)}
                  style={{ paddingLeft: `${0.5 + (item.level - 1) * 0.6}rem` }}
                  className={cn(
                    'mb-0.5 block w-full truncate rounded-md py-1.5 pr-2 text-left text-xs transition-colors hover:bg-muted/60',
                    item.level === 1 && 'font-semibold',
                    activePos === item.pos && 'border-l-[3px] border-primary bg-primary/10',
                  )}
                >
                  {item.text || 'Titre sans texte'}
                </button>
              ))
            )}
          </nav>
        </Card>

        <div className="flex min-h-0 flex-col gap-3">
          <Card className="min-h-0 flex-1 overflow-hidden">
            <WordEditor
              ref={editorRef}
              key={doc.id}
              initialHtml={initialHtmlRef.current ?? '<p></p>'}
              onChange={handleChange}
              onOutlineChange={setOutline}
              onError={(message) => toast.error('Insertion impossible', { description: message })}
              onImproveSelection={(selection) => improveMutation.mutate(selection)}
              improving={improveMutation.isPending}
            />
          </Card>

          {suggestion && (
            <div className="shrink-0 space-y-1">
              {suggestion.confidence !== undefined && (
                <div className="flex items-center gap-2 text-xs text-muted-foreground">
                  Confiance auto-déclarée du modèle <ConfidencePill score={suggestion.confidence} />
                </div>
              )}
              <SectionSuggestionDiff
                before={suggestion.selection.text}
                after={suggestion.text}
                onAccept={acceptSuggestion}
                onReject={() => setSuggestion(null)}
              />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/**
 * Contenu à ouvrir dans l'éditeur. Les documents créés avant celui-ci n'ont pas
 * de `contentHtml` : leur contenu, réparti en Markdown section par section, est
 * reconstitué en HTML pour qu'ils restent modifiables et exportables au lieu de
 * s'ouvrir vides.
 */
function initialHtmlOf(doc: AppDocument): string {
  if (doc.contentHtml && doc.contentHtml.trim()) {
    return doc.contentHtml;
  }
  const sections = [...(doc.sections ?? [])].sort((a, b) => a.order - b.order);
  const html = sections.map(legacySectionToHtml).join('');
  return html || '<p></p>';
}

const LEGACY_HEADING_TAG: Partial<Record<DocumentSection['type'], string>> = {
  TITLE: 'h1',
  SUBTITLE: 'h2',
  SUB_SUBTITLE: 'h3',
};

function legacySectionToHtml(section: DocumentSection): string {
  const tag = LEGACY_HEADING_TAG[section.type];
  const label = escapeHtml(section.label);
  const body = markdownToHtml(section.userContent) || '<p></p>';
  return tag ? `<${tag}>${label}</${tag}>${body}` : body;
}

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}
