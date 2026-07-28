'use client';

import { useMemo, useState } from 'react';
import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import { History, Search, FileText, Pencil } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { GeneratedStatusBadge } from '@/components/status-badge';
import { useAuth } from '@/lib/auth-store';
import { useDocumentTypes, useCategories } from '@/lib/hooks/queries';
import { formatDateTime } from '@/lib/format';
import type { Category, DocumentType, GeneratedDocument } from '@/types';

export default function HistoryPage() {
  const session = useAuth((s) => s.session);
  const { data: documentTypes } = useDocumentTypes();
  const { data: categories } = useCategories();
  const [search, setSearch] = useState('');
  const [statusFilter, setStatusFilter] = useState('all');
  const [catFilter, setCatFilter] = useState('all');

  const { data: docs, isLoading } = useQuery<GeneratedDocument[]>({
    queryKey: ['all-generations', session?.user.id],
    queryFn: async () => {
      const { generatedDocuments } = await import('@/lib/api/fixtures');
      return generatedDocuments
        .filter((g) => g.userId === session!.user.id)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt));
    },
    enabled: !!session,
  });

  const docsList: GeneratedDocument[] = docs ?? [];

  const filtered = useMemo(() => {
    return docsList
      .filter((g: GeneratedDocument) => (statusFilter === 'all' ? true : g.status === statusFilter))
      .filter((g: GeneratedDocument) => {
        if (catFilter === 'all') return true;
        const dt = documentTypes?.find((d: DocumentType) => d.id === g.documentTypeId);
        return dt?.categoryId === catFilter;
      })
      .filter((g: GeneratedDocument) => (search.trim() ? g.contentPivot.toLowerCase().includes(search.toLowerCase()) : true));
  }, [docsList, statusFilter, catFilter, search, documentTypes]);

  const docTypeName = (id: string) => documentTypes?.find((d: DocumentType) => d.id === id)?.name ?? '—';

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Historique" description="Toutes vos conversations et documents générés." icon={History} />

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
              {Object.entries({ BROUILLON: 'Brouillon', EN_GENERATION: 'En génération', GENERE: 'Généré', ECHEC: 'Échec', EN_EDITION: 'En édition', EXPORTE: 'Exporté', ARCHIVE: 'Archivé' }).map(([k, v]) => <SelectItem key={k} value={k}>{v}</SelectItem>)}
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
              {filtered.map((g: GeneratedDocument) => (
                <li key={g.id} className="flex items-center justify-between gap-4 p-4 transition-colors hover:bg-muted/30">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-10 w-10 flex-none items-center justify-center rounded-md bg-primary/10 text-primary">
                      <FileText className="h-5 w-5" />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate font-medium">{g.contentPivot}</p>
                      <p className="truncate text-xs text-muted-foreground">{docTypeName(g.documentTypeId)} · {formatDateTime(g.updatedAt)}</p>
                    </div>
                  </div>
                  <div className="flex flex-none items-center gap-3">
                    <GeneratedStatusBadge status={g.status} />
                    {(g.status === 'GENERE' || g.status === 'EN_EDITION' || g.status === 'EXPORTE') && (
                      <Button asChild variant="ghost" size="sm"><Link href={`/editor/${g.id}`}><Pencil className="mr-1 h-3.5 w-3.5" /> Éditer</Link></Button>
                    )}
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
