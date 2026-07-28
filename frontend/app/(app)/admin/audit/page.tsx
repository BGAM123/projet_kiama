'use client';

import { useMemo, useState } from 'react';
import { ScrollText, Search } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@/components/ui/button';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useAuditLogs } from '@/lib/hooks/queries';
import { formatDateTime } from '@/lib/format';
import type { AuditLogEntry } from '@/types';

const PAGE_SIZE = 8;

export default function AuditLogsPage() {
  const { data: logs, isLoading } = useAuditLogs();
  const [search, setSearch] = useState('');
  const [actionFilter, setActionFilter] = useState('all');
  const [page, setPage] = useState(0);

  const filtered = useMemo(() => {
    if (!logs) return [];
    return logs
      .filter((l: AuditLogEntry) => (actionFilter === 'all' ? true : l.action === actionFilter))
      .filter((l: AuditLogEntry) => (search.trim() ? (l.userName + l.action + l.entityType).toLowerCase().includes(search.toLowerCase()) : true));
  }, [logs, actionFilter, search]);

  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageItems = filtered.slice(page * PAGE_SIZE, page * PAGE_SIZE + PAGE_SIZE);

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Journal d'activité" description="Traçabilité des actions effectuées sur la plateforme." icon={ScrollText} />

      <Card>
        <CardContent className="flex flex-col gap-3 p-4 lg:flex-row lg:items-center">
          <div className="relative flex-1">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input placeholder="Rechercher (utilisateur, action, entité)…" className="pl-9" value={search} onChange={(e) => { setSearch(e.target.value); setPage(0); }} aria-label="Recherche" />
          </div>
          <Select value={actionFilter} onValueChange={(v) => { setActionFilter(v); setPage(0); }}>
            <SelectTrigger className="w-full lg:w-48" aria-label="Action"><SelectValue placeholder="Action" /></SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Toutes actions</SelectItem>
              {['LOGIN', 'CREATE', 'UPDATE', 'DELETE', 'GENERATE', 'EXPORT', 'IMPORT'].map((a) => <SelectItem key={a} value={a}>{a}</SelectItem>)}
            </SelectContent>
          </Select>
        </CardContent>
      </Card>

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Utilisateur</th>
                  <th className="px-4 py-3">Action</th>
                  <th className="px-4 py-3">Type d'entité</th>
                  <th className="px-4 py-3">ID</th>
                  <th className="px-4 py-3">Adresse IP</th>
                  <th className="px-4 py-3">Horodatage</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {isLoading ? (
                  Array.from({ length: 5 }).map((_, i) => <tr key={i}><td colSpan={6} className="px-4 py-3"><Skeleton className="h-8 w-full" /></td></tr>)
                ) : pageItems.length === 0 ? (
                  <tr><td colSpan={6} className="px-4 py-16 text-center text-muted-foreground">Aucune entrée.</td></tr>
                ) : (
                  pageItems.map((l: AuditLogEntry) => (
                    <tr key={l.id} className="hover:bg-muted/30">
                      <td className="px-4 py-3 font-medium">{l.userName}</td>
                      <td className="px-4 py-3"><span className="rounded bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">{l.action}</span></td>
                      <td className="px-4 py-3 text-muted-foreground">{l.entityType}</td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{l.entityId}</td>
                      <td className="px-4 py-3 font-mono text-xs text-muted-foreground">{l.ipAddress}</td>
                      <td className="px-4 py-3 text-muted-foreground">{formatDateTime(l.timestamp)}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
          {filtered.length > PAGE_SIZE && (
            <div className="flex items-center justify-between border-t border-border px-4 py-3 text-sm">
              <span className="text-muted-foreground">Page {page + 1} / {pageCount} · {filtered.length} entrées</span>
              <div className="flex gap-1">
                <Button variant="outline" size="icon" className="h-8 w-8" disabled={page === 0} onClick={() => setPage((p) => p - 1)} aria-label="Page précédente"><ChevronLeft className="h-4 w-4" /></Button>
                <Button variant="outline" size="icon" className="h-8 w-8" disabled={page >= pageCount - 1} onClick={() => setPage((p) => p + 1)} aria-label="Page suivante"><ChevronRight className="h-4 w-4" /></Button>
              </div>
            </div>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
