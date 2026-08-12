'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Check,
  ChevronDown,
  Download,
  FileDown,
  FileText,
  Loader2,
  Pencil,
  RefreshCw,
  Save,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
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
import { ConfidenceLegend, ConfidencePill } from '@/components/confidence-legend';
import { RichTextEditor } from '@/components/rich-text-editor';
import { useDocument, useDocumentTypes } from '@/lib/hooks/queries';
import {
  updateDocumentSection,
  improveDocumentSection,
  applySectionSuggestion,
  rejectSectionSuggestion,
  finalizeDocument,
  exportDocumentFile,
  type ExportFormat,
} from '@/lib/api/client';
import { sectionStatusLabel } from '@/lib/format';
import { buildSectionNumbers, confidenceThreshold, formatConfidence } from '@/lib/confidence';
import { markdownToHtml } from '@/lib/markdown-editor';
import { buildEmptyTableHtml } from '@/lib/markdown-table';
import { cn } from '@/lib/utils';
import type { AppDocument, DocumentSection, DocumentType } from '@/types';

const SECTION_TYPE_LABEL: Record<DocumentSection['type'], string> = {
  TITLE: 'Titre',
  SUBTITLE: 'Sous-titre',
  SUB_SUBTITLE: 'Sous-sous-titre',
  TABLE: 'Tableau',
  PARAGRAPH_PLACEHOLDER: 'Paragraphe',
};

const AUTOSAVE_DEBOUNCE_MS = 1000;

/**
 * Éditeur de squelette : plan du document à gauche (numérotation hiérarchique,
 * pastille de confiance par section, score global), sections dépliables à
 * droite. Une seule section est ouverte à la fois — c'est ce qui permet de
 * garder l'éditeur riche monté sur une seule section, et donc une sauvegarde
 * sans ambiguïté sur ce qui est en cours d'édition.
 */
export default function DocumentEditPage() {
  const params = useParams<{ id: string }>();
  const qc = useQueryClient();
  const { data: doc, isLoading } = useDocument(params.id);
  const { data: documentTypes } = useDocumentTypes();
  const [openId, setOpenId] = useState<string | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);
  /** Vidage immédiat de l'éditeur ouvert, appelé par le bouton "Sauvegarder" et avant la finalisation. */
  const flushRef = useRef<(() => Promise<void>) | null>(null);

  const sections = useMemo(() => [...(doc?.sections ?? [])].sort((a, b) => a.order - b.order), [doc]);
  const numbers = useMemo(() => buildSectionNumbers(sections), [sections]);

  useEffect(() => {
    if (!openId && sections.length > 0) setOpenId(sections[0].id);
  }, [sections, openId]);

  const docType = documentTypes?.find((d: DocumentType) => d.id === doc?.documentTypeId);
  const writtenCount = sections.filter((s) => s.status !== 'EMPTY').length;
  const progress = sections.length ? Math.round((100 * writtenCount) / sections.length) : 0;
  const globalConfidence = doc?.globalConfidenceScore;
  const globalThreshold = confidenceThreshold(globalConfidence);

  // Identité stable : `SectionCard` s'enregistre dans un effet, une nouvelle
  // fonction à chaque rendu le ferait se désenregistrer/réenregistrer en boucle.
  const registerFlush = useCallback((flush: (() => Promise<void>) | null) => {
    flushRef.current = flush;
  }, []);

  const patchSectionInCache = useCallback(
    (updated: DocumentSection) => {
      qc.setQueryData<AppDocument>(['document', params.id], (old: AppDocument | undefined) =>
        old ? { ...old, sections: old.sections.map((s: DocumentSection) => (s.id === updated.id ? updated : s)) } : old,
      );
    },
    [qc, params.id],
  );

  const improveMutation = useMutation({
    mutationFn: async (sectionId: string) => (await improveDocumentSection(params.id, sectionId)).data,
    onSuccess: (result, sectionId) => {
      const current = sections.find((s) => s.id === sectionId);
      if (current) {
        patchSectionInCache({
          ...current,
          aiSuggestedContent: result.aiSuggestedContent,
          confidenceScore: result.confidenceScore,
        });
      }
      toast.success('Nouvelle proposition IA — comparez avant d\'accepter.');
    },
    onError: (e: Error) => toast.error('La régénération a échoué', { description: e.message }),
  });

  const applyMutation = useMutation({
    mutationFn: async (sectionId: string) => (await applySectionSuggestion(params.id, sectionId)).data,
    onSuccess: (updated) => {
      patchSectionInCache(updated);
      toast.success('Suggestion acceptée.');
    },
    onError: (e: Error) => toast.error("Impossible d'accepter la suggestion", { description: e.message }),
  });

  const rejectMutation = useMutation({
    mutationFn: async (sectionId: string) => (await rejectSectionSuggestion(params.id, sectionId)).data,
    onSuccess: (updated) => patchSectionInCache(updated),
  });

  const finalizeMutation = useMutation({
    mutationFn: async () => {
      await flushRef.current?.();
      return (await finalizeDocument(params.id)).data;
    },
    onSuccess: (updated) => {
      qc.setQueryData(['document', params.id], updated);
      toast.success('Génération validée — vous pouvez exporter le document.');
    },
    onError: (e: Error) => toast.error('La validation a échoué', { description: e.message }),
  });

  const saveMutation = useMutation({
    mutationFn: async () => {
      await flushRef.current?.();
    },
    onSuccess: () => toast.success('Document sauvegardé.'),
    onError: (e: Error) => toast.error('La sauvegarde a échoué', { description: e.message }),
  });

  const exportMutation = useMutation({
    mutationFn: async (format: ExportFormat) => {
      await flushRef.current?.();
      return { blob: await exportDocumentFile(params.id, format), format };
    },
    onSuccess: ({ blob, format }) => {
      const extension = format === 'Markdown' ? 'md' : format.toLowerCase();
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement('a');
      link.href = url;
      link.download = `${docType?.name ?? 'document'}.${extension}`;
      link.click();
      URL.revokeObjectURL(url);
      toast.success(`Export ${format} terminé.`);
    },
    onError: (e: Error) => toast.error("L'export a échoué", { description: e.message }),
  });

  function selectSection(id: string) {
    setOpenId((current) => (current === id ? null : id));
    setEditingId(null);
    // La carte peut être hors écran quand la sélection vient du plan.
    requestAnimationFrame(() => {
      window.document.getElementById(`section-${id}`)?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
    });
  }

  if (isLoading) {
    return (
      <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
        <Skeleton className="h-12 w-full" />
        <Skeleton className="h-96 w-full" />
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
    <div className="mx-auto flex max-w-7xl flex-col gap-4 p-6 lg:p-8">
      <PageHeader
        title="Éditeur de squelette"
        description={docType?.name ?? 'Document'}
        actions={
          <div className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm">
              <Link href="/history">
                <ArrowLeft className="mr-2 h-4 w-4" /> Historique
              </Link>
            </Button>
            <Button variant="outline" size="sm" disabled={saveMutation.isPending} onClick={() => saveMutation.mutate()}>
              {saveMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />}
              Sauvegarder
            </Button>
            <Button
              size="sm"
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              disabled={finalizeMutation.isPending || doc.status === 'FINALISE'}
              onClick={() => finalizeMutation.mutate()}
            >
              {finalizeMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Check className="mr-2 h-4 w-4" />}
              {doc.status === 'FINALISE' ? 'Génération validée' : 'Valider la génération'}
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
                  <FileDown className="mr-2 h-4 w-4" /> DOCX (Word)
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => exportMutation.mutate('PDF')}>
                  <FileDown className="mr-2 h-4 w-4" /> PDF
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => exportMutation.mutate('Markdown')}>
                  <FileDown className="mr-2 h-4 w-4" /> Markdown
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
        }
      />

      <ConfidenceLegend />

      <div className="grid gap-4 lg:h-[calc(100vh-19rem)] lg:min-h-[32rem] lg:grid-cols-[280px_1fr]">
        {/* Plan du document */}
        <Card className="flex flex-col overflow-hidden">
          <div className="space-y-2 border-b border-border p-4">
            <p className="text-sm font-medium">Plan du document</p>
            <div className="flex items-center gap-2">
              <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted">
                <div
                  className={cn('h-full rounded-full transition-all', globalThreshold.dot)}
                  style={{ width: `${globalConfidence ?? 0}%` }}
                />
              </div>
              <span className={cn('text-xs font-semibold', globalThreshold.text)}>{formatConfidence(globalConfidence)}</span>
            </div>
            <p className="text-xs text-muted-foreground">
              Score de confiance global
              {globalConfidence === undefined && ' — aucune section évaluée par l\'IA'}
            </p>
            <p className="text-xs text-muted-foreground">
              {writtenCount}/{sections.length} sections rédigées ({progress} %)
            </p>
          </div>
          <div className="scrollbar-thin flex-1 overflow-y-auto p-2">
            {sections.map((section) => {
              const threshold = confidenceThreshold(section.confidenceScore);
              return (
                <button
                  key={section.id}
                  type="button"
                  onClick={() => selectSection(section.id)}
                  className={cn(
                    'mb-0.5 flex w-full items-center gap-2 rounded-md px-2 py-2 text-left transition-colors hover:bg-muted/60',
                    openId === section.id && 'border-l-[3px] border-primary bg-primary/10',
                  )}
                >
                  <span className="w-8 flex-none text-[10px] font-bold text-muted-foreground">{numbers.get(section.id)}</span>
                  <span className="flex-1 truncate text-xs">{section.label}</span>
                  <span
                    className={cn('h-2.5 w-2.5 flex-none rounded-[3px]', threshold.dot)}
                    title={`${threshold.interpretation} — ${formatConfidence(section.confidenceScore)}`}
                  />
                </button>
              );
            })}
          </div>
        </Card>

        {/* Corps de l'éditeur */}
        <Card className="flex flex-col overflow-hidden">
          <div className="flex items-center gap-2 border-b border-border p-3">
            <FileText className="h-5 w-5 text-primary" />
            <span className="flex-1 truncate text-sm font-semibold">{docType?.name ?? 'Document'}</span>
            <DocumentStatusBadge status={doc.status} />
          </div>
          <div className="scrollbar-thin flex-1 space-y-3 overflow-y-auto p-4">
            {sections.length === 0 && (
              <p className="py-8 text-center text-sm text-muted-foreground">
                Ce document n&apos;a aucune section — vérifiez la structure du Document Type.
              </p>
            )}
            {sections.map((section) => (
              <SectionCard
                key={section.id}
                documentId={params.id}
                section={section}
                number={numbers.get(section.id) ?? ''}
                open={openId === section.id}
                editing={editingId === section.id}
                onToggle={() => selectSection(section.id)}
                onToggleEditing={() => setEditingId((current) => (current === section.id ? null : section.id))}
                onSaved={patchSectionInCache}
                registerFlush={registerFlush}
                onRegenerate={() => improveMutation.mutate(section.id)}
                regenerating={improveMutation.isPending && improveMutation.variables === section.id}
                onAccept={() => applyMutation.mutate(section.id)}
                onReject={() => rejectMutation.mutate(section.id)}
                accepting={applyMutation.isPending && applyMutation.variables === section.id}
              />
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}

function SectionCard({
  documentId,
  section,
  number,
  open,
  editing,
  onToggle,
  onToggleEditing,
  onSaved,
  registerFlush,
  onRegenerate,
  regenerating,
  onAccept,
  onReject,
  accepting,
}: {
  documentId: string;
  section: DocumentSection;
  number: string;
  open: boolean;
  editing: boolean;
  onToggle: () => void;
  onToggleEditing: () => void;
  onSaved: (updated: DocumentSection) => void;
  registerFlush: (flush: (() => Promise<void>) | null) => void;
  onRegenerate: () => void;
  regenerating: boolean;
  onAccept: () => void;
  onReject: () => void;
  accepting: boolean;
}) {
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const pendingRef = useRef<string | null>(null);
  const isTable = section.type === 'TABLE';

  const flush = useCallback(async () => {
    if (pendingRef.current === null) return;
    const content = pendingRef.current;
    pendingRef.current = null;
    if (timerRef.current) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }
    try {
      const res = await updateDocumentSection(documentId, section.id, content);
      onSaved(res.data);
      setSavedAt(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }));
    } catch (e) {
      toast.error('Échec de la sauvegarde automatique', { description: e instanceof Error ? e.message : undefined });
    }
  }, [documentId, section.id, onSaved]);

  // Le bouton "Sauvegarder" de l'en-tête doit vider l'éditeur réellement ouvert :
  // seule la section en cours d'édition s'enregistre comme cible.
  useEffect(() => {
    if (!editing) return;
    registerFlush(flush);
    return () => {
      flush();
      registerFlush(null);
    };
  }, [editing, flush, registerFlush]);

  const handleChange = useCallback(
    (markdown: string) => {
      pendingRef.current = markdown;
      if (timerRef.current) clearTimeout(timerRef.current);
      timerRef.current = setTimeout(() => {
        flush();
      }, AUTOSAVE_DEBOUNCE_MS);
    },
    [flush],
  );

  // Une section TABLE s'édite comme un vrai tableau : celui déjà rempli, ou un
  // tableau vide dont l'en-tête reprend les colonnes attendues du Document Type.
  const initialHtml = isTable
    ? markdownToHtml(section.userContent) || buildEmptyTableHtml(section.tableColumns)
    : markdownToHtml(section.userContent);

  const hasSuggestion = Boolean(section.aiSuggestedContent);
  const hasContent = Boolean(section.userContent?.trim());

  return (
    <div
      id={`section-${section.id}`}
      className={cn('rounded-lg border transition-colors', open ? 'border-primary' : 'border-border hover:border-primary/50')}
    >
      <button
        type="button"
        onClick={onToggle}
        aria-expanded={open}
        className="flex w-full items-center gap-3 p-3 text-left"
      >
        <span className="w-8 flex-none text-[11px] font-bold text-muted-foreground">{number}.</span>
        <span className="flex-1 truncate text-sm font-semibold">{section.label}</span>
        <Badge variant="outline" className="hidden font-normal sm:inline-flex">
          {SECTION_TYPE_LABEL[section.type]}
        </Badge>
        <ConfidencePill score={section.confidenceScore} />
        <ChevronDown className={cn('h-4 w-4 flex-none text-muted-foreground transition-transform', open && 'rotate-180')} />
      </button>

      {open && (
        <div className="space-y-3 border-t border-border p-3">
          <div className="flex flex-wrap items-center gap-2 text-xs text-muted-foreground">
            <span>{sectionStatusLabel[section.status]}</span>
            {savedAt && (
              <span className="flex items-center gap-1">
                <Save className="h-3 w-3" /> Enregistré à {savedAt}
              </span>
            )}
            {editing && !savedAt && <span>Sauvegarde automatique active</span>}
          </div>

          {editing ? (
            <RichTextEditor
              key={section.id}
              variant={isTable ? 'table' : 'prose'}
              initialHtml={initialHtml}
              placeholder={
                isTable
                  ? 'Remplissez les lignes du tableau…'
                  : 'Rédigez le contenu de cette section…'
              }
              onChange={handleChange}
              onBlur={flush}
            />
          ) : (
            <SectionPreview markdown={section.userContent} />
          )}

          <div className="flex flex-wrap gap-2">
            <Button type="button" variant="outline" size="sm" disabled={!hasSuggestion || accepting} onClick={onAccept}>
              {accepting ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <Check className="mr-1.5 h-3.5 w-3.5" />}
              Accepter
            </Button>
            {/* "Régénérer" reformule le contenu déjà écrit : sans texte de départ, l'IA n'aurait rien à reprendre. */}
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={regenerating || !hasContent || isTable}
              title={
                isTable
                  ? "L'amélioration IA reformule de la prose et casserait la syntaxe du tableau."
                  : hasContent
                    ? undefined
                    : 'Rédigez d\'abord un contenu à reformuler.'
              }
              onClick={onRegenerate}
            >
              {regenerating ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <RefreshCw className="mr-1.5 h-3.5 w-3.5" />}
              Régénérer
            </Button>
            <Button type="button" variant={editing ? 'secondary' : 'outline'} size="sm" onClick={onToggleEditing}>
              <Pencil className="mr-1.5 h-3.5 w-3.5" />
              {editing ? 'Terminer' : 'Modifier'}
            </Button>
          </div>

          {section.aiSuggestedContent && (
            <SectionSuggestionDiff
              before={section.userContent ?? ''}
              after={section.aiSuggestedContent}
              onAccept={onAccept}
              onReject={onReject}
              pending={accepting}
            />
          )}
        </div>
      )}
    </div>
  );
}

/**
 * Aperçu du contenu retenu. Le Markdown est rendu en HTML par
 * {@link markdownToHtml}, qui échappe tout le texte et ne produit que ses
 * propres balises — rien de ce que contient la section n'est interprété comme
 * du HTML.
 */
function SectionPreview({ markdown }: { markdown?: string }) {
  if (!markdown?.trim()) {
    return (
      <p className="rounded-md border border-dashed border-border p-4 text-center text-sm text-muted-foreground">
        Section vide — cliquez sur « Modifier » pour la rédiger.
      </p>
    );
  }
  return (
    <div
      className="prose-editor max-w-none rounded-md border border-border p-3 text-sm [&_table]:w-full [&_table]:border-collapse [&_th]:border [&_th]:border-border [&_th]:bg-muted [&_th]:px-3 [&_th]:py-1.5 [&_th]:text-left [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-1.5"
      dangerouslySetInnerHTML={{ __html: markdownToHtml(markdown) }}
    />
  );
}
