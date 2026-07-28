'use client';

import { useEffect, useRef, useState } from 'react';
import { useParams } from 'next/navigation';
import Link from 'next/link';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import { Table as TableExt } from '@tiptap/extension-table';
import { TableRow } from '@tiptap/extension-table-row';
import { TableHeader } from '@tiptap/extension-table-header';
import { TableCell } from '@tiptap/extension-table-cell';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Bold,
  CheckCircle2,
  FileDown,
  Heading1,
  Heading2,
  Italic,
  List,
  ListOrdered,
  Loader2,
  Save,
  Table as TableIcon,
  TriangleAlert,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { useGeneration, useDocumentTypes } from '@/lib/hooks/queries';
import { updateGeneration } from '@/lib/api/client';
import { getStructureFor } from '@/lib/api/generator';
import { GeneratedStatusBadge } from '@/components/status-badge';
import { cn } from '@/lib/utils';
import type { DocumentType, GeneratedDocument } from '@/types';

export default function EditorPage() {
  const params = useParams<{ id: string }>();
  const { data: doc, isLoading } = useGeneration(params.id);
  const { data: documentTypes } = useDocumentTypes();
  const [savedAt, setSavedAt] = useState<string | null>(null);
  const [exporting, setExporting] = useState<string | null>(null);
  const [structureOk, setStructureOk] = useState(true);
  const saveTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const docType = documentTypes?.find((d: DocumentType) => d.id === doc?.documentTypeId);

  const editor = useEditor({
    extensions: [
      StarterKit,
      TableExt.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      Placeholder.configure({ placeholder: 'Le document généré apparaîtra ici…' }),
      CharacterCount,
    ],
    content: doc?.content || '',
    editorProps: {
      attributes: { class: 'prose-editor max-w-none focus:outline-none' },
    },
  });

  useEffect(() => {
    if (doc?.content && editor && !editor.getText()) {
      editor.commands.setContent(doc.content);
    }
  }, [doc, editor]);

  useEffect(() => {
    if (!editor || !doc) return;
    const handler = () => {
      if (saveTimer.current) clearTimeout(saveTimer.current);
      saveTimer.current = setTimeout(async () => {
        const html = editor.getHTML();
        await updateGeneration(doc.id, { content: html, status: 'EN_EDITION' });
        setSavedAt(new Date().toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' }));
        const expected = getStructureFor(doc.documentTypeId)
          .filter((n) => n.type === 'heading' && n.level === 1)
          .map((n) => n.label);
        const text = editor.getText();
        const missing = expected.filter((label) => !text.includes(label.replace(/^\d+\.\s*/, '')));
        setStructureOk(missing.length === 0);
      }, 1200);
    };
    editor.on('update', handler);
    return () => { editor.off('update', handler); };
  }, [editor, doc]);

  if (isLoading) {
    return <div className="mx-auto max-w-5xl space-y-6 p-6 lg:p-8"><Skeleton className="h-12 w-full" /><Skeleton className="h-96 w-full" /></div>;
  }

  if (!doc) {
    return (
      <div className="mx-auto max-w-5xl p-6 text-center lg:p-8">
        <p className="text-muted-foreground">Document introuvable.</p>
        <Button asChild variant="outline" className="mt-4"><Link href="/history">Retour à l'historique</Link></Button>
      </div>
    );
  }

  async function handleExport(format: 'DOCX' | 'PDF' | 'Markdown') {
    setExporting(format);
    await new Promise((r) => setTimeout(r, 1200));
    const content = editor?.getHTML() ?? doc?.content ?? '';
    const mime = format === 'PDF' ? 'application/pdf' : format === 'Markdown' ? 'text/markdown' : 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
    const blob = new Blob([content], { type: mime });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `${doc?.contentPivot ?? 'document'}.${format === 'Markdown' ? 'md' : format.toLowerCase()}`;
    a.click();
    URL.revokeObjectURL(url);
    if (doc) await updateGeneration(doc.id, { status: 'EXPORTE' });
    toast.success(`Export ${format} terminé`, { description: 'Le fichier a été téléchargé.' });
    setExporting(null);
  }

  if (!editor) return null;

  const ToolbarButton = ({ icon: Icon, active, onClick, label }: { icon: React.ComponentType<{ className?: string }>; active?: boolean; onClick: () => void; label: string }) => (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      className={cn('flex h-9 w-9 items-center justify-center rounded-md transition-colors hover:bg-muted', active && 'bg-primary/10 text-primary')}
    >
      <Icon className="h-4 w-4" />
    </button>
  );

  const currentDoc: GeneratedDocument = doc;

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title={currentDoc.contentPivot}
        description={docType?.name}
        actions={
          <div className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm"><Link href="/chat"><ArrowLeft className="mr-2 h-4 w-4" /> Chat</Link></Button>
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button size="sm" className="bg-accent text-accent-foreground hover:bg-accent/90">
                  {exporting ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <FileDown className="mr-2 h-4 w-4" />}
                  Exporter
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem onClick={() => handleExport('DOCX')}><FileDown className="mr-2 h-4 w-4" /> DOCX (Word)</DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('PDF')}><FileDown className="mr-2 h-4 w-4" /> PDF</DropdownMenuItem>
                <DropdownMenuItem onClick={() => handleExport('Markdown')}><FileDown className="mr-2 h-4 w-4" /> Markdown</DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        }
      />

      <div className="flex flex-wrap items-center gap-3 text-sm">
        <GeneratedStatusBadge status={currentDoc.status} />
        {structureOk ? (
          <Badge variant="outline" className="border-success/30 bg-success/15 text-success"><CheckCircle2 className="mr-1 h-3 w-3" /> Conforme à la structure</Badge>
        ) : (
          <Badge variant="outline" className="border-warning/30 bg-warning/15 text-warning"><TriangleAlert className="mr-1 h-3 w-3" /> Écart détecté</Badge>
        )}
        <span className="ml-auto flex items-center gap-1 text-xs text-muted-foreground">
          {savedAt ? <><Save className="h-3 w-3" /> Enregistré à {savedAt}</> : 'Sauvegarde automatique active'}
        </span>
      </div>

      <Card>
        <div className="flex flex-wrap items-center gap-1 border-b border-border bg-muted/30 p-2">
          <ToolbarButton icon={Heading1} active={editor.isActive('heading', { level: 1 })} onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()} label="Titre 1" />
          <ToolbarButton icon={Heading2} active={editor.isActive('heading', { level: 2 })} onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()} label="Titre 2" />
          <div className="mx-1 h-6 w-px bg-border" />
          <ToolbarButton icon={Bold} active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()} label="Gras" />
          <ToolbarButton icon={Italic} active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()} label="Italique" />
          <div className="mx-1 h-6 w-px bg-border" />
          <ToolbarButton icon={List} active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()} label="Liste à puces" />
          <ToolbarButton icon={ListOrdered} active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()} label="Liste numérotée" />
          <div className="mx-1 h-6 w-px bg-border" />
          <ToolbarButton icon={TableIcon} onClick={() => editor.chain().focus().insertTable({ rows: 3, cols: 3, withHeaderRow: true }).run()} label="Insérer un tableau" />
        </div>
        <CardContent className="p-8">
          <div className="min-h-[60vh] space-y-3 [&_h1]:text-2xl [&_h1]:font-semibold [&_h1]:mt-6 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:mt-4 [&_p]:leading-relaxed [&_table]:w-full [&_table]:border-collapse [&_th]:border [&_th]:border-border [&_th]:bg-muted [&_th]:px-3 [&_th]:py-1.5 [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-1.5 [&_ul]:list-disc [&_ul]:pl-6 [&_ol]:list-decimal [&_ol]:pl-6 [&_.is-empty]:before:content-[attr(data-placeholder)] [&_.is-empty]:before:text-muted-foreground [&_.is-empty]:before:float-left [&_.is-empty]:before:h-0 [&_.is-empty]:before:pointer-events-none">
            <EditorContent editor={editor} />
          </div>
          <p className="mt-4 text-xs text-muted-foreground">
            {editor.storage.characterCount.characters()} caractères · {editor.storage.characterCount.words()} mots
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
