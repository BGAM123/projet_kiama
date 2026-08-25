'use client';

import { useRef, useState } from 'react';
import { NodeViewWrapper, type NodeViewProps } from '@tiptap/react';
import { AlignCenter, AlignLeft, AlignRight } from 'lucide-react';
import { cn } from '@/lib/utils';

const MIN_WIDTH_PX = 48;

const ALIGN_STYLE: Record<string, React.CSSProperties> = {
  left: { float: 'left', margin: '0.25rem 1rem 0.75rem 0' },
  right: { float: 'right', margin: '0.25rem 0 0.75rem 1rem' },
  center: { display: 'block', margin: '0.5rem auto' },
};

/**
 * NodeView de l'image du document (`DocumentImage`, word-editor.tsx) :
 * poignée de redimensionnement et alignement directement sur l'image
 * sélectionnée, plutôt que par un menu séparé — même principe que le
 * redimensionnement de colonnes d'un tableau (poignée sur l'objet lui-même).
 * La largeur glissée et l'alignement choisi sont écrits dans les attributs du
 * nœud ({@link updateAttributes}), donc persistés dans le HTML exactement
 * comme avant (voir `DocumentImage.addAttributes`).
 */
export function ImageNodeView({ node, updateAttributes, selected, editor }: NodeViewProps) {
  const imgRef = useRef<HTMLImageElement>(null);
  const [liveWidthPx, setLiveWidthPx] = useState<number | null>(null);
  const [resizing, setResizing] = useState(false);

  const align = (node.attrs.align as string) || 'center';
  const editable = editor.isEditable;

  function startResize(event: React.PointerEvent) {
    event.preventDefault();
    event.stopPropagation();
    const img = imgRef.current;
    if (!img) return;

    const startX = event.clientX;
    const startWidth = img.getBoundingClientRect().width;
    setResizing(true);

    function clamp(px: number) {
      // Ne dépasse jamais la largeur de la page — cohérent avec `max-width: 100%` déjà posé sur .word-page-content img.
      const maxWidth = img?.parentElement?.closest('.word-page-content')?.clientWidth ?? Infinity;
      return Math.min(Math.max(MIN_WIDTH_PX, Math.round(px)), maxWidth);
    }

    function onMove(moveEvent: PointerEvent) {
      const delta = align === 'right' ? startX - moveEvent.clientX : moveEvent.clientX - startX;
      setLiveWidthPx(clamp(startWidth + delta));
    }
    function onUp(upEvent: PointerEvent) {
      window.removeEventListener('pointermove', onMove);
      window.removeEventListener('pointerup', onUp);
      const delta = align === 'right' ? startX - upEvent.clientX : upEvent.clientX - startX;
      updateAttributes({ width: `${clamp(startWidth + delta)}px` });
      setResizing(false);
      setLiveWidthPx(null);
    }
    window.addEventListener('pointermove', onMove);
    window.addEventListener('pointerup', onUp);
  }

  return (
    <NodeViewWrapper
      as="div"
      data-drag-handle
      style={{ ...ALIGN_STYLE[align], width: 'fit-content', position: 'relative', lineHeight: 0 }}
    >
      <img
        ref={imgRef}
        src={node.attrs.src as string}
        alt={(node.attrs.alt as string) ?? ''}
        title={(node.attrs.title as string) ?? undefined}
        draggable={false}
        style={{
          width: liveWidthPx ? `${liveWidthPx}px` : (node.attrs.width as string) || undefined,
          display: 'block',
          height: 'auto',
          maxWidth: '100%',
          outline: selected ? '2px solid hsl(var(--primary))' : undefined,
          outlineOffset: 2,
          cursor: editable ? 'grab' : undefined,
        }}
      />

      {editable && selected && (
        <>
          <div className="absolute -top-10 left-1/2 z-10 flex -translate-x-1/2 items-center gap-0.5 rounded-md border border-border bg-card p-1 shadow-md">
            {(['left', 'center', 'right'] as const).map((value) => {
              const Icon = value === 'left' ? AlignLeft : value === 'right' ? AlignRight : AlignCenter;
              const label = value === 'left' ? 'Aligner à gauche' : value === 'right' ? 'Aligner à droite' : 'Centrer';
              return (
                <button
                  key={value}
                  type="button"
                  aria-label={label}
                  title={label}
                  // Sans ceci, le mousedown sur le bouton déplacerait la sélection avant le clic.
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => updateAttributes({ align: value })}
                  className={cn(
                    'flex h-7 w-7 items-center justify-center rounded transition-colors hover:bg-muted',
                    align === value && 'bg-primary/10 text-primary',
                  )}
                >
                  <Icon className="h-3.5 w-3.5" />
                </button>
              );
            })}
          </div>

          <span
            role="slider"
            aria-label="Redimensionner l'image"
            aria-valuenow={liveWidthPx ?? undefined}
            tabIndex={-1}
            onPointerDown={startResize}
            className={cn(
              'absolute bottom-0 right-0 z-10 h-3.5 w-3.5 translate-x-1/2 translate-y-1/2 cursor-se-resize rounded-full border-2 border-primary bg-background shadow-sm',
              resizing && 'scale-110',
            )}
          />
        </>
      )}
    </NodeViewWrapper>
  );
}
