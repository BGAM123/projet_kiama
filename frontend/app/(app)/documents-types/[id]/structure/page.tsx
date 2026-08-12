'use client';

import { useState, useEffect } from 'react';
import { useParams, useRouter } from 'next/navigation';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowLeft,
  Check,
  ChevronDown,
  ChevronRight,
  FileText,
  GripVertical,
  Pencil,
  Plus,
  RefreshCw,
  Save,
  Table2,
  Trash2,
  Type,
  List,
  CheckCircle2,
  PenLine,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { DocumentTypeStatusBadge } from '@/components/status-badge';
import { useDocumentTypes, useStructure } from '@/lib/hooks/queries';
import { updateStructure, validateDocumentType, reextractDocumentType } from '@/lib/api/client';
import type { StructureNode, DocumentType } from '@/types';

const nodeIcon: Record<StructureNode['type'], React.ComponentType<{ className?: string }>> = {
  heading: Type,
  paragraph: Type,
  // Emplacement de paragraphe à rédiger manuellement (flux "décrire en texte -> squelette généré par IA").
  paragraph_placeholder: PenLine,
  table: Table2,
  list: List,
  cover: FileText,
};

function clone(tree: StructureNode[]): StructureNode[] {
  return tree.map((n) => ({ ...n, children: n.children ? clone(n.children) : undefined }));
}

export default function StructurePreviewPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const qc = useQueryClient();
  const { data: documentTypes, isLoading: dtLoading } = useDocumentTypes();
  const { data: structure, isLoading } = useStructure(params.id);
  const docType = documentTypes?.find((d: DocumentType) => d.id === params.id);

  const [tree, setTree] = useState<StructureNode[]>([]);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [draftLabel, setDraftLabel] = useState('');

  useEffect(() => {
    if (structure) setTree(structure.tree);
  }, [structure]);

  const saveStructure = useMutation({
    mutationFn: async (t: StructureNode[]) => (await updateStructure(params.id, t)).data,
  });

  // POST /document-types/{id}/validate (Bloc 4) : n'accepte la transition
  // que depuis STRUCTURE_EXTRAITE ou EN_VALIDATION (rejet 409 sinon, cf.
  // DocumentTypeService#validate côté backend) — le bouton reste désactivé
  // tant que la structure n'a pas été extraite avec succès.
  const validate = useMutation({
    mutationFn: async () => (await validateDocumentType(params.id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      toast.success('Document Type activé', { description: 'Il est désormais disponible pour la génération.' });
      router.push('/documents-types');
    },
    onError: (e: Error) => toast.error("Impossible d'activer ce Document Type", { description: e.message }),
  });

  // POST /document-types/{id}/extract : relance l'extraction à partir du
  // fichier source déjà stocké — utile après un ECHEC_EXTRACTION (format
  // limite mal supporté, etc.) sans avoir à réimporter le fichier.
  const reextract = useMutation({
    mutationFn: async () => (await reextractDocumentType(params.id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      qc.invalidateQueries({ queryKey: ['structure', params.id] });
      toast.success('Extraction relancée.');
    },
    onError: (e: Error) => toast.error("Échec de l'extraction", { description: e.message }),
  });

  const canValidate = docType?.status === 'STRUCTURE_EXTRAITE' || docType?.status === 'EN_VALIDATION';
  const canReextract = docType?.status === 'ECHEC_EXTRACTION';

  function updateNode(id: string, patch: Partial<StructureNode>, nodes = tree): StructureNode[] {
    return nodes.map((n) => {
      if (n.id === id) return { ...n, ...patch };
      if (n.children) return { ...n, children: updateNode(id, patch, n.children) };
      return n;
    });
  }

  function removeNode(id: string, nodes = tree): StructureNode[] {
    return nodes
      .filter((n) => n.id !== id)
      .map((n) => (n.children ? { ...n, children: removeNode(id, n.children) } : n));
  }

  function addChild(parentId: string | null) {
    const newNode: StructureNode = {
      id: `new-${Math.random().toString(36).slice(2, 8)}`,
      type: 'heading',
      level: 2,
      label: 'Nouvelle section',
    };
    if (!parentId) {
      setTree([...tree, newNode]);
      return;
    }
    setTree(updateNode(parentId, {}, tree.map((n) => n.id === parentId ? { ...n, children: [...(n.children ?? []), newNode] } : n)));
  }

  function startEdit(node: StructureNode) {
    setEditingId(node.id);
    setDraftLabel(node.label);
  }

  function commitEdit() {
    if (editingId) {
      setTree(updateNode(editingId, { label: draftLabel || 'Sans titre' }));
    }
    setEditingId(null);
  }

  function save() {
    saveStructure.mutate(tree, {
      onSuccess: () => {
        qc.invalidateQueries({ queryKey: ['structure', params.id] });
        toast.success('Structure enregistrée.');
      },
    });
  }

  if (isLoading || dtLoading) {
    return (
      <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
        <Skeleton className="h-12 w-full" />
        <div className="grid gap-6 lg:grid-cols-2"><Skeleton className="h-96" /><Skeleton className="h-96" /></div>
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title={docType ? `Structure — ${docType.name}` : 'Structure du Document Type'}
        description="Vérifiez et corrigez l'arborescence extraite avant activation."
        icon={FileText}
        actions={
          <div className="flex items-center gap-2">
            <Button asChild variant="ghost" size="sm"><Link href="/documents-types"><ArrowLeft className="mr-2 h-4 w-4" /> Retour</Link></Button>
            <Button onClick={save} disabled={saveStructure.isPending} variant="outline" size="sm">
              <Save className="mr-2 h-4 w-4" /> Enregistrer
            </Button>
            {canReextract && (
              <Button onClick={() => reextract.mutate()} disabled={reextract.isPending} variant="outline" size="sm">
                <RefreshCw className="mr-2 h-4 w-4" /> Relancer l'extraction
              </Button>
            )}
            <Button
              onClick={() => validate.mutate()}
              disabled={!canValidate || validate.isPending}
              title={canValidate ? undefined : "Une structure extraite avec succès est requise avant l'activation."}
              className="bg-accent text-accent-foreground hover:bg-accent/90"
              size="sm"
            >
              <CheckCircle2 className="mr-2 h-4 w-4" /> Valider & activer
            </Button>
          </div>
        }
      />

      {docType && (
        <div className="flex items-center gap-3 text-sm">
          <span className="text-muted-foreground">Statut :</span>
          <DocumentTypeStatusBadge status={docType.status} />
          <span className="text-muted-foreground">· Version v{docType.version}</span>
        </div>
      )}

      <div className="grid gap-6 lg:grid-cols-2">
        {/* Editable tree */}
        <Card>
          <CardContent className="p-4">
            <h2 className="mb-3 text-sm font-semibold uppercase tracking-wider text-muted-foreground">Arborescence structurelle</h2>
            <div className="max-h-[65vh] overflow-y-auto pr-1">
              <ul className="space-y-1">
                {tree.map((node) => (
                  <TreeRow
                    key={node.id}
                    node={node}
                    depth={0}
                    editingId={editingId}
                    draftLabel={draftLabel}
                    onEdit={startEdit}
                    onDraft={setDraftLabel}
                    onCommit={commitEdit}
                    onRemove={(id) => setTree(removeNode(id))}
                    onAddChild={addChild}
                  />
                ))}
              </ul>
            </div>
            <Button variant="outline" size="sm" className="mt-3 w-full border-dashed" onClick={() => addChild(null)}>
              <Plus className="mr-2 h-4 w-4" /> Ajouter une section
            </Button>
          </CardContent>
        </Card>

        {/* Visual preview */}
        <Card className="bg-muted/20">
          <CardContent className="p-6">
            <h2 className="mb-4 text-sm font-semibold uppercase tracking-wider text-muted-foreground">Aperçu visuel</h2>
            <div className="max-h-[65vh] overflow-y-auto rounded-lg border border-border bg-background p-6 shadow-sm">
              {/* Cover */}
              {tree.find((n) => n.type === 'cover') && (
                <div className="mb-6 rounded-md bg-primary/5 px-4 py-8 text-center">
                  <p className="text-xs uppercase tracking-widest text-muted-foreground">Page de garde</p>
                  <p className="mt-2 text-lg font-semibold">{docType?.name}</p>
                </div>
              )}
              {tree.filter((n) => n.type !== 'cover').map((node) => (
                <PreviewNode key={node.id} node={node} />
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}

function TreeRow({
  node,
  depth,
  editingId,
  draftLabel,
  onEdit,
  onDraft,
  onCommit,
  onRemove,
  onAddChild,
}: {
  node: StructureNode;
  depth: number;
  editingId: string | null;
  draftLabel: string;
  onEdit: (n: StructureNode) => void;
  onDraft: (s: string) => void;
  onCommit: () => void;
  onRemove: (id: string) => void;
  onAddChild: (parentId: string | null) => void;
}) {
  const [expanded, setExpanded] = useState(true);
  const Icon = nodeIcon[node.type];
  const hasChildren = !!node.children?.length;
  const isEditing = editingId === node.id;

  return (
    <li>
      <div
        className="group flex items-center gap-2 rounded-md px-2 py-1.5 hover:bg-muted/50"
        style={{ paddingLeft: `${depth * 16 + 8}px` }}
      >
        <GripVertical className="h-4 w-4 flex-none cursor-grab text-muted-foreground/40" />
        {hasChildren ? (
          <button aria-label={expanded ? 'Réduire' : 'Déplier'} onClick={() => setExpanded((e) => !e)} className="text-muted-foreground hover:text-foreground">
            {expanded ? <ChevronDown className="h-4 w-4" /> : <ChevronRight className="h-4 w-4" />}
          </button>
        ) : (
          <span className="w-4" />
        )}
        <Icon className="h-4 w-4 flex-none text-primary" />
        {isEditing ? (
          <Input
            autoFocus
            value={draftLabel}
            onChange={(e) => onDraft(e.target.value)}
            onBlur={onCommit}
            onKeyDown={(e) => e.key === 'Enter' && onCommit()}
            className="h-7 flex-1"
          />
        ) : (
          <span className="flex-1 text-sm">{node.label}</span>
        )}
        {!isEditing && (
          <div className="flex flex-none items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
            <button aria-label="Renommer" className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground" onClick={() => onEdit(node)}>
              <Pencil className="h-3.5 w-3.5" />
            </button>
            {node.type === 'heading' && (
              <button aria-label="Ajouter une sous-section" className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground" onClick={() => onAddChild(node.id)}>
                <Plus className="h-3.5 w-3.5" />
              </button>
            )}
            <button aria-label="Supprimer" className="rounded p-1 text-muted-foreground hover:bg-destructive/10 hover:text-destructive" onClick={() => onRemove(node.id)}>
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
        )}
      </div>
      {expanded && hasChildren && (
        <ul>
          {node.children!.map((c: StructureNode) => (
            <TreeRow
              key={c.id}
              node={c}
              depth={depth + 1}
              editingId={editingId}
              draftLabel={draftLabel}
              onEdit={onEdit}
              onDraft={onDraft}
              onCommit={onCommit}
              onRemove={onRemove}
              onAddChild={onAddChild}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

function PreviewNode({ node }: { node: StructureNode }) {
  if (node.type === 'heading') {
    const Tag = (`h${Math.min(Math.max(node.level ?? 2, 1), 3)}`) as 'h1' | 'h2' | 'h3';
    const cls = Tag === 'h1' ? 'mt-6 mb-2 text-xl font-semibold' : Tag === 'h2' ? 'mt-4 mb-1.5 text-base font-semibold' : 'mt-3 mb-1 text-sm font-medium';
    return (
      <>
        <Tag className={cls}>{node.label}</Tag>
        <p className="mb-3 text-sm text-muted-foreground/70">Lorem ipsum dolor sit amet, consectetur adipiscing elit. Sed do eiusmod tempor incididunt ut labore.</p>
        {node.children?.map((c) => <PreviewNode key={c.id} node={c} />)}
      </>
    );
  }
  if (node.type === 'table' && node.columns?.length) {
    return (
      <div className="my-3 overflow-x-auto">
        <p className="mb-1 text-xs font-medium text-muted-foreground">{node.label}</p>
        <table className="w-full border-collapse text-xs">
          <thead>
            <tr>{node.columns.map((c) => <th key={c} className="border border-border bg-muted/50 px-2 py-1.5 text-left font-medium">{c}</th>)}</tr>
          </thead>
          <tbody>
            <tr>{node.columns.map((c) => <td key={c} className="border border-border px-2 py-1.5">—</td>)}</tr>
          </tbody>
        </table>
      </div>
    );
  }
  if (node.type === 'list') {
    return (
      <ul className="my-2 ml-5 list-disc space-y-1 text-sm text-muted-foreground/80">
        <li>{node.label} — point 1</li>
        <li>{node.label} — point 2</li>
        <li>{node.label} — point 3</li>
      </ul>
    );
  }
  return <p className="my-2 text-sm text-muted-foreground/70">{node.label}</p>;
}
