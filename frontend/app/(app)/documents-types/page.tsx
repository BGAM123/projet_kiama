'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertCircle,
  ArrowDownUp,
  ChevronRight,
  Eye,
  FileText,
  Layers,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  Search,
  Settings2,
  Sparkles,
  Trash2,
  Upload,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { DocumentTypeStatusBadge } from '@/components/status-badge';
import { useDocumentTypes, useCategories, useStructure } from '@/lib/hooks/queries';
import { deleteDocumentType, generateDocumentType, reextractDocumentType, updateDocumentType } from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import { formatDate } from '@/lib/format';
import type { DocumentType, Category, StructureNode } from '@/types';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';

type SortKey = 'name' | 'status' | 'version' | 'createdAt';

export default function DocumentTypesPage() {
  const router = useRouter();
  const hasRole = useAuth((s) => s.hasRole);
  const isAdmin = hasRole('ADMIN');
  const { data: documentTypes, isLoading } = useDocumentTypes();
  const { data: categories } = useCategories();
  const qc = useQueryClient();

  const [search, setSearch] = useState('');
  const [categoryFilter, setCategoryFilter] = useState<string>('all');
  const [statusFilter, setStatusFilter] = useState<string>('all');
  const [sortKey, setSortKey] = useState<SortKey>('createdAt');
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('desc');
  const [preview, setPreview] = useState<DocumentType | null>(null);
  const [editing, setEditing] = useState<DocumentType | null>(null);
  const [generating, setGenerating] = useState(false);

  const remove = useMutation({
    mutationFn: async (id: string) => (await deleteDocumentType(id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      toast.success('Document Type archivé.');
    },
  });

  // Relance rapide depuis la liste après un ECHEC_EXTRACTION, sans passer
  // par l'écran de structure (POST /document-types/{id}/extract, Bloc 4).
  const reextract = useMutation({
    mutationFn: async (id: string) => (await reextractDocumentType(id)).data,
    onSuccess: (dt) => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      qc.invalidateQueries({ queryKey: ['structure', dt.id] });
      toast.success(dt.status === 'ECHEC_EXTRACTION' ? "L'extraction a échoué à nouveau." : 'Extraction relancée avec succès.');
    },
    onError: (e: Error) => toast.error("Échec de l'extraction", { description: e.message }),
  });

  const filtered = useMemo(() => {
    if (!documentTypes) return [];
    return documentTypes
      .filter((d: DocumentType) => (categoryFilter === 'all' ? true : d.categoryId === categoryFilter))
      .filter((d: DocumentType) => (statusFilter === 'all' ? true : d.status === statusFilter))
      .filter((d: DocumentType) =>
        search.trim()
          ? d.name.toLowerCase().includes(search.toLowerCase()) ||
            d.description.toLowerCase().includes(search.toLowerCase())
          : true,
      )
      .sort((a: DocumentType, b: DocumentType) => {
        const dir = sortDir === 'asc' ? 1 : -1;
        if (sortKey === 'name') return a.name.localeCompare(b.name) * dir;
        if (sortKey === 'status') return a.status.localeCompare(b.status) * dir;
        if (sortKey === 'version') return (a.version - b.version) * dir;
        return a.createdAt.localeCompare(b.createdAt) * dir;
      });
  }, [documentTypes, categoryFilter, statusFilter, search, sortKey, sortDir]);

  const catName = (id: string) => categories?.find((c: Category) => c.id === id)?.name ?? '—';

  function toggleSort(key: SortKey) {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Documents Types"
        description="Structures documentaires extraites prêtes pour la génération."
        icon={Layers}
        actions={
          isAdmin && (
            <div className="flex gap-2">
              <Button variant="outline" onClick={() => setGenerating(true)}>
                <Sparkles className="mr-2 h-4 w-4" /> Générer avec l&apos;IA
              </Button>
              <Button asChild className="bg-accent text-accent-foreground hover:bg-accent/90">
                <Link href="/documents-types/import"><Upload className="mr-2 h-4 w-4" /> Importer un document</Link>
              </Button>
            </div>
          )
        }
      />

      {/* Filters */}
      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Rechercher un Document Type…" className="pl-9" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Recherche" />
          </div>
          <Select value={categoryFilter} onValueChange={setCategoryFilter}>
            <SelectTrigger className="w-full lg:w-48" aria-label="Filtrer par catégorie"><SelectValue placeholder="Catégorie" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Toutes les catégories</SelectItem>
              {categories?.map((c: Category) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-full lg:w-44" aria-label="Filtrer par statut"><SelectValue placeholder="Statut" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous les statuts</SelectItem>
              {Object.entries({ ACTIF: 'Actif', EN_VALIDATION: 'En validation', STRUCTURE_EXTRAITE: 'Structure extraite', EN_EXTRACTION: 'Extraction en cours', IMPORTE: 'Importé', ECHEC_EXTRACTION: 'Échec', ARCHIVE: 'Archivé' }).map(([k, v]) => (
                <SelectItem key={k} value={k}>{v}</SelectItem>
              ))}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      {/* Table */}
      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">
                    <button className="flex items-center gap-1 hover:text-foreground" onClick={() => toggleSort('name')}>Nom {sortKey === 'name' && <ArrowDownUp className="h-3 w-3" />}</button>
                  </th>
                  <th className="px-4 py-3">Catégorie</th>
                  <th className="px-4 py-3">
                    <button className="flex items-center gap-1 hover:text-foreground" onClick={() => toggleSort('status')}>Statut {sortKey === 'status' && <ArrowDownUp className="h-3 w-3" />}</button>
                  </th>
                  <th className="px-4 py-3 text-center">
                    <button className="flex items-center gap-1 hover:text-foreground" onClick={() => toggleSort('version')}>Version {sortKey === 'version' && <ArrowDownUp className="h-3 w-3" />}</button>
                  </th>
                  <th className="px-4 py-3">
                    <button className="flex items-center gap-1 hover:text-foreground" onClick={() => toggleSort('createdAt')}>Créé le {sortKey === 'createdAt' && <ArrowDownUp className="h-3 w-3" />}</button>
                  </th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i}>
                      <td className="px-4 py-3" colSpan={6}><Skeleton className="h-10 w-full" /></td>
                    </tr>
                  ))
                ) : filtered.length === 0 ? (
                  <tr>
                    <td colSpan={6} className="px-4 py-16 text-center text-muted-foreground">
                      Aucun Document Type ne correspond à vos filtres.
                    </td>
                  </tr>
                ) : (
                  filtered.map((d: DocumentType) => (
                    <tr key={d.id} className="group transition-colors hover:bg-muted/30">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 flex-none items-center justify-center rounded-md bg-primary/10 text-primary">
                            <FileText className="h-4 w-4" />
                          </div>
                          <div className="min-w-0">
                            <p className="truncate font-medium">{d.name}</p>
                            <p className="truncate text-xs text-muted-foreground">{d.description}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{catName(d.categoryId)}</td>
                      <td className="px-4 py-3"><DocumentTypeStatusBadge status={d.status} /></td>
                      <td className="px-4 py-3 text-center text-muted-foreground">v{d.version}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatDate(d.createdAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex items-center justify-end gap-1">
                          <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Aperçu de la structure" onClick={() => setPreview(d)}>
                            <Eye className="h-4 w-4" />
                          </Button>
                          {isAdmin && (
                            <>
                              {d.status === 'ECHEC_EXTRACTION' && (
                                <Button
                                  variant="ghost"
                                  size="icon"
                                  className="h-8 w-8 text-warning hover:bg-warning/10"
                                  aria-label="Relancer l'extraction"
                                  disabled={reextract.isPending}
                                  onClick={() => reextract.mutate(d.id)}
                                >
                                  <RefreshCw className="h-4 w-4" />
                                </Button>
                              )}
                              <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Modifier les informations" onClick={() => setEditing(d)}>
                                <Settings2 className="h-4 w-4" />
                              </Button>
                              <Button variant="ghost" size="icon" className="h-8 w-8" aria-label="Éditer la structure" onClick={() => router.push(`/documents-types/${d.id}/structure`)}>
                                <Pencil className="h-4 w-4" />
                              </Button>
                              <Button
                                variant="ghost"
                                size="icon"
                                className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                                aria-label="Archiver"
                                disabled={remove.isPending}
                                onClick={() => {
                                  if (confirm(`Archiver « ${d.name} » ? Cette action est réversible.`)) {
                                    remove.mutate(d.id);
                                  }
                                }}
                              >
                                <Trash2 className="h-4 w-4" />
                              </Button>
                            </>
                          )}
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      <StructurePreviewDialog documentType={preview} onClose={() => setPreview(null)} />
      <EditDocumentTypeDialog documentType={editing} categories={categories} onClose={() => setEditing(null)} />
      <GenerateDocumentTypeDialog open={generating} categories={categories} onClose={() => setGenerating(false)} />
    </div>
  );
}

const editSchema = z.object({
  name: z.string().min(1, 'Nom requis.'),
  description: z.string().optional(),
  categoryId: z.string().min(1, 'Sélectionnez une catégorie.'),
});

type EditValues = z.infer<typeof editSchema>;

function EditDocumentTypeDialog({
  documentType,
  categories,
  onClose,
}: {
  documentType: DocumentType | null;
  categories: Category[] | undefined;
  onClose: () => void;
}) {
  const qc = useQueryClient();
  const form = useForm<EditValues>({ resolver: zodResolver(editSchema) });
  const [serverError, setServerError] = useState<string | null>(null);

  // Re-seed le formulaire à chaque ouverture (nouveau Document Type ciblé).
  const currentId = documentType?.id ?? null;
  const [seededFor, setSeededFor] = useState<string | null>(null);
  if (documentType && currentId !== seededFor) {
    form.reset({ name: documentType.name, description: documentType.description, categoryId: documentType.categoryId });
    setSeededFor(currentId);
  }

  const editMutation = useMutation({
    mutationFn: async (v: EditValues) =>
      (await updateDocumentType(documentType!.id, { name: v.name, description: v.description ?? '', categoryId: v.categoryId })).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      toast.success('Document Type modifié');
      onClose();
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const onSubmit = form.handleSubmit((v) => {
    setServerError(null);
    editMutation.mutate(v);
  });

  return (
    <Dialog open={!!documentType} onOpenChange={(o) => { if (!o) { onClose(); setSeededFor(null); setServerError(null); } }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Settings2 className="h-5 w-5 text-primary" /> Modifier les informations
          </DialogTitle>
          <DialogDescription>Nom, description et catégorie — le statut évolue via le pipeline d'extraction.</DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="edt-name">Nom</Label>
            <Input id="edt-name" {...form.register('name')} aria-invalid={!!form.formState.errors.name} />
            {form.formState.errors.name && <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="edt-description">Description</Label>
            <Textarea id="edt-description" rows={3} {...form.register('description')} />
          </div>
          <div className="space-y-2">
            <Label htmlFor="edt-categoryId">Catégorie</Label>
            <Select value={form.watch('categoryId')} onValueChange={(v) => form.setValue('categoryId', v, { shouldValidate: true })}>
              <SelectTrigger id="edt-categoryId" aria-label="Catégorie"><SelectValue placeholder="Sélectionnez une catégorie" /></SelectTrigger>
              <SelectContent>
                {categories?.map((c: Category) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
              </SelectContent>
            </Select>
            {form.formState.errors.categoryId && <p className="text-xs text-destructive">{form.formState.errors.categoryId.message}</p>}
          </div>
          {serverError && (
            <div role="alert" className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 flex-none" /> {serverError}
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Annuler</Button>
            <Button type="submit" disabled={editMutation.isPending} className="bg-accent text-accent-foreground hover:bg-accent/90">
              {editMutation.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Enregistrement…</> : 'Enregistrer'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

const generateSchema = z.object({
  name: z.string().min(1, 'Nom requis.'),
  description: z.string().min(10, 'Décrivez le type de document souhaité (10 caractères minimum).'),
  categoryId: z.string().min(1, 'Sélectionnez une catégorie.'),
});

type GenerateValues = z.infer<typeof generateSchema>;

/**
 * Flux "décrire en texte -> squelette généré par IA" (coexiste avec l'import
 * de fichier) : pas d'upload, une description en langage naturel suffit. Le
 * squelette produit (titres/sous-titres/tableaux, jamais de contenu rédigé)
 * atterrit sur l'écran de structure existant pour relecture/correction avant
 * validation, comme après un import de fichier.
 */
function GenerateDocumentTypeDialog({
  open,
  categories,
  onClose,
}: {
  open: boolean;
  categories: Category[] | undefined;
  onClose: () => void;
}) {
  const router = useRouter();
  const qc = useQueryClient();
  const form = useForm<GenerateValues>({ resolver: zodResolver(generateSchema), defaultValues: { name: '', description: '', categoryId: '' } });
  const [serverError, setServerError] = useState<string | null>(null);

  const generateMutation = useMutation({
    mutationFn: async (v: GenerateValues) => (await generateDocumentType(v)).data,
    onSuccess: (dt) => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      if (dt.status === 'ECHEC_EXTRACTION') {
        toast.error("L'IA n'a pas réussi à produire un squelette conforme après plusieurs tentatives.");
      } else {
        toast.success('Squelette généré — relisez-le avant de le valider.');
      }
      form.reset();
      onClose();
      router.push(`/documents-types/${dt.id}/structure`);
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const onSubmit = form.handleSubmit((v) => {
    setServerError(null);
    generateMutation.mutate(v);
  });

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) { onClose(); form.reset(); setServerError(null); } }}>
      <DialogContent className="max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <Sparkles className="h-5 w-5 text-primary" /> Générer avec l&apos;IA
          </DialogTitle>
          <DialogDescription>
            Décrivez le type de document souhaité — l&apos;IA propose uniquement un squelette (titres, sous-titres, tableaux), sans aucun contenu rédigé.
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={onSubmit} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="gdt-name">Nom</Label>
            <Input id="gdt-name" placeholder="Rapport d'audit interne" {...form.register('name')} aria-invalid={!!form.formState.errors.name} />
            {form.formState.errors.name && <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="gdt-description">Description</Label>
            <Textarea
              id="gdt-description"
              rows={5}
              placeholder="Ex. Rapport d'audit interne avec introduction, méthodologie, constats par thème (sous forme de tableau), recommandations et conclusion."
              {...form.register('description')}
              aria-invalid={!!form.formState.errors.description}
            />
            {form.formState.errors.description && <p className="text-xs text-destructive">{form.formState.errors.description.message}</p>}
          </div>
          <div className="space-y-2">
            <Label htmlFor="gdt-categoryId">Catégorie</Label>
            <Select value={form.watch('categoryId')} onValueChange={(v) => form.setValue('categoryId', v, { shouldValidate: true })}>
              <SelectTrigger id="gdt-categoryId" aria-label="Catégorie"><SelectValue placeholder="Sélectionnez une catégorie" /></SelectTrigger>
              <SelectContent>
                {categories?.map((c: Category) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
              </SelectContent>
            </Select>
            {form.formState.errors.categoryId && <p className="text-xs text-destructive">{form.formState.errors.categoryId.message}</p>}
          </div>
          {serverError && (
            <div role="alert" className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
              <AlertCircle className="h-4 w-4 flex-none" /> {serverError}
            </div>
          )}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>Annuler</Button>
            <Button type="submit" disabled={generateMutation.isPending} className="bg-accent text-accent-foreground hover:bg-accent/90">
              {generateMutation.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Génération…</> : <><Sparkles className="mr-2 h-4 w-4" /> Générer le squelette</>}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}

function StructurePreviewDialog({ documentType, onClose }: { documentType: DocumentType | null; onClose: () => void }) {
  const { data: structure, isLoading } = useStructure(documentType?.id ?? null);

  return (
    <Dialog open={!!documentType} onOpenChange={(o) => !o && onClose()}>
      <DialogContent className="max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <FileText className="h-5 w-5 text-primary" />
            {documentType?.name}
          </DialogTitle>
          <DialogDescription>Aperçu de la structure extraite.</DialogDescription>
        </DialogHeader>
        {isLoading ? (
          <div className="space-y-2"><Skeleton className="h-6 w-full" /><Skeleton className="h-6 w-3/4" /><Skeleton className="h-6 w-2/3" /></div>
        ) : (
          <ul className="space-y-1 text-sm">
            {(structure?.tree ?? []).map((node: StructureNode) => (
              <li key={node.id} className="rounded-md bg-muted/40 px-3 py-2">
                <div className="flex items-center gap-2">
                  <ChevronRight className="h-4 w-4 text-muted-foreground" />
                  <span className="font-medium">{node.label}</span>
                  <span className="ml-auto text-xs uppercase text-muted-foreground">{node.type}{node.level ? ` H${node.level}` : ''}</span>
                </div>
                {node.children?.length ? (
                  <ul className="ml-6 mt-1 space-y-1 border-l border-border pl-3">
                    {node.children.map((c: StructureNode) => (
                      <li key={c.id} className="text-sm text-muted-foreground">{c.label}</li>
                    ))}
                  </ul>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </DialogContent>
    </Dialog>
  );
}

