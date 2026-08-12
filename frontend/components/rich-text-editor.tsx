'use client';

import { useCallback, useEffect, useState } from 'react';
import { useEditor, EditorContent, type Editor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import { Table as TableExt } from '@tiptap/extension-table';
import { TableRow } from '@tiptap/extension-table-row';
import { TableHeader } from '@tiptap/extension-table-header';
import { TableCell } from '@tiptap/extension-table-cell';
import {
  Bold,
  Code,
  Columns3,
  Eraser,
  IndentDecrease,
  IndentIncrease,
  Italic,
  Link2,
  List,
  ListOrdered,
  Minus,
  Redo2,
  Rows3,
  Table as TableIcon,
  Trash2,
  Undo2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { docToMarkdown } from '@/lib/markdown-editor';
import { htmlToMarkdownTable } from '@/lib/markdown-table';
import { cn } from '@/lib/utils';

/**
 * Éditeur de section. `variant` décide du format produit, jamais de l'apparence
 * seule :
 * - `prose` sérialise le document en Markdown ({@link docToMarkdown}) ;
 * - `table` sérialise l'unique tableau du document en Markdown de tableau,
 *   format attendu tel quel par l'export DOCX/PDF pour une section TABLE.
 *
 * La barre d'outils n'expose que des mises en forme qui survivent à cette
 * sérialisation et sont réellement rendues à l'export — pas de police, de
 * taille, de couleur ni d'alignement, qui n'ont pas d'équivalent Markdown et
 * seraient perdus en silence à la sauvegarde.
 */
export type EditorVariant = 'prose' | 'table';

interface RichTextEditorProps {
  initialHtml: string;
  variant: EditorVariant;
  placeholder?: string;
  onChange: (markdown: string) => void;
  onBlur?: () => void;
}

const BLOCK_STYLES = [
  { value: 'paragraph', label: 'Paragraphe' },
  { value: 'h1', label: 'Titre 1' },
  { value: 'h2', label: 'Titre 2' },
  { value: 'h3', label: 'Titre 3' },
] as const;

const GRID_ROWS = 6;
const GRID_COLS = 8;

export function RichTextEditor({ initialHtml, variant, placeholder, onChange, onBlur }: RichTextEditorProps) {
  const isTable = variant === 'table';

  const editor = useEditor({
    immediatelyRender: false,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
        // Rendus non pris en charge par le pipeline d'export Markdown : ils
        // ressortiraient en caractères bruts dans le DOCX/PDF.
        blockquote: false,
        codeBlock: false,
        strike: false,
        underline: false,
        link: { openOnClick: false },
      }),
      TableExt.configure({ resizable: false }),
      TableRow,
      TableHeader,
      TableCell,
      CharacterCount,
      Placeholder.configure({ placeholder: placeholder ?? 'Rédigez le contenu de cette section…' }),
    ],
    content: initialHtml,
    editorProps: { attributes: { class: 'prose-editor min-h-[220px] max-w-none focus:outline-none' } },
  });

  const serialize = useCallback(
    (instance: Editor) => (isTable ? htmlToMarkdownTable(instance.getHTML()) : docToMarkdown(instance.getJSON())),
    [isTable],
  );

  useEffect(() => {
    if (!editor) return;
    const handler = () => onChange(serialize(editor));
    editor.on('update', handler);
    return () => {
      editor.off('update', handler);
    };
  }, [editor, onChange, serialize]);

  if (!editor) return null;

  const words = editor.storage.characterCount.words();
  const characters = editor.storage.characterCount.characters();
  const activeBlock = editor.isActive('heading', { level: 1 })
    ? 'h1'
    : editor.isActive('heading', { level: 2 })
      ? 'h2'
      : editor.isActive('heading', { level: 3 })
        ? 'h3'
        : 'paragraph';

  function applyBlockStyle(value: string) {
    if (!editor) return;
    const chain = editor.chain().focus();
    if (value === 'paragraph') chain.setParagraph().run();
    else chain.setHeading({ level: Number(value.slice(1)) as 1 | 2 | 3 }).run();
  }

  function promptLink() {
    if (!editor) return;
    const previous = (editor.getAttributes('link').href as string | undefined) ?? 'https://';
    const url = window.prompt('URL du lien :', previous);
    if (url === null) return;
    if (url.trim() === '') {
      editor.chain().focus().unsetLink().run();
      return;
    }
    editor.chain().focus().extendMarkRange('link').setLink({ href: url.trim() }).run();
  }

  return (
    <div className="rounded-md border border-border" onBlur={onBlur}>
      <div className="flex flex-wrap items-center gap-1 border-b border-border p-1.5">
        {!isTable && (
          <>
            <Select value={activeBlock} onValueChange={applyBlockStyle}>
              <SelectTrigger className="h-8 w-[132px] text-xs" aria-label="Style de paragraphe">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {BLOCK_STYLES.map((style) => (
                  <SelectItem key={style.value} value={style.value} className="text-xs">
                    {style.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Divider />
          </>
        )}

        <ToolbarButton icon={Bold} label="Gras" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()} />
        <ToolbarButton icon={Italic} label="Italique" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()} />
        <ToolbarButton icon={Code} label="Code inline" active={editor.isActive('code')} onClick={() => editor.chain().focus().toggleCode().run()} />

        {!isTable && (
          <>
            <Divider />
            <ToolbarButton icon={List} label="Liste à puces" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()} />
            <ToolbarButton icon={ListOrdered} label="Liste numérotée" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()} />
            <ToolbarButton
              icon={IndentDecrease}
              label="Diminuer le retrait"
              disabled={!editor.can().liftListItem('listItem')}
              onClick={() => editor.chain().focus().liftListItem('listItem').run()}
            />
            <ToolbarButton
              icon={IndentIncrease}
              label="Augmenter le retrait"
              disabled={!editor.can().sinkListItem('listItem')}
              onClick={() => editor.chain().focus().sinkListItem('listItem').run()}
            />
            <Divider />
            <ToolbarButton icon={Link2} label="Insérer un lien" active={editor.isActive('link')} onClick={promptLink} />
            <ToolbarButton icon={Minus} label="Ligne horizontale" onClick={() => editor.chain().focus().setHorizontalRule().run()} />
            <TablePicker editor={editor} />
          </>
        )}

        {editor.isActive('table') && (
          <>
            <Divider />
            <ToolbarButton icon={Rows3} label="Ajouter une ligne" onClick={() => editor.chain().focus().addRowAfter().run()} />
            <ToolbarButton icon={Columns3} label="Ajouter une colonne" onClick={() => editor.chain().focus().addColumnAfter().run()} />
            <ToolbarButton icon={Trash2} label="Supprimer la ligne" onClick={() => editor.chain().focus().deleteRow().run()} />
          </>
        )}

        <Divider />
        <ToolbarButton icon={Undo2} label="Annuler" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()} />
        <ToolbarButton icon={Redo2} label="Rétablir" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()} />
        <ToolbarButton
          icon={Eraser}
          label="Effacer le formatage"
          onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}
        />
      </div>

      <div className="p-2 [&_table]:w-full [&_table]:table-fixed [&_table]:border-collapse [&_th]:border [&_th]:border-border [&_th]:bg-muted [&_th]:px-3 [&_th]:py-1.5 [&_th]:text-left [&_th]:font-medium [&_td]:border [&_td]:border-border [&_td]:px-3 [&_td]:py-1.5 [&_.selectedCell]:bg-primary/10">
        <EditorContent editor={editor} />
      </div>

      <div className="flex items-center gap-4 border-t border-border px-3 py-1.5 text-xs text-muted-foreground">
        <span>
          {words} mot{words > 1 ? 's' : ''}
        </span>
        <span>
          {characters} caractère{characters > 1 ? 's' : ''}
        </span>
      </div>
    </div>
  );
}

/** Grille de sélection des dimensions, reprise du mock : on survole, on voit ce qu'on va insérer. */
function TablePicker({ editor }: { editor: Editor }) {
  const [open, setOpen] = useState(false);
  const [hovered, setHovered] = useState({ rows: 0, cols: 0 });

  function insert(rows: number, cols: number) {
    editor.chain().focus().insertTable({ rows, cols, withHeaderRow: true }).run();
    setOpen(false);
    setHovered({ rows: 0, cols: 0 });
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label="Insérer un tableau" title="Insérer un tableau">
          <TableIcon className="h-4 w-4" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-2" align="start">
        <div
          className="grid gap-0.5"
          style={{ gridTemplateColumns: `repeat(${GRID_COLS}, 1rem)` }}
          onMouseLeave={() => setHovered({ rows: 0, cols: 0 })}
        >
          {Array.from({ length: GRID_ROWS * GRID_COLS }, (_, index) => {
            const row = Math.floor(index / GRID_COLS) + 1;
            const col = (index % GRID_COLS) + 1;
            const highlighted = row <= hovered.rows && col <= hovered.cols;
            return (
              <button
                key={index}
                type="button"
                aria-label={`Tableau ${row} × ${col}`}
                onMouseEnter={() => setHovered({ rows: row, cols: col })}
                onClick={() => insert(row, col)}
                className={cn('h-4 w-4 rounded-[2px] border border-border', highlighted ? 'bg-primary' : 'bg-muted')}
              />
            );
          })}
        </div>
        <p className="pt-2 text-center text-xs text-muted-foreground">
          {hovered.rows} × {hovered.cols}
        </p>
      </PopoverContent>
    </Popover>
  );
}

function Divider() {
  return <div className="mx-0.5 h-6 w-px bg-border" aria-hidden />;
}

function ToolbarButton({
  icon: Icon,
  label,
  active,
  disabled,
  onClick,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  active?: boolean;
  disabled?: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      disabled={disabled}
      aria-label={label}
      aria-pressed={active}
      title={label}
      className={cn(
        'flex h-8 w-8 items-center justify-center rounded-md transition-colors hover:bg-muted disabled:pointer-events-none disabled:opacity-40',
        active && 'bg-primary/10 text-primary',
      )}
    >
      <Icon className="h-4 w-4" />
    </button>
  );
}
