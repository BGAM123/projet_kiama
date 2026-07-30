'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  Bot,
  ChevronDown,
  ChevronRight,
  FileDown,
  FileText,
  List,
  Loader2,
  MessageSquare,
  Pencil,
  Plus,
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Separator } from '@/components/ui/separator';
import { Progress } from '@/components/ui/progress';
import { ScrollArea } from '@/components/ui/scroll-area';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
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
} from '@/lib/hooks/queries';
import {
  createConversation,
  sendMessage,
  addReferenceDocument,
  startGeneration,
  updateGeneration,
  exportDocument,
} from '@/lib/api/client';
import { streamGeneration, getStructureFor } from '@/lib/api/generator';
import { languageLabel, toneLabel, targetLengthLabel, relativeTime } from '@/lib/format';
import type {
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

  const [activeId, setActiveId] = useState<string | null>(null);
  const [documentTypeId, setDocumentTypeId] = useState<string>('');
  const [language, setLanguage] = useState<Language>('FR');
  const [tone, setTone] = useState<Tone>('FORMEL');
  const [targetLength, setTargetLength] = useState<TargetLength>('LONG');
  const [contentPivot, setContentPivot] = useState('');
  const [input, setInput] = useState('');
  const [generating, setGenerating] = useState(false);
  const [genProgress, setGenProgress] = useState(0);
  const [currentSection, setCurrentSection] = useState<string | null>(null);
  const [activeGen, setActiveGen] = useState<GeneratedDocument | null>(null);
  const [expandedNodes, setExpandedNodes] = useState<Set<string>>(new Set());
  const [showSettings, setShowSettings] = useState(true);
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

  const liveSections = activeGen?.sections ?? [];

  useEffect(() => {
    if (!activeId && convList.length) setActiveId(convList[0].id);
  }, [convList, activeId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

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
    mutationFn: async (fileName: string) => (await addReferenceDocument(activeId!, fileName)).data,
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
      await addRef.mutateAsync(f.name);
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
        }
      }
      await updateGeneration(doc.id, { status: 'GENERE' });
      qc.invalidateQueries({ queryKey: ['generation', doc.id] });
      toast.success('Document généré', { description: 'Vous pouvez l\'éditer ou l\'exporter.' });
    } catch (e) {
      toast.error('La génération a échoué.', { description: e instanceof Error ? e.message : undefined });
      if (activeGen) await updateGeneration(activeGen.id, { status: 'ECHEC' });
    } finally {
      setGenerating(false);
      setCurrentSection(null);
      setMobilePanel(null);
    }
  }

  async function handleExport(format: 'DOCX' | 'PDF' | 'Markdown') {
    if (!activeGen) return;
    setExporting(format);
    try {
      // Export réel via POST /api/v1/export — remplace la simulation locale
      // qui téléchargeait du texte brut renommé en .pdf/.docx (écart 1.13
      // du rapport d'écarts). Le backend renvoie un vrai binaire.
      const blob = await exportDocument({
        title: activeGen.contentPivot || 'document',
        content: activeGen.content || '',
        format,
      });
      const ext = format === 'Markdown' ? 'md' : format.toLowerCase();
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `${activeGen.contentPivot ?? 'document'}.${ext}`;
      a.click();
      URL.revokeObjectURL(url);
      await updateGeneration(activeGen.id, { status: 'EXPORTE' });
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
  const hasGeneratedDoc = !!activeGen && !generating;

  /* ---------------- Conversations panel (shared) ---------------- */
  const ConversationsPanel = (
    <div className="flex h-full flex-col bg-card">
      <div className="flex items-center justify-between p-3 lg:hidden">
        <span className="text-sm font-semibold">Conversations</span>
        <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Fermer" onClick={() => setMobilePanel(null)}>
          <X className="h-4 w-4" />
        </Button>
      </div>
      <div className="p-3">
        <Button className="w-full bg-accent text-accent-foreground hover:bg-accent/90" onClick={() => createConv.mutate()} disabled={createConv.isPending}>
          <Plus className="mr-2 h-4 w-4" /> Nouvelle conversation
        </Button>
      </div>
      <ScrollArea className="flex-1">
        <ul className="space-y-1 p-2">
          {convLoading ? (
            <li className="p-3 text-sm text-muted-foreground">Chargement…</li>
          ) : !convList.length ? (
            <li className="p-3 text-sm text-muted-foreground">Aucune conversation.</li>
          ) : (
            convList.map((c: Conversation) => (
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
                    {relativeTime(c.createdAt)}
                  </p>
                </button>
              </li>
            ))
          )}
        </ul>
      </ScrollArea>
    </div>
  );

  /* ---------------- Settings panel (shared) ---------------- */
  const SettingsPanel = (
    <div className="flex h-full flex-col bg-card">
      <div className="flex items-center justify-between p-4 lg:hidden">
        <span className="text-sm font-semibold">Paramètres & sections</span>
        <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Fermer" onClick={() => setMobilePanel(null)}>
          <X className="h-4 w-4" />
        </Button>
      </div>

      {/* Document sections */}
      <div className="border-b border-border p-4">
        <h3 className="flex items-center gap-2 text-sm font-semibold">
          <FileText className="h-4 w-4 text-primary" />
          Sections du document
        </h3>
        <p className="mt-1 text-xs text-muted-foreground">{docTypeName ?? 'Sélectionnez un Document Type'}</p>
        {!structureTree.length ? (
          <p className="mt-4 rounded-md bg-muted/40 px-3 py-4 text-center text-xs text-muted-foreground">
            Aucune structure à afficher. Choisissez un Document Type dans les paramètres ci-dessous.
          </p>
        ) : (
          <ScrollArea className="mt-3 max-h-72">
            <ul className="space-y-0.5">
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
        )}
      </div>

      {/* Settings (collapsible) */}
      <div className="flex-1 overflow-y-auto p-4">
        <button
          className="flex w-full items-center justify-between text-sm font-semibold"
          onClick={() => setShowSettings((s) => !s)}
          aria-expanded={showSettings}
        >
          <span className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-accent" /> Paramètres de génération</span>
          {showSettings ? <ChevronDown className="h-4 w-4 text-muted-foreground" /> : <ChevronRight className="h-4 w-4 text-muted-foreground" />}
        </button>

        {showSettings && (
          <div className="mt-4 space-y-4">
            <div className="space-y-2">
              <Label htmlFor="dt">Document Type</Label>
              <Select value={documentTypeId} onValueChange={setDocumentTypeId}>
                <SelectTrigger id="dt"><SelectValue placeholder="Sélectionner" /></SelectTrigger>
                <SelectContent>
                  {documentTypes?.filter((d: DocumentType) => d.status === 'ACTIF').map((d: DocumentType) => <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>

            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-2">
                <Label htmlFor="lang">Langue</Label>
                <Select value={language} onValueChange={(v) => setLanguage(v as Language)}>
                  <SelectTrigger id="lang"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(languageLabel).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-2">
                <Label htmlFor="tone">Ton</Label>
                <Select value={tone} onValueChange={(v) => setTone(v as Tone)}>
                  <SelectTrigger id="tone"><SelectValue /></SelectTrigger>
                  <SelectContent>
                    {Object.entries(toneLabel).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                  </SelectContent>
                </Select>
              </div>
            </div>

            <div className="space-y-2">
              <Label htmlFor="len">Longueur cible</Label>
              <Select value={targetLength} onValueChange={(v) => setTargetLength(v as TargetLength)}>
                <SelectTrigger id="len"><SelectValue /></SelectTrigger>
                <SelectContent>
                  {Object.entries(targetLengthLabel).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
                </SelectContent>
              </Select>
            </div>

            <div className="space-y-2">
              <Label htmlFor="pivot">Sujet / pivot de contenu</Label>
              <Input id="pivot" placeholder="Ex. Rapport Q2 2026" value={contentPivot} onChange={(e) => setContentPivot(e.target.value)} />
            </div>

            <Separator />

            <div className="space-y-2">
              <div className="flex items-center justify-between">
                <Label>Documents de référence</Label>
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
        )}
      </div>

      {/* Bottom actions */}
      <div className="border-t border-border p-4">
        <Button onClick={handleGenerate} disabled={generating} className="w-full bg-accent text-accent-foreground hover:bg-accent/90">
          {generating ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Génération…</> : <><Sparkles className="mr-2 h-4 w-4" /> Générer le document</>}
        </Button>
        {hasGeneratedDoc && (
          <div className="mt-2 grid grid-cols-2 gap-2">
            <Button variant="outline" size="sm" onClick={() => router.push(`/editor/${activeGen.id}`)}>
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

      <div className="grid flex-1 grid-cols-1 overflow-hidden lg:grid-cols-[260px_1fr_320px]">
        {/* Desktop conversations list */}
        <aside className="hidden flex-col border-r border-border bg-card lg:flex">
          {ConversationsPanel}
        </aside>

        {/* Chat area */}
        <section className="flex flex-col overflow-hidden">
          <header className="flex items-center justify-between border-b border-border bg-card px-4 py-3">
            <div className="min-w-0">
              <h2 className="truncate text-sm font-semibold">{activeConv?.title ?? 'Générer un document'}</h2>
              <p className="truncate text-xs text-muted-foreground">{docTypeName ?? 'Aucun Document Type sélectionné'}</p>
            </div>
            <div className="flex flex-none items-center gap-2">
              <Button
                variant="outline"
                size="sm"
                onClick={() => activeGen && router.push(`/editor/${activeGen.id}`)}
                disabled={!hasGeneratedDoc}
                title={hasGeneratedDoc ? 'Ouvrir dans l\'éditeur' : 'Générez un document d\'abord'}
              >
                <Pencil className="mr-1.5 h-3.5 w-3.5" /> <span className="hidden sm:inline">Éditer</span>
              </Button>
              <DropdownMenu>
                <DropdownMenuTrigger asChild>
                  <Button size="sm" className="bg-accent text-accent-foreground hover:bg-accent/90" disabled={!hasGeneratedDoc || !!exporting}>
                    {exporting ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <FileDown className="mr-1.5 h-3.5 w-3.5" />}
                    <span className="hidden sm:inline">Exporter</span>
                  </Button>
                </DropdownMenuTrigger>
                <DropdownMenuContent align="end">
                  <DropdownMenuItem onClick={() => handleExport('DOCX')}><FileDown className="mr-2 h-4 w-4" /> DOCX (Word)</DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleExport('PDF')}><FileDown className="mr-2 h-4 w-4" /> PDF</DropdownMenuItem>
                  <DropdownMenuItem onClick={() => handleExport('Markdown')}><FileDown className="mr-2 h-4 w-4" /> Markdown</DropdownMenuItem>
                </DropdownMenuContent>
              </DropdownMenu>
            </div>
          </header>

          <ScrollArea className="flex-1 scrollbar-thin">
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

              {generating && (
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
              )}
              <div ref={messagesEndRef} />
            </div>
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
                placeholder="Écrivez votre message… (Entrée pour envoyer)"
                rows={1}
                aria-label="Saisie du message"
                className="flex-1 resize-none rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              />
              <Button onClick={handleSend} disabled={!input.trim() || sendMsg.isPending} aria-label="Envoyer">
                <Send className="h-4 w-4" />
              </Button>
            </div>
          </div>
        </section>

        {/* Desktop right panel */}
        <aside className="hidden flex-col overflow-hidden border-l border-border bg-card scrollbar-thin lg:flex">
          {SettingsPanel}
        </aside>
      </div>
    </div>
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
