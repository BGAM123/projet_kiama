'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Bot,
  ChevronDown,
  ChevronRight,
  Cpu,
  Download,
  FileDown,
  FileText,
  Globe,
  List,
  Loader2,
  MessageSquare,
  PanelRightClose,
  PanelRightOpen,
  Pencil,
  Plus,
  Search,
  Send,
  Sparkles,
  Table2,
  Type,
  User as UserIcon,
  X,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Progress } from '@/components/ui/progress';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth-store';
import {
  useConversations,
  useMessages,
  useRefDocuments,
  useDocumentTypes,
  useStructure,
  useGenerations,
  useAiConfigs,
} from '@/lib/hooks/queries';
import {
  createConversation,
  sendMessage,
  addReferenceDocument,
  startGeneration,
  getGeneration,
  updateGeneration,
  exportDocument,
} from '@/lib/api/client';
import { streamGeneration, getStructureFor } from '@/lib/api/generator';
import { languageLabel, toneLabel, targetLengthLabel, relativeTime } from '@/lib/format';
import type {
  AiModelConfig,
  Conversation,
  DocumentType,
  GeneratedDocument,
  Language,
  Message,
  ReferenceDocument,
  StructureNode,
  TargetLength,
  Tone,
} from '@/types';

const nodeIcon: Record<StructureNode['type'], React.ComponentType<{ className?: string }>> = {
  heading: Type,
  paragraph: Type,
  table: Table2,
  list: List,
  cover: FileText,
};

type MobilePanel = 'conversations' | 'settings' | null;

export default function GenerateDocumentPage() {
  const router = useRouter();
  const session = useAuth((s) => s.session);
  const qc = useQueryClient();
  const { data: conversations, isLoading: convLoading } = useConversations();
  const { data: documentTypes } = useDocumentTypes();
  const { data: allGenerations } = useGenerations();
  const { data: aiConfigs } = useAiConfigs();

  const [activeId, setActiveId] = useState<string | null>(null);
  const [documentTypeId, setDocumentTypeId] = useState<string>('');
  const [language, setLanguage] = useState<Language>('FR');
  const [tone, setTone] = useState<Tone>('FORMEL');
  const [targetLength, setTargetLength] = useState<TargetLength>('LONG');
  const [aiConfigId, setAiConfigId] = useState<string>('');
  const [contentPivot, setContentPivot] = useState('');
  const [input, setInput] = useState('');
  const [generating, setGenerating] = useState(false);
  const [genProgress, setGenProgress] = useState(0);
  const [currentSection, setCurrentSection] = useState<string | null>(null);
  const [activeGen, setActiveGen] = useState<GeneratedDocument | null>(null);
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());
  const [showStructure, setShowStructure] = useState(true);
  const [rightPanelOpen, setRightPanelOpen] = useState(true);
  const [conversationSearch, setConversationSearch] = useState('');
  const [exporting, setExporting] = useState<string | null>(null);
  const [mobilePanel, setMobilePanel] = useState<MobilePanel>(null);
  const chatFileRef = useRef<HTMLInputElement>(null);
  const settingsFileRef = useRef<HTMLInputElement>(null);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const { data: messages } = useMessages(activeId);
  const { data: refDocs } = useRefDocuments(activeId);
  const convList: Conversation[] = conversations ?? [];

  const activeConv = convList.find((c: Conversation) => c.id === activeId);
  const activeDocTypeId = documentTypeId || activeConv?.documentTypeId || '';

  const { data: structure } = useStructure(activeDocTypeId || null);
  const structureTree: StructureNode[] = structure?.tree ?? [];

  // Dernière génération connue pour la conversation active : repli sur
  // l'historique serveur (useGenerations) quand aucune génération n'a été
  // lancée pendant cette session — permet d'afficher l'aperçu du document
  // en rouvrant une conversation déjà générée, sans re-générer.
  const conversationGen: GeneratedDocument | null =
    activeGen ??
    (allGenerations ?? [])
      .filter((g: GeneratedDocument) => g.conversationId === activeId)
      .sort((a: GeneratedDocument, b: GeneratedDocument) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())[0] ??
    null;

  const activeAiConfigs = (aiConfigs ?? []).filter((c: AiModelConfig) => c.active);
  const selectedAiConfig =
    activeAiConfigs.find((c: AiModelConfig) => c.id === aiConfigId) ??
    activeAiConfigs.find((c: AiModelConfig) => c.isDefault) ??
    activeAiConfigs[0];

  function conversationDateGroup(iso: string): string {
    const now = new Date();
    const d = new Date(iso);
    const startOfDay = (date: Date) => new Date(date.getFullYear(), date.getMonth(), date.getDate());
    const dayDiff = Math.floor((startOfDay(now).getTime() - startOfDay(d).getTime()) / 86400000);
    if (dayDiff <= 0) return "Aujourd'hui";
    if (dayDiff <= 7) return 'Cette semaine';
    if (dayDiff <= 30) return 'Ce mois';
    return 'Plus ancien';
  }

  const groupOrder = ["Aujourd'hui", 'Cette semaine', 'Ce mois', 'Plus ancien'];
  const filteredConvList = convList.filter((c) =>
    c.title.toLowerCase().includes(conversationSearch.trim().toLowerCase()),
  );
  const conversationGroups = groupOrder
    .map((label) => ({
      label,
      items: filteredConvList.filter((c) => conversationDateGroup(c.createdAt) === label),
    }))
    .filter((g) => g.items.length > 0);

  const liveSections = activeGen?.sections ?? [];
  // Contenu de la section en cours de génération, accumulé fragment par
  // fragment via les événements SSE `delta` (cf. lib/api/generator.ts) —
  // affiché en direct pendant que le texte s'écrit, comme chez Claude.
  const liveSectionContent = liveSections.find((s) => s.status === 'GENERATING')?.content || '';

  useEffect(() => {
    if (!activeId && convList.length) setActiveId(convList[0].id);
  }, [convList, activeId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    // activeGen dans les dépendances : suit aussi le défilement pendant la
    // génération en direct (le contenu de la section en cours s'allonge à
    // chaque `delta`), pas seulement à l'arrivée d'un nouveau message.
  }, [messages, activeGen]);

  useEffect(() => {
    if (structureTree.length) {
      setExpandedNodes(new Set(structureTree.map((n) => n.id)));
    }
  }, [structureTree.length]);

  useEffect(() => {
    if (mobilePanel) {
      document.body.style.overflow = 'hidden';
      return () => { document.body.style.overflow = ''; };
    }
  }, [mobilePanel]);

  const createConv = useMutation({
    mutationFn: async () =>
      (await createConversation({
        userId: session!.user.id,
        documentTypeId: documentTypeId || documentTypes?.find((d: DocumentType) => d.status === 'ACTIF')?.id || '',
        title: contentPivot || 'Nouvelle conversation',
      })).data,
    onSuccess: (c: Conversation) => {
      qc.invalidateQueries({ queryKey: ['conversations'] });
      setActiveId(c.id);
      toast.success('Nouvelle conversation créée.');
    },
  });

  const sendMsg = useMutation({
    mutationFn: async (content: string) => (await sendMessage(activeId!, content)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['messages', activeId] });
      setInput('');
    },
  });

  const addRef = useMutation({
    mutationFn: async (file: File) => (await addReferenceDocument(activeId!, file)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ref-docs', activeId] }),
  });

  async function handleSend() {
    if (!input.trim()) return;
    if (!activeId) {
      await createConv.mutateAsync();
    }
    if (!activeId) return;
    sendMsg.mutate(input.trim());
  }

  async function handleFiles(files: FileList | null) {
    if (!files || !activeId) {
      if (!activeId) toast.error('Créez d\'abord une conversation.');
      return;
    }
    if ((refDocs?.length ?? 0) + files.length > 10) {
      toast.error('Maximum 10 documents de référence.');
      return;
    }
    for (const f of Array.from(files)) {
      await addRef.mutateAsync(f);
    }
    toast.success(`${files.length} document(s) de référence ajouté(s).`);
  }

  async function handleGenerate() {
    if (!activeId) {
      toast.error('Créez ou sélectionnez une conversation d\'abord.');
      return;
    }
    const dtId = documentTypeId || activeConv?.documentTypeId;
    if (!dtId) {
      toast.error('Sélectionnez un Document Type.');
      return;
    }
    setGenerating(true);
    setGenProgress(0);
    setActiveGen(null);
    try {
      const doc = (await startGeneration({
        conversationId: activeId,
        documentTypeId: dtId,
        userId: session!.user.id,
        language,
        tone,
        targetLength,
        contentPivot: contentPivot || 'Document',
      })).data;
      setActiveGen(doc);
      const total = doc.sections.length;
      // GENERATION_IN_PROGRESS (verrou Redis, cf. GenerationLockService) : le flux
      // se termine juste après cet événement, sans `done` — on le signale
      // explicitement plutôt que de laisser la boucle finir en silence, ce qui
      // afficherait ensuite un toast "Document généré" trompeur.
      let conflict = false;
      for await (const evt of streamGeneration(doc, (patch) => {
        setActiveGen((prev) => (prev ? { ...prev, ...patch } : prev));
      })) {
        if (evt.type === 'progress') {
          setCurrentSection(evt.sectionLabel ?? null);
          setGenProgress(Math.round(((evt.sectionIndex ?? 0) / Math.max(total, 1)) * 100));
        } else if (evt.type === 'section') {
          setGenProgress(Math.round(((evt.sectionIndex ?? 0 + 1) / Math.max(total, 1)) * 100));
        } else if (evt.type === 'done') {
          setGenProgress(100);
        } else if (evt.type === 'error') {
          conflict = true;
          toast.error('Génération interrompue', {
            description: evt.message ?? 'Une génération est déjà en cours pour ce document.',
          });
        }
      }
      // Le statut final (GENERE, ou ECHEC si une section a échoué) est déjà
      // positionné côté serveur à la fin du flux SSE — on relit l'état réel
      // plutôt que de le forcer nous-mêmes (le PATCH /generations/{id} ne
      // sert qu'à l'édition manuelle et écraserait ce statut par EN_EDITION).
      const finalDoc = (await getGeneration(doc.id)).data;
      setActiveGen(finalDoc);
      qc.invalidateQueries({ queryKey: ['generation', doc.id] });
      if (conflict) {
        // Toast déjà affiché ci-dessus ; on a seulement resynchronisé l'état réel du document, rien de nouveau à ouvrir.
      } else if (finalDoc?.status === 'ECHEC') {
        toast.error('La génération a échoué pour une ou plusieurs sections.', {
          description: 'Le contenu déjà généré reste disponible dans l\'éditeur.',
        });
        router.push(`/editor/${doc.id}`);
      } else {
        toast.success('Document généré', { description: 'Vous pouvez l\'éditer ou l\'exporter.' });
        // Bascule directe vers l'éditeur (plutôt qu'un clic manuel sur "Éditer") :
        // c'est là que le contenu généré peut être corrigé puis exporté en DOCX/PDF/Markdown.
        router.push(`/editor/${doc.id}`);
      }
    } catch (e) {
      toast.error('La génération a échoué.', { description: e instanceof Error ? e.message : undefined });
    } finally {
      setGenerating(false);
      setCurrentSection(null);
      setMobilePanel(null);
    }
  }

  async function handleExport(format: 'DOCX' | 'PDF' | 'Markdown') {
    if (!conversationGen) return;
    setExporting(format);
    try {
      // Export réel via POST /api/v1/export — remplace la simulation locale
      // qui téléchargeait du texte brut renommé en .pdf/.docx (écart 1.13
      // du rapport d'écarts). Le backend renvoie un vrai binaire.
      const blob = await exportDocument({
        title: conversationGen.contentPivot || 'document',
        content: conversationGen.content || '',
        format,
        headerText: structure?.headerText,
        footerText: structure?.footerText,
      });
      const ext = format === 'Markdown' ? 'md' : format.toLowerCase();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${conversationGen.contentPivot ?? 'document'}.${ext}`;
      a.click();
      URL.revokeObjectURL(url);
      await updateGeneration(conversationGen.id, { status: 'EXPORTE' });
      toast.success(`Export ${format} terminé`, { description: 'Le fichier a été téléchargé.' });
    } catch (e) {
      toast.error(`L'export ${format} a échoué`, { description: e instanceof Error ? e.message : undefined });
    } finally {
      setExporting(null);
    }
  }

  function toggleNode(id: string) {
    setExpandedNodes((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function sectionStatus(label: string): 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED' | 'NONE' {
    if (!activeGen) return 'NONE';
    const sec = liveSections.find((s) => s.label === label);
    if (!sec) return 'NONE';
    return sec.status;
  }

  const docTypeName = documentTypes?.find((d: DocumentType) => d.id === activeDocTypeId)?.name;
  const hasGeneratedDoc = !!conversationGen && !generating;

  /* ---------------- Conversations panel (shared) ---------------- */
  const ConversationsPanel = (
    <div className="flex h-full flex-col bg-card">
      <div className="flex items-center justify-between p-3 lg:hidden">
        <span className="text-sm font-semibold">Conversations</span>
        <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Fermer" onClick={() => setMobilePanel(null)}>
          <X className="h-4 w-4" />
        </Button>
      </div>
      <div className="space-y-2 p-3">
        <Button className="w-full bg-accent text-accent-foreground hover:bg-accent/90" onClick={() => createConv.mutate()} disabled={createConv.isPending}>
          <Plus className="mr-2 h-4 w-4" /> Nouvelle conversation
        </Button>
        <div className="relative">
          <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            value={conversationSearch}
            onChange={(e) => setConversationSearch(e.target.value)}
            placeholder="Rechercher…"
            aria-label="Rechercher une conversation"
            className="h-9 pl-8 text-sm"
          />
        </div>
      </div>
      <ScrollArea className="flex-1">
        <div className="space-y-4 p-2 pb-4">
          {convLoading ? (
            <p className="p-3 text-sm text-muted-foreground">Chargement…</p>
          ) : !filteredConvList.length ? (
            <p className="p-3 text-sm text-muted-foreground">
              {conversationSearch ? 'Aucun résultat.' : 'Aucune conversation.'}
            </p>
          ) : (
            conversationGroups.map((group) => (
              <div key={group.label}>
                <p className="px-2 pb-1 text-xs font-semibold uppercase tracking-wide text-muted-foreground">
                  {group.label}
                </p>
                <ul className="space-y-1">
                  {group.items.map((c: Conversation) => {
                    const dtName = documentTypes?.find((d: DocumentType) => d.id === c.documentTypeId)?.name;
                    return (
                      <li key={c.id}>
                        <button
                          onClick={() => { setActiveId(c.id); setMobilePanel(null); }}
                          className={cn(
                            'w-full rounded-md px-3 py-2 text-left text-sm transition-colors',
                            activeId === c.id ? 'bg-primary text-primary-foreground' : 'hover:bg-muted',
                          )}
                        >
                          <p className="truncate font-medium">{c.title}</p>
                          <p className={cn('truncate text-xs', activeId === c.id ? 'text-primary-foreground/70' : 'text-muted-foreground')}>
                            {dtName ? `${dtName} · ${relativeTime(c.createdAt)}` : relativeTime(c.createdAt)}
                          </p>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              </div>
            ))
          )}
        </div>
      </ScrollArea>
    </div>
  );

  /* ---------------- Settings panel (shared) ---------------- */
  const SettingsPanel = (
    <div className="flex h-full flex-col bg-card">
      <div className="flex items-center justify-between border-b border-border p-4 lg:hidden">
        <span className="text-sm font-semibold">Paramètres & sections</span>
        <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Fermer" onClick={() => setMobilePanel(null)}>
          <X className="h-4 w-4" />
        </Button>
      </div>
      <div className="hidden items-center justify-between border-b border-border p-4 lg:flex">
        <span className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Paramètres</span>
        <Button
          variant="ghost"
          size="icon"
          className="h-7 w-7 text-muted-foreground hover:text-foreground"
          aria-label="Réduire le panneau"
          onClick={() => setRightPanelOpen(false)}
        >
          <PanelRightClose className="h-4 w-4" />
        </Button>
      </div>

      <div className="flex-1 overflow-y-auto p-4">
        <div className="space-y-5">
          <div className="space-y-2">
            <Label htmlFor="dt" className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Document Type</Label>
            <Select value={documentTypeId} onValueChange={setDocumentTypeId}>
              <SelectTrigger id="dt"><SelectValue placeholder="Sélectionner" /></SelectTrigger>
              <SelectContent>
                {documentTypes?.filter((d: DocumentType) => d.status === 'ACTIF').map((d: DocumentType) => <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>)}
              </SelectContent>
            </Select>
            <button
              type="button"
              className="flex items-center gap-1 text-xs font-medium text-accent hover:underline disabled:cursor-not-allowed disabled:text-muted-foreground disabled:no-underline"
              onClick={() => setShowStructure((s) => !s)}
              disabled={!structureTree.length}
              aria-expanded={showStructure}
            >
              {showStructure ? 'Masquer la structure' : 'Voir la structure'}
              {showStructure ? <ChevronDown className="h-3 w-3" /> : <ChevronRight className="h-3 w-3" />}
            </button>
            {showStructure && (
              !structureTree.length ? (
                <p className="rounded-md bg-muted/40 px-3 py-4 text-center text-xs text-muted-foreground">
                  Aucune structure à afficher. Choisissez un Document Type.
                </p>
              ) : (
                <ScrollArea className="max-h-64 rounded-md border border-border/60">
                  <ul className="space-y-0.5 p-1.5">
                    {structureTree.map((node) => (
                      <SectionRow
                        key={node.id}
                        node={node}
                        depth={0}
                        expandedNodes={expandedNodes}
                        onToggle={toggleNode}
                        sectionStatus={sectionStatus}
                        generating={generating}
                        currentSection={currentSection}
                      />
                    ))}
                  </ul>
                </ScrollArea>
              )
            )}
          </div>

          <div className="space-y-2">
            <Label htmlFor="pivot" className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Sujet / pivot de contenu</Label>
            <Input id="pivot" placeholder="Ex. Rapport Q2 2026" value={contentPivot} onChange={(e) => setContentPivot(e.target.value)} />
          </div>

          <div className="space-y-2">
            <Label htmlFor="lang" className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Langue</Label>
            <Select value={language} onValueChange={(v) => setLanguage(v as Language)}>
              <SelectTrigger id="lang"><SelectValue /></SelectTrigger>
              <SelectContent>
                {Object.entries(languageLabel).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
              </SelectContent>
            </Select>
          </div>

          <div className="space-y-2">
            <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Ton</Label>
            <div className="flex flex-wrap gap-2">
              {Object.entries(toneLabel).map(([k, v]) => (
                <SegButton key={k} active={tone === k} onClick={() => setTone(k as Tone)}>{v}</SegButton>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Longueur cible</Label>
            <div className="flex flex-col gap-2">
              {Object.entries(targetLengthLabel).map(([k, v]) => (
                <SegButton key={k} active={targetLength === k} onClick={() => setTargetLength(k as TargetLength)} full>{v}</SegButton>
              ))}
            </div>
          </div>

          <div className="space-y-2">
            <Label htmlFor="model" className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Modèle IA</Label>
            <Select value={selectedAiConfig?.id ?? ''} onValueChange={setAiConfigId}>
              <SelectTrigger id="model"><SelectValue placeholder="Modèle par défaut" /></SelectTrigger>
              <SelectContent>
                {activeAiConfigs.map((c: AiModelConfig) => (
                  <SelectItem key={c.id} value={c.id}>{c.modelName}{c.isDefault ? ' (Défaut)' : ''}</SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>

          <Separator />

          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">Documents de référence</Label>
              <span className="text-xs text-muted-foreground">{refDocs?.length ?? 0}/10</span>
            </div>
            <input ref={settingsFileRef} type="file" multiple className="hidden" accept=".doc,.docx,.pdf,.md,.txt,.xlsx,.xls,.csv" onChange={(e) => handleFiles(e.target.files)} />
            <Button variant="outline" size="sm" className="w-full border-dashed" onClick={() => settingsFileRef.current?.click()} disabled={!activeId}>
              <Plus className="mr-2 h-4 w-4" /> Importer des fichiers
            </Button>
            {refDocs?.length ? (
              <ul className="space-y-1">
                {refDocs.map((r: ReferenceDocument) => (
                  <li key={r.id} className="flex items-center gap-2 rounded-md bg-muted/50 px-2 py-1.5 text-xs">
                    <FileText className="h-3 w-3 flex-none text-muted-foreground" />
                    <span className="truncate">{r.fileName}</span>
                  </li>
                ))}
              </ul>
            ) : null}
          </div>
        </div>
      </div>

      {/* Bottom actions */}
      <div className="border-t border-border p-4">
        <Button onClick={handleGenerate} disabled={generating} className="w-full bg-accent text-accent-foreground hover:bg-accent/90">
          {generating ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Génération…</> : <><Sparkles className="mr-2 h-4 w-4" /> Générer le document</>}
        </Button>
        {hasGeneratedDoc && conversationGen && (
          <div className="mt-2 grid grid-cols-2 gap-2">
            <Button variant="outline" size="sm" onClick={() => router.push(`/editor/${conversationGen.id}`)}>
              <Pencil className="mr-1.5 h-3.5 w-3.5" /> Éditer
            </Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="outline" size="sm" disabled={!!exporting}>
                  {exporting ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <FileDown className="mr-1.5 h-3.5 w-3.5" />}
                  Exporter
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="start" className="w-full">
                {conversationGen.exportUrl && (
                  <>
                    <DropdownMenuItem asChild>
                      <a href={conversationGen.exportUrl} target="_blank" rel="noopener noreferrer">
                        <Download className="mr-2 h-4 w-4" /> Télécharger (Word)
                      </a>
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                  </>
                )}
                <DropdownMenuItem onClick={() => handleExport('DOCX')}><FileDown className="mr-2 h-4 w-4" /> DOCX</DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('PDF')}><FileDown className="mr-2 h-4 w-4" /> PDF</DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('Markdown')}><FileDown className="mr-2 h-4 w-4" /> Markdown</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        )}
      </div>
    </div>
  );

  const CollapsedSettingsRail = (
    <div className="hidden flex-col items-center border-l border-border bg-card py-4 lg:flex">
      <Button
        variant="ghost"
        size="icon"
        className="h-8 w-8 text-muted-foreground hover:text-foreground"
        aria-label="Afficher les paramètres"
        onClick={() => setRightPanelOpen(true)}
      >
        <PanelRightOpen className="h-4 w-4" />
      </Button>
    </div>
  );

  return (
    <div className="flex h-[calc(100vh-4rem)] flex-col">
      {/* Mobile action bar */}
      <div className="flex items-center gap-2 border-b border-border bg-card px-3 py-2 lg:hidden">
        <Button
          variant="outline"
          size="sm"
          className="flex-1"
          onClick={() => setMobilePanel('conversations')}
        >
          <MessageSquare className="mr-1.5 h-3.5 w-3.5" /> Conversations
        </Button>
        <Button
          variant="outline"
          size="sm"
          className="flex-1"
          onClick={() => setMobilePanel('settings')}
        >
          <Sparkles className="mr-1.5 h-3.5 w-3.5" /> Paramètres
        </Button>
        <Button
          size="sm"
          className="bg-accent text-accent-foreground hover:bg-accent/90"
          onClick={handleGenerate}
          disabled={generating}
        >
          {generating ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Sparkles className="h-3.5 w-3.5" />}
        </Button>
      </div>

      {/* Mobile overlay panels */}
      {mobilePanel && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div className="absolute inset-0 bg-black/50" onClick={() => setMobilePanel(null)} />
          <div className={cn(
            'absolute top-0 h-full w-80 max-w-[85vw] animate-slide-in-right',
            mobilePanel === 'conversations' ? 'left-0 border-r' : 'right-0 border-l',
          )} style={{ borderColor: 'hsl(var(--border))' }}>
            {mobilePanel === 'conversations' ? ConversationsPanel : SettingsPanel}
          </div>
        </div>
      )}

      <div className={cn(
        'grid flex-1 grid-cols-1 overflow-hidden',
        rightPanelOpen ? 'lg:grid-cols-[260px_1fr_320px]' : 'lg:grid-cols-[260px_1fr_52px]',
      )}>
        {/* Desktop conversations list */}
        <aside className="hidden flex-col border-r border-border bg-card lg:flex">
          {ConversationsPanel}
        </aside>

        {/* Chat area */}
        <section className="flex flex-col overflow-hidden">
          <header className="flex items-center justify-between gap-3 border-b border-border bg-card px-4 py-3">
            <div className="min-w-0">
              <h2 className="truncate text-sm font-semibold">{activeConv?.title ?? 'Générer un document'}</h2>
              <div className="mt-1 flex flex-wrap items-center gap-1.5">
                <Badge variant="outline" className="gap-1 font-normal text-muted-foreground">
                  <FileText className="h-3 w-3" /> {docTypeName ?? 'Aucun Document Type'}
                </Badge>
                <Badge variant="outline" className="gap-1 font-normal text-muted-foreground">
                  <Globe className="h-3 w-3" /> {languageLabel[language]}
                </Badge>
                <Badge variant="outline" className="gap-1 font-normal text-muted-foreground">
                  <Cpu className="h-3 w-3" /> {selectedAiConfig?.modelName ?? 'Modèle par défaut'}
                </Badge>
              </div>
            </div>
            <div className="flex flex-none items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => conversationGen && router.push(`/editor/${conversationGen.id}`)}
                disabled={!hasGeneratedDoc}
                title={hasGeneratedDoc ? 'Ouvrir dans l\'éditeur' : 'Générez un document d\'abord'}
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" /> <span className="hidden sm:inline">Ouvrir dans l'éditeur</span>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button size="sm" className="bg-accent text-accent-foreground hover:bg-accent/90" disabled={!hasGeneratedDoc || !!exporting}>
                    {exporting ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <FileDown className="mr-1.5 h-3.5 w-3.5" />}
                    <span className="hidden sm:inline">Exporter</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  {conversationGen?.exportUrl && (
                    <>
                      <DropdownMenuItem asChild>
                        <a href={conversationGen.exportUrl} target="_blank" rel="noopener noreferrer">
                          <Download className="mr-2 h-4 w-4" /> Télécharger (Word)
                        </a>
                      </DropdownMenuItem>
                      <DropdownMenuSeparator />
                    </>
                  )}
                  <DropdownMenuItem onClick={() => handleExport('DOCX')}><FileDown className="mr-2 h-4 w-4" /> DOCX (Word)</DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleExport('PDF')}><FileDown className="mr-2 h-4 w-4" /> PDF</DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleExport('Markdown')}><FileDown className="mr-2 h-4 w-4" /> Markdown</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
              {!rightPanelOpen && (
                <Button
                  variant="outline"
                  size="icon"
                  className="hidden lg:inline-flex"
                  aria-label="Afficher les paramètres"
                  onClick={() => setRightPanelOpen(true)}
                >
                  <PanelRightOpen className="h-4 w-4" />
                </Button>
              )}
            </div>
          </header>

          <ScrollArea className="flex-1 scrollbar-thin">
            {generating ? (
              <div className="mx-auto max-w-3xl space-y-4 p-4 sm:p-6">
                <div className="rounded-lg border border-accent/30 bg-accent/5 p-4">
                  <div className="mb-2 flex items-center gap-2 text-sm font-medium">
                    <Loader2 className="h-4 w-4 animate-spin text-accent" />
                    Génération en cours…
                  </div>
                  <Progress value={genProgress} className="mb-2" />
                  <p className="text-xs text-muted-foreground">
                    Section {Math.round((genProgress / 100) * (activeGen?.sections.length ?? 1))}/{activeGen?.sections.length ?? 0} : {currentSection}
                  </p>
                </div>
                {activeGen?.content && (
                  <div className="rounded-lg border border-border bg-background p-6 sm:p-8">
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/90">{activeGen.content}</p>
                  </div>
                )}
                {liveSectionContent && (
                  <div className="rounded-md border border-border/60 bg-background/70 p-4">
                    <p className="whitespace-pre-wrap text-sm leading-relaxed text-foreground/90">
                      {liveSectionContent}
                      <span className="ml-0.5 inline-block h-4 w-1.5 animate-pulse bg-accent align-text-bottom" />
                    </p>
                  </div>
                )}
                <div ref={messagesEndRef} />
              </div>
            ) : conversationGen ? (
              <div className="mx-auto max-w-3xl space-y-4 p-4 sm:p-6">
                <div className="rounded-lg border border-border bg-background p-6 sm:p-10">
                  <div
                    className="space-y-3 [&_h1]:text-2xl [&_h1]:font-semibold [&_h1]:mt-6 [&_h1]:first:mt-0 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:mt-4 [&_p]:leading-relaxed [&_table]:w-full [&_table]:border-collapse [&_th]:border [&_th]:border-border [&_th]:bg-muted [&_th]:px-3 [&_th]:py-1.5 [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6"
                    dangerouslySetInnerHTML={{ __html: conversationGen.content || '<p class="text-muted-foreground">Document vide.</p>' }}
                  />
                </div>
                <div ref={messagesEndRef} />
              </div>
            ) : (
              <div className="mx-auto max-w-3xl space-y-4 p-4 sm:p-6">
                {!messages?.length ? (
                  <div className="flex flex-col items-center gap-3 py-10 text-center sm:py-16">
                    <div className="flex h-14 w-14 items-center justify-center rounded-full bg-primary/10 text-primary">
                      <Bot className="h-7 w-7" />
                    </div>
                    <h3 className="text-lg font-semibold">Générer un document</h3>
                    <p className="max-w-md text-sm text-muted-foreground">
                      Décrivez le document que vous souhaitez générer, importez des fichiers de référence avec le bouton <Plus className="inline h-3 w-3" />, puis cliquez sur « Générer le document ».
                    </p>
                  </div>
                ) : (
                  messages.map((m: Message) => (
                    <div key={m.id} className={cn('flex gap-3 animate-fade-in-up', m.role === 'user' && 'flex-row-reverse')}>
                      <div className={cn('flex h-8 w-8 flex-none items-center justify-center rounded-full', m.role === 'user' ? 'bg-accent text-accent-foreground' : 'bg-primary text-primary-foreground')}>
                        {m.role === 'user' ? <UserIcon className="h-4 w-4" /> : <Bot className="h-4 w-4" />}
                      </div>
                      <div className={cn('max-w-[80%] rounded-lg px-4 py-2.5 text-sm', m.role === 'user' ? 'bg-accent/15 text-foreground' : 'bg-muted text-foreground')}>
                        <p className="whitespace-pre-wrap leading-relaxed">{m.content}</p>
                      </div>
                    </div>
                  ))
                )}

                {refDocs?.length ? (
                  <div className="flex flex-wrap gap-2">
                    {refDocs.map((r: ReferenceDocument) => (
                      <span key={r.id} className="inline-flex items-center gap-1.5 rounded-md border border-border bg-muted/50 px-2.5 py-1 text-xs">
                        <FileText className="h-3 w-3 text-muted-foreground" />
                        {r.fileName}
                      </span>
                    ))}
                  </div>
                ) : null}
                <div ref={messagesEndRef} />
              </div>
            )}
          </ScrollArea>

          {/* Input bar */}
          <div className="border-t border-border bg-card p-3 sm:p-4">
            <input ref={chatFileRef} type="file" multiple className="hidden" accept=".doc,.docx,.pdf,.md,.txt,.xlsx,.xls,.csv" onChange={(e) => handleFiles(e.target.files)} />
            <div className="mx-auto flex max-w-3xl items-end gap-2">
              <Button
                variant="outline"
                size="icon"
                className="flex-none"
                aria-label="Importer des fichiers"
                onClick={() => chatFileRef.current?.click()}
                disabled={!activeId}
              >
                <Plus className="h-4 w-4" />
              </Button>
              <textarea
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleSend(); } }}
                placeholder="Décrivez le document à générer… (Entrée pour envoyer, Maj+Entrée pour un retour à la ligne)"
                rows={1}
                aria-label="Saisie du message"
                className="flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <Button
                onClick={handleSend}
                disabled={!input.trim() || sendMsg.isPending}
                aria-label="Envoyer"
                size="icon"
                className="flex-none rounded-full bg-accent text-accent-foreground hover:bg-accent/90"
              >
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </section>

        {/* Desktop right panel */}
        {rightPanelOpen ? (
          <aside className="hidden flex-col overflow-hidden border-l border-border bg-card scrollbar-thin lg:flex">
            {SettingsPanel}
          </aside>
        ) : (
          CollapsedSettingsRail
        )}
      </div>
    </div>
  );
}

function SegButton({
  active,
  onClick,
  full,
  children,
}: {
  active: boolean;
  onClick: () => void;
  full?: boolean;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-pressed={active}
      className={cn(
        'rounded-md border px-3 py-1.5 text-sm font-medium transition-colors',
        full && 'w-full text-left',
        active ? 'border-accent bg-accent/10 text-accent' : 'border-border text-foreground hover:bg-muted',
      )}
    >
      {children}
    </button>
  );
}

function SectionRow({
  node,
  depth,
  expandedNodes,
  onToggle,
  sectionStatus,
  generating,
  currentSection,
}: {
  node: StructureNode;
  depth: number;
  expandedNodes: Set<string>;
  onToggle: (id: string) => void;
  sectionStatus: (label: string) => 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED' | 'NONE';
  generating: boolean;
  currentSection: string | null;
}) {
  const Icon = nodeIcon[node.type];
  const hasChildren = !!node.children?.length;
  const isExpanded = expandedNodes.has(node.id);
  const status = sectionStatus(node.label);
  const isCurrent = generating && currentSection === node.label;

  const statusDot = (s: 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED' | 'NONE') => {
    if (s === 'DONE') return <span className="h-2 w-2 flex-none rounded-full bg-success" aria-label="Terminé" />;
    if (s === 'GENERATING') return <Loader2 className="h-3 w-3 flex-none animate-spin text-accent" aria-label="En cours" />;
    if (s === 'FAILED') return <X className="h-3 w-3 flex-none text-destructive" aria-label="Échec" />;
    if (s === 'PENDING') return <span className="h-2 w-2 flex-none rounded-full border border-muted-foreground/40" aria-label="En attente" />;
    return null;
  };

  return (
    <li>
      <div
        className={cn(
          'flex items-center gap-1.5 rounded-md px-2 py-1.5 text-sm transition-colors',
          isCurrent ? 'bg-accent/10' : 'hover:bg-muted/50',
        )}
        style={{ paddingLeft: `${depth * 14 + 8}px` }}
      >
        {hasChildren ? (
          <button
            aria-label={isExpanded ? 'Réduire' : 'Déplier'}
            onClick={() => onToggle(node.id)}
            className="flex-none text-muted-foreground hover:text-foreground"
          >
            {isExpanded ? <ChevronDown className="h-3.5 w-3.5" /> : <ChevronRight className="h-3.5 w-3.5" />}
          </button>
        ) : (
          <span className="w-3.5 flex-none" />
        )}
        <Icon className="h-3.5 w-3.5 flex-none text-primary" />
        <span className={cn('flex-1 truncate', node.type === 'cover' && 'font-medium')}>{node.label}</span>
        {statusDot(status)}
      </div>
      {isExpanded && hasChildren && (
        <ul>
          {node.children!.map((child) => (
            <SectionRow
              key={child.id}
              node={child}
              depth={depth + 1}
              expandedNodes={expandedNodes}
              onToggle={onToggle}
              sectionStatus={sectionStatus}
              generating={generating}
              currentSection={currentSection}
            />
          ))}
        </ul>
      )}
    </li>
  );
}
