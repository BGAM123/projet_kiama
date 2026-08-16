'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { History, Search, FileText, Pencil } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { DocumentStatusBadge } from '@/components/status-badge';
import { useDocumentTypes, useCategories, useDocuments } from '@/lib/hooks/queries';
import { formatDateTime } from '@/lib/format';
import type { AppDocument, Category, DocumentType } from '@/types';

export default function HistoryPage() {
  const { data: documentTypes } = useDocumentTypes();
  const { data: categories } = useCategories();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [catFilter, setCatFilter] = useState('all');

  // GET /api/v1/documents?userId= trie par date de création ; on retrie ici
  // par date de mise à jour, plus pertinent pour un historique (un document
  // édité récemment doit remonter même si créé plus tôt).
  const { data: docs, isLoading } = useDocuments();
  const docsList: AppDocument[] = useMemo(
    () => [...(docs ?? [])].sort((a, b) => (b.updatedAt ?? b.createdAt).localeCompare(a.updatedAt ?? a.createdAt)),
    [docs],
  );

  const filtered = useMemo(() => {
    return docsList
      .filter((d: AppDocument) => (statusFilter === 'all' ? true : d.status === statusFilter))
      .filter((d: AppDocument) => {
        if (catFilter === 'all') return true;
        const dt = documentTypes?.find((t: DocumentType) => t.id === d.documentTypeId);
        return dt?.categoryId === catFilter;
      })
      .filter((d: AppDocument) => {
        if (!search.trim()) return true;
        return (d.title ?? '').toLowerCase().includes(search.toLowerCase());
      });
  }, [docsList, statusFilter, catFilter, search, documentTypes]);

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Historique" description="Tous vos documents." icon={History} />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Rechercher un document…" className="pl-9" value={search} onChange={(e) => setSearch(e.target.value)} aria-label="Recherche" />
          </div>
          <Select value={catFilter} onValueChange={setCatFilter}>
            <SelectTrigger className="w-full lg:w-48" aria-label="Catégorie"><SelectValue placeholder="Catégorie" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Toutes catégories</SelectItem>
              {categories?.map((c: Category) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={statusFilter} onValueChange={setStatusFilter}>
            <SelectTrigger className="w-full lg:w-44" aria-label="Statut"><SelectValue placeholder="Statut" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Tous statuts</SelectItem>
              {Object.entries({ BROUILLON: 'Brouillon', SAUVEGARDE: 'Sauvegardé', FINALISE: 'Finalisé', ARCHIVE: 'Archivé' }).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-3 p-4">{Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div>
          ) : filtered.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-16 text-center">
              <FileText className="h-10 w-10 text-muted-foreground/50" />
              <p className="text-sm text-muted-foreground">Aucun document trouvé.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {filtered.map((d: AppDocument) => (
                <li key={d.id} className="flex items-center justify-between gap-4 p-4 transition-colors hover:bg-muted/30">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-10 w-10 flex-none items-center justify-center rounded-md bg-primary/10 text-primary">
                      <FileText className="h-5 w-5" />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-medium">{d.title ?? '—'}</p>
                      <p className="truncate text-xs text-muted-foreground">{formatDateTime(d.updatedAt ?? d.createdAt)}</p>
                    </div>
                  </div>
                  <div className="flex flex-none items-center gap-3">
                    <DocumentStatusBadge status={d.status} />
                    <Button asChild variant="ghost" size="sm"><Link href={`/documents/${d.id}`}><Pencil className="mr-1 h-3.5 w-3.5" /> Ouvrir</Link></Button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
