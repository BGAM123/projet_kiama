'use client';

import { useState, useMemo } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import {
  ArrowDownUp,
  ChevronRight,
  Eye,
  FileText,
  Layers,
  Pencil,
  Plus,
  Search,
  Trash2,
  Upload,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { DocumentTypeStatusBadge } from '@/components/status-badge';
import { useDocumentTypes, useCategories, useStructure } from '@/lib/hooks/queries';
import { deleteDocumentType } from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import { formatDate } from '@/lib/format';
import type { DocumentType, Category, StructureNode } from '@/types';
import {
  Dialog,
  DialogContent,
  DialogDescription,
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

  const remove = useMutation({
    mutationFn: async (id: string) => (await deleteDocumentType(id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['document-types'] });
      toast.success('Document Type archivé.');
    },
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
            <Button asChild className="bg-accent text-accent-foreground hover:bg-accent/90">
              <Link href="/documents-types/import"><Upload className="mr-2 h-4 w-4" /> Importer un document</Link>
            </Button>
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
    </div>
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

