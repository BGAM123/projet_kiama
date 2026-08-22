'use client';

import {
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import { useEditor, EditorContent, type Editor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import CharacterCount from '@tiptap/extension-character-count';
import { TableKit } from '@tiptap/extension-table';
import TextAlign from '@tiptap/extension-text-align';
import { TextStyle, Color, FontFamily, FontSize } from '@tiptap/extension-text-style';
import Highlight from '@tiptap/extension-highlight';
import Subscript from '@tiptap/extension-subscript';
import Superscript from '@tiptap/extension-superscript';
import TiptapImage from '@tiptap/extension-image';
import { PaginationPlus, PAGE_SIZES } from 'tiptap-pagination-plus';
import {
  AlignCenter,
  AlignJustify,
  AlignLeft,
  AlignRight,
  Baseline,
  Bold,
  ChevronDown,
  Code,
  Columns3,
  Eraser,
  Highlighter,
  ImagePlus,
  IndentDecrease,
  IndentIncrease,
  Italic,
  Link2,
  List,
  ListOrdered,
  Loader2,
  Merge,
  Minus,
  Redo2,
  Rows3,
  Scissors,
  Sparkles,
  Strikethrough,
  Subscript as SubscriptIcon,
  Superscript as SuperscriptIcon,
  Table as TableIcon,
  Trash2,
  Underline as UnderlineIcon,
  Undo2,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { ACCEPTED_IMAGE_TYPES, fileToEditorImage } from '@/lib/editor-image';
import { cn } from '@/lib/utils';

/**
 * Éditeur plein document, à la manière d'un traitement de texte : le squelette
 * du Document Type est chargé tel quel et se modifie directement — police,
 * taille, couleur, surlignage, alignement, listes, tableaux, images — avant
 * export en DOCX ou PDF. La pagination (découpage en pages A4, numérotation)
 * est automatique, gérée par {@link PaginationPlus} au fil de la frappe.
 *
 * Le format manipulé est le HTML de l'éditeur, et c'est aussi celui qui est
 * stocké puis relu par l'export serveur ({@code HtmlContentParser}). Tout ce
 * que cette barre d'outils propose a donc un rendu réel dans le fichier livré :
 * contrairement au pivot Markdown qu'il remplace, rien n'est perdu en silence à
 * la sauvegarde.
 */

// ---------------------------------------------------------------------------
// Extensions
// ---------------------------------------------------------------------------

/**
 * Pagination automatique : l'éditeur découpe lui-même le contenu en pages A4
 * au fil de la frappe (mesure de hauteur réelle), avec numérotation en pied de
 * page — remplace l'ancien "saut de page" manuel (un simple séparateur visuel
 * sans vraie pagination). Dimensions {@link PAGE_SIZES.A4} de la bibliothèque :
 * 794×1123px avec ses marges par défaut, au pixel près la largeur qu'avait la
 * page fixe précédente (21 cm à 96 dpi).
 */
const PAGE_CONFIG = {
  ...PAGE_SIZES.A4,
  pageGap: 24,
  pageGapBorderColor: 'hsl(var(--border))',
  // Toujours un gris neutre, jamais teinté par le thème sombre : c'est
  // l'espace entre deux feuilles, pas du contenu du document.
  pageBreakBackground: '#e5e7eb',
  footerRight: 'Page {page}',
};

/**
 * Image dont la largeur voulue est écrite dans `style`, et non dans un attribut
 * `width` : c'est la forme que l'export serveur lit, et celle que le navigateur
 * applique réellement à l'affichage.
 */
const DocumentImage = TiptapImage.extend({
  addAttributes() {
    return {
      ...this.parent?.(),
      width: {
        default: null,
        parseHTML: (element) => element.style.width || element.getAttribute('width'),
        renderHTML: (attributes) =>
          attributes.width ? { style: `width: ${attributes.width}` } : {},
      },
    };
  },
});

// ---------------------------------------------------------------------------
// Listes de choix de la barre d'outils
// ---------------------------------------------------------------------------

const BLOCK_STYLES = [
  { value: 'paragraph', label: 'Normal' },
  { value: 'h1', label: 'Titre 1' },
  { value: 'h2', label: 'Titre 2' },
  { value: 'h3', label: 'Titre 3' },
  { value: 'h4', label: 'Titre 4' },
  { value: 'h5', label: 'Titre 5' },
  { value: 'h6', label: 'Titre 6' },
] as const;

/** Polices restreintes à celles qu'un poste Windows/Office a toujours — une police absente serait remplacée à l'ouverture du DOCX. */
const FONT_FAMILIES = [
  'Calibri',
  'Arial',
  'Times New Roman',
  'Georgia',
  'Cambria',
  'Verdana',
  'Tahoma',
  'Courier New',
];

const FONT_SIZES = ['8', '9', '10', '11', '12', '14', '16', '18', '20', '24', '28', '36', '48'];

const TEXT_COLORS = [
  '#000000', '#404040', '#737373', '#A6A6A6', '#D9D9D9', '#FFFFFF',
  '#C00000', '#FF0000', '#FFC000', '#FFFF00', '#92D050', '#00B050',
  '#00B0F0', '#0070C0', '#002060', '#7030A0', '#1155CC', '#B45F06',
];

const HIGHLIGHT_COLORS = ['#FFFF00', '#00FF00', '#00FFFF', '#FF00FF', '#FFC7CE', '#C6EFCE', '#DDEBF7', '#D9D9D9'];

const GRID_ROWS = 8;
const GRID_COLS = 10;

const IMAGE_WIDTHS = [
  { label: '25 %', value: '25%' },
  { label: '50 %', value: '50%' },
  { label: '75 %', value: '75%' },
  { label: '100 %', value: '100%' },
];

// ---------------------------------------------------------------------------
// Composant
// ---------------------------------------------------------------------------

export interface OutlineItem {
  /** Position ProseMirror du titre — sert à y amener le curseur depuis le sommaire. */
  pos: number;
  level: number;
  text: string;
}

/** Passage sélectionné, avec ses bornes : elles servent à réinjecter la suggestion IA au bon endroit même si le curseur a bougé depuis. */
export interface EditorSelection {
  text: string;
  from: number;
  to: number;
}

export interface WordEditorHandle {
  /** Amène le curseur (et la vue) sur un titre du sommaire. */
  goTo: (pos: number) => void;
  /** HTML courant — lu au moment de l'export/sauvegarde explicite, sans attendre le prochain `onChange`. */
  getHtml: () => string;
  /** Remplace un intervalle par du texte brut — utilisé pour appliquer une suggestion IA acceptée. */
  replaceRange: (from: number, to: number, text: string) => void;
}

interface WordEditorProps {
  initialHtml: string;
  editable?: boolean;
  onChange: (html: string) => void;
  onOutlineChange?: (outline: OutlineItem[]) => void;
  onError?: (message: string) => void;
  /** Absent = bouton « Améliorer avec l'IA » masqué. */
  onImproveSelection?: (selection: EditorSelection) => void;
  improving?: boolean;
}

export const WordEditor = forwardRef<WordEditorHandle, WordEditorProps>(function WordEditor(
  { initialHtml, editable = true, onChange, onOutlineChange, onError, onImproveSelection, improving },
  ref,
) {
  const fileInputRef = useRef<HTMLInputElement>(null);

  const editor = useEditor({
    immediatelyRender: false,
    editable,
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3, 4, 5, 6] },
        link: { openOnClick: false, autolink: true },
        codeBlock: false,
      }),
      TextStyle,
      Color,
      FontFamily,
      FontSize,
      Highlight.configure({ multicolor: true }),
      Subscript,
      Superscript,
      TextAlign.configure({ types: ['heading', 'paragraph'] }),
      TableKit.configure({ table: { resizable: true, allowTableNodeSelection: true } }),
      DocumentImage.configure({ allowBase64: true, inline: false }),
      CharacterCount,
      Placeholder.configure({ placeholder: 'Rédigez votre document…' }),
      PaginationPlus.configure(PAGE_CONFIG),
    ],
    content: initialHtml,
    editorProps: {
      attributes: { class: 'word-page-content focus:outline-none' },
    },
  });

  useImperativeHandle(
    ref,
    () => ({
      goTo: (pos: number) => {
        if (!editor) return;
        editor.chain().focus().setTextSelection(pos).scrollIntoView().run();
      },
      getHtml: () => editor?.getHTML() ?? initialHtml,
      replaceRange: (from: number, to: number, text: string) => {
        editor?.chain().focus().insertContentAt({ from, to }, text).run();
      },
    }),
    [editor, initialHtml],
  );

  // Le sommaire se reconstruit à chaque frappe : c'est ce qui le garde en phase
  // avec un titre qu'on vient de renommer.
  const publishOutline = useCallback(
    (instance: Editor) => {
      if (!onOutlineChange) return;
      const outline: OutlineItem[] = [];
      instance.state.doc.descendants((node, pos) => {
        if (node.type.name === 'heading') {
          outline.push({ pos, level: node.attrs.level as number, text: node.textContent });
        }
      });
      onOutlineChange(outline);
    },
    [onOutlineChange],
  );

  useEffect(() => {
    if (!editor) return;
    const handler = () => {
      onChange(editor.getHTML());
      publishOutline(editor);
    };
    publishOutline(editor);
    editor.on('update', handler);
    return () => {
      editor.off('update', handler);
    };
  }, [editor, onChange, publishOutline]);

  useEffect(() => {
    editor?.setEditable(editable);
  }, [editor, editable]);

  const insertImage = useCallback(
    async (file: File) => {
      if (!editor) return;
      try {
        const { src, width } = await fileToEditorImage(file);
        editor.chain().focus().setImage({ src, width: `${width}px` } as { src: string }).run();
      } catch (e) {
        onError?.(e instanceof Error ? e.message : "L'image n'a pas pu être insérée.");
      }
    },
    [editor, onError],
  );

  if (!editor) return null;

  return (
    <div className="flex h-full flex-col overflow-hidden">
      <Toolbar
        editor={editor}
        onPickImage={() => fileInputRef.current?.click()}
        onImproveSelection={onImproveSelection}
        improving={improving}
      />

      <input
        ref={fileInputRef}
        type="file"
        accept={ACCEPTED_IMAGE_TYPES}
        className="hidden"
        onChange={(event) => {
          const file = event.target.files?.[0];
          // Réinitialisé pour que réinsérer deux fois la même image déclenche
          // bien un second `change`.
          event.target.value = '';
          if (file) insertImage(file);
        }}
      />

      <div className="scrollbar-thin flex-1 overflow-y-auto bg-muted/40 p-4 lg:p-8">
        <div className="flex justify-center">
          <EditorContent editor={editor} />
        </div>
      </div>

      <StatusBar editor={editor} />
    </div>
  );
});

// ---------------------------------------------------------------------------
// Barre d'outils
// ---------------------------------------------------------------------------

function Toolbar({
  editor,
  onPickImage,
  onImproveSelection,
  improving,
}: {
  editor: Editor;
  onPickImage: () => void;
  onImproveSelection?: (selection: EditorSelection) => void;
  improving?: boolean;
}) {
  const activeBlock = useMemo(() => {
    for (let level = 1; level <= 6; level++) {
      if (editor.isActive('heading', { level })) return `h${level}`;
    }
    return 'paragraph';
  }, [editor, editor.state.selection]);

  const currentFont = (editor.getAttributes('textStyle').fontFamily as string | undefined) ?? '';
  const currentSize = String(editor.getAttributes('textStyle').fontSize ?? '').replace('px', '');
  const inTable = editor.isActive('table');
  const onImage = editor.isActive('image');

  function applyBlockStyle(value: string) {
    const chain = editor.chain().focus();
    if (value === 'paragraph') chain.setParagraph().run();
    else chain.setHeading({ level: Number(value.slice(1)) as 1 | 2 | 3 | 4 | 5 | 6 }).run();
  }

  function promptLink() {
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
    <div className="flex flex-wrap items-center gap-0.5 border-b border-border bg-card px-2 py-1.5">
      <ToolbarButton icon={Undo2} label="Annuler" disabled={!editor.can().undo()} onClick={() => editor.chain().focus().undo().run()} />
      <ToolbarButton icon={Redo2} label="Rétablir" disabled={!editor.can().redo()} onClick={() => editor.chain().focus().redo().run()} />
      <Divider />

      <Select value={activeBlock} onValueChange={applyBlockStyle}>
        <SelectTrigger className="h-8 w-[104px] text-xs" aria-label="Style de paragraphe">
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

      <Select
        value={currentFont}
        onValueChange={(value) => editor.chain().focus().setFontFamily(value).run()}
      >
        <SelectTrigger className="h-8 w-[140px] text-xs" aria-label="Police">
          <SelectValue placeholder="Police" />
        </SelectTrigger>
        <SelectContent>
          {FONT_FAMILIES.map((font) => (
            <SelectItem key={font} value={font} className="text-xs" style={{ fontFamily: font }}>
              {font}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Select
        value={currentSize}
        onValueChange={(value) => editor.chain().focus().setFontSize(`${value}px`).run()}
      >
        <SelectTrigger className="h-8 w-[68px] text-xs" aria-label="Taille de police">
          <SelectValue placeholder="Taille" />
        </SelectTrigger>
        <SelectContent>
          {FONT_SIZES.map((size) => (
            <SelectItem key={size} value={size} className="text-xs">
              {size}
            </SelectItem>
          ))}
        </SelectContent>
      </Select>

      <Divider />

      <ToolbarButton icon={Bold} label="Gras" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()} />
      <ToolbarButton icon={Italic} label="Italique" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()} />
      <ToolbarButton icon={UnderlineIcon} label="Souligné" active={editor.isActive('underline')} onClick={() => editor.chain().focus().toggleUnderline().run()} />
      <ToolbarButton icon={Strikethrough} label="Barré" active={editor.isActive('strike')} onClick={() => editor.chain().focus().toggleStrike().run()} />
      <ToolbarButton icon={Code} label="Code" active={editor.isActive('code')} onClick={() => editor.chain().focus().toggleCode().run()} />
      <ToolbarButton icon={SuperscriptIcon} label="Exposant" active={editor.isActive('superscript')} onClick={() => editor.chain().focus().toggleSuperscript().run()} />
      <ToolbarButton icon={SubscriptIcon} label="Indice" active={editor.isActive('subscript')} onClick={() => editor.chain().focus().toggleSubscript().run()} />

      <ColorPicker
        icon={Baseline}
        label="Couleur du texte"
        colors={TEXT_COLORS}
        current={editor.getAttributes('textStyle').color as string | undefined}
        onPick={(color) => editor.chain().focus().setColor(color).run()}
        onReset={() => editor.chain().focus().unsetColor().run()}
      />
      <ColorPicker
        icon={Highlighter}
        label="Surlignage"
        colors={HIGHLIGHT_COLORS}
        current={editor.getAttributes('highlight').color as string | undefined}
        onPick={(color) => editor.chain().focus().setHighlight({ color }).run()}
        onReset={() => editor.chain().focus().unsetHighlight().run()}
      />
      <ToolbarButton
        icon={Eraser}
        label="Effacer la mise en forme"
        onClick={() => editor.chain().focus().unsetAllMarks().clearNodes().run()}
      />

      <Divider />

      <ToolbarButton icon={AlignLeft} label="Aligner à gauche" active={editor.isActive({ textAlign: 'left' })} onClick={() => editor.chain().focus().setTextAlign('left').run()} />
      <ToolbarButton icon={AlignCenter} label="Centrer" active={editor.isActive({ textAlign: 'center' })} onClick={() => editor.chain().focus().setTextAlign('center').run()} />
      <ToolbarButton icon={AlignRight} label="Aligner à droite" active={editor.isActive({ textAlign: 'right' })} onClick={() => editor.chain().focus().setTextAlign('right').run()} />
      <ToolbarButton icon={AlignJustify} label="Justifier" active={editor.isActive({ textAlign: 'justify' })} onClick={() => editor.chain().focus().setTextAlign('justify').run()} />

      <Divider />

      <ToolbarButton icon={List} label="Liste à puces" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()} />
      <ToolbarButton icon={ListOrdered} label="Liste numérotée" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()} />
      <ToolbarButton icon={IndentDecrease} label="Diminuer le retrait" disabled={!editor.can().liftListItem('listItem')} onClick={() => editor.chain().focus().liftListItem('listItem').run()} />
      <ToolbarButton icon={IndentIncrease} label="Augmenter le retrait" disabled={!editor.can().sinkListItem('listItem')} onClick={() => editor.chain().focus().sinkListItem('listItem').run()} />

      <Divider />

      <ToolbarButton icon={Link2} label="Insérer un lien" active={editor.isActive('link')} onClick={promptLink} />
      <ToolbarButton icon={ImagePlus} label="Insérer une image" onClick={onPickImage} />
      <TablePicker editor={editor} />
      <ToolbarButton icon={Minus} label="Ligne horizontale" onClick={() => editor.chain().focus().setHorizontalRule().run()} />

      {onImproveSelection && (
        <>
          <Divider />
          <Button
            type="button"
            variant="ghost"
            size="sm"
            className="h-8 gap-1.5 px-2 text-xs text-primary"
            // Sans sélection, l'IA n'aurait rien à reformuler.
            disabled={improving || editor.state.selection.empty}
            title={
              editor.state.selection.empty
                ? 'Sélectionnez le passage à reformuler.'
                : 'Reformuler le passage sélectionné'
            }
            onClick={() => {
              const { from, to } = editor.state.selection;
              onImproveSelection({ from, to, text: editor.state.doc.textBetween(from, to, '\n') });
            }}
          >
            {improving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Sparkles className="h-4 w-4" />}
            Améliorer avec l&apos;IA
          </Button>
        </>
      )}

      {inTable && <TableMenu editor={editor} />}
      {onImage && <ImageMenu editor={editor} />}
    </div>
  );
}

/** Grille de sélection des dimensions : on survole, on voit ce qu'on va insérer. */
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

/** Opérations de tableau, visibles seulement quand le curseur est dans un tableau — comme l'onglet contextuel de Word. */
function TableMenu({ editor }: { editor: Editor }) {
  return (
    <>
      <Divider />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="ghost" size="sm" className="h-8 gap-1 px-2 text-xs">
            <TableIcon className="h-4 w-4" /> Tableau <ChevronDown className="h-3 w-3" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start" className="w-56">
          <DropdownMenuItem onClick={() => editor.chain().focus().addRowBefore().run()}>
            <Rows3 className="mr-2 h-4 w-4" /> Insérer une ligne au-dessus
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().addRowAfter().run()}>
            <Rows3 className="mr-2 h-4 w-4" /> Insérer une ligne en dessous
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().deleteRow().run()}>
            <Trash2 className="mr-2 h-4 w-4" /> Supprimer la ligne
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => editor.chain().focus().addColumnBefore().run()}>
            <Columns3 className="mr-2 h-4 w-4" /> Insérer une colonne à gauche
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().addColumnAfter().run()}>
            <Columns3 className="mr-2 h-4 w-4" /> Insérer une colonne à droite
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().deleteColumn().run()}>
            <Trash2 className="mr-2 h-4 w-4" /> Supprimer la colonne
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => editor.chain().focus().mergeCells().run()}>
            <Merge className="mr-2 h-4 w-4" /> Fusionner les cellules
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().splitCell().run()}>
            <Scissors className="mr-2 h-4 w-4" /> Scinder la cellule
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().toggleHeaderRow().run()}>
            <Rows3 className="mr-2 h-4 w-4" /> Ligne d&apos;en-tête
          </DropdownMenuItem>
          <DropdownMenuItem onClick={() => editor.chain().focus().toggleHeaderColumn().run()}>
            <Columns3 className="mr-2 h-4 w-4" /> Colonne d&apos;en-tête
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => editor.chain().focus().deleteTable().run()} className="text-destructive">
            <Trash2 className="mr-2 h-4 w-4" /> Supprimer le tableau
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

/** Redimensionnement par paliers plutôt que poignée de glissement : une largeur en pourcentage se transpose exactement à l'export. */
function ImageMenu({ editor }: { editor: Editor }) {
  return (
    <>
      <Divider />
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button type="button" variant="ghost" size="sm" className="h-8 gap-1 px-2 text-xs">
            <ImagePlus className="h-4 w-4" /> Image <ChevronDown className="h-3 w-3" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="start">
          {IMAGE_WIDTHS.map((width) => (
            <DropdownMenuItem
              key={width.value}
              onClick={() => editor.chain().focus().updateAttributes('image', { width: width.value }).run()}
            >
              Largeur {width.label}
            </DropdownMenuItem>
          ))}
          <DropdownMenuSeparator />
          <DropdownMenuItem onClick={() => editor.chain().focus().deleteSelection().run()} className="text-destructive">
            <Trash2 className="mr-2 h-4 w-4" /> Supprimer l&apos;image
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
    </>
  );
}

function ColorPicker({
  icon: Icon,
  label,
  colors,
  current,
  onPick,
  onReset,
}: {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  colors: string[];
  current?: string;
  onPick: (color: string) => void;
  onReset: () => void;
}) {
  const [open, setOpen] = useState(false);

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="icon" className="h-8 w-8" aria-label={label} title={label}>
          <span className="flex flex-col items-center">
            <Icon className="h-3.5 w-3.5" />
            <span className="mt-0.5 h-1 w-4 rounded-sm border border-border" style={{ backgroundColor: current ?? 'transparent' }} />
          </span>
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-auto p-2" align="start">
        <div className="grid grid-cols-6 gap-1">
          {colors.map((color) => (
            <button
              key={color}
              type="button"
              aria-label={color}
              title={color}
              onClick={() => {
                onPick(color);
                setOpen(false);
              }}
              className="h-5 w-5 rounded-[3px] border border-border"
              style={{ backgroundColor: color }}
            />
          ))}
        </div>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          className="mt-2 h-7 w-full text-xs"
          onClick={() => {
            onReset();
            setOpen(false);
          }}
        >
          Aucune
        </Button>
      </PopoverContent>
    </Popover>
  );
}

function StatusBar({ editor }: { editor: Editor }) {
  const words = editor.storage.characterCount.words();
  const characters = editor.storage.characterCount.characters();

  return (
    <div className="flex items-center gap-4 border-t border-border bg-card px-3 py-1.5 text-xs text-muted-foreground">
      <span>
        {words} mot{words > 1 ? 's' : ''}
      </span>
      <span>
        {characters} caractère{characters > 1 ? 's' : ''}
      </span>
    </div>
  );
}

function Divider() {
  return <div className="mx-1 h-6 w-px bg-border" aria-hidden />;
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
