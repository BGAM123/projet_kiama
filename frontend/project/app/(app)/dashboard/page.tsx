'use client';

import Link from 'next/link';
import { useQuery } from '@tanstack/react-query';
import {
  Bar,
  BarChart,
  CartesianGrid,
  Cell,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import {
  ArrowRight,
  Bot,
  Clock,
  FileText,
  FolderTree,
  Plus,
  TrendingUp,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { Badge } from '@/components/ui/badge';
import { useDashboardStats, useConversations, useDocumentTypes, useCategories } from '@/lib/hooks/queries';
import { useAuth } from '@/lib/auth-store';
import { generatedStatuslabel, generatedStatusTone, relativeTime } from '@/lib/format';
import type { GeneratedDocument, Category, DocumentType } from '@/types';

const PIE_COLORS = ['hsl(var(--chart-1))', 'hsl(var(--chart-2))', 'hsl(var(--chart-3))', 'hsl(var(--chart-4))', 'hsl(var(--chart-5))'];

export default function DashboardPage() {
  const session = useAuth((s) => s.session);
  const { data: stats, isLoading: statsLoading } = useDashboardStats();
  const { data: conversations } = useConversations();
  const { data: categories } = useCategories();
  const { data: documentTypes } = useDocumentTypes();

  // Recent generated docs: derive from conversations via the fixtures (mocked).
  const { data: recentDocs } = useQuery<GeneratedDocument[]>({
    queryKey: ['recent-generations', session?.user.id],
    queryFn: async () => {
      const { generatedDocuments } = await import('@/lib/api/fixtures');
      return generatedDocuments
        .filter((g) => g.userId === session!.user.id)
        .sort((a, b) => b.updatedAt.localeCompare(a.updatedAt))
        .slice(0, 5);
    },
    enabled: !!session,
  });

  const kpis = [
    {
      label: 'Documents ce mois',
      value: stats?.documentsThisMonth ?? 0,
      icon: FileText,
      tone: 'text-primary',
      hint: 'Générations produites ce mois-ci',
    },
    {
      label: 'Temps moyen',
      value: stats ? `${stats.averageGenerationTimeSec}s` : '—',
      icon: Clock,
      tone: 'text-info',
      hint: 'Durée moyenne de génération',
    },
    {
      label: 'Documents Types actifs',
      value: stats?.activeDocumentTypes ?? 0,
      icon: FolderTree,
      tone: 'text-success',
      hint: 'Structures prêtes à l\'emploi',
    },
    {
      label: 'Taux de réussite',
      value: stats ? `${stats.successRate}%` : '—',
      icon: TrendingUp,
      tone: 'text-accent',
      hint: 'Générations abouties',
    },
  ];

  const catName = (id: string) => categories?.find((c: Category) => c.id === id)?.name ?? '—';
  const docTypeName = (id: string) => documentTypes?.find((d: DocumentType) => d.id === id)?.name ?? 'Document';

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Tableau de bord"
        description="Vue d'ensemble de votre activité de génération documentaire."
        icon={Bot}
        actions={
          <Button asChild className="bg-accent text-accent-foreground hover:bg-accent/90">
            <Link href="/chat"><Plus className="mr-2 h-4 w-4" /> Nouvelle conversation</Link>
          </Button>
        }
      />

      {/* KPI cards */}
      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        {kpis.map((k) => {
          const Icon = k.icon;
          return (
            <Card key={k.label} className="animate-fade-in-up">
              <CardContent className="flex items-center gap-4 p-5">
                <div className={`flex h-12 w-12 flex-none items-center justify-center rounded-lg bg-muted ${k.tone}`}>
                  <Icon className="h-6 w-6" />
                </div>
                <div className="min-w-0">
                  {statsLoading ? (
                    <Skeleton className="h-8 w-20" />
                  ) : (
                    <p className="text-3xl font-semibold leading-none tracking-tight">{k.value}</p>
                  )}
                  <p className="mt-1.5 truncate text-sm text-muted-foreground">{k.label}</p>
                </div>
              </CardContent>
            </Card>
          );
        })}
      </div>

      <div className="grid gap-6 lg:grid-cols-5">
        {/* 7-day activity */}
        <Card className="lg:col-span-3">
          <CardHeader>
            <CardTitle className="text-lg">Activité des 7 derniers jours</CardTitle>
            <CardDescription>Nombre de documents générés par jour.</CardDescription>
          </CardHeader>
          <CardContent>
            {statsLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : (
              <ResponsiveContainer width="100%" height={260}>
                <BarChart data={stats?.last7Days ?? []} margin={{ top: 8, right: 8, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="hsl(var(--border))" vertical={false} />
                  <XAxis
                    dataKey="date"
                    tickFormatter={(d) => new Date(d).toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit' })}
                    tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }}
                    axisLine={false}
                    tickLine={false}
                  />
                  <YAxis allowDecimals={false} tick={{ fontSize: 12, fill: 'hsl(var(--muted-foreground))' }} axisLine={false} tickLine={false} />
                  <Tooltip
                    contentStyle={{ background: 'hsl(var(--popover))', border: '1px solid hsl(var(--border))', borderRadius: 8, fontSize: 12 }}
                    labelFormatter={(d) => new Date(d as string).toLocaleDateString('fr-FR', { weekday: 'long', day: '2-digit', month: 'long' })}
                  />
                  <Bar dataKey="count" name="Documents" fill="hsl(var(--chart-1))" radius={[6, 6, 0, 0]} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </CardContent>
        </Card>

        {/* Category repartition */}
        <Card className="lg:col-span-2">
          <CardHeader>
            <CardTitle className="text-lg">Par catégorie</CardTitle>
            <CardDescription>Répartition de vos documents.</CardDescription>
          </CardHeader>
          <CardContent>
            {statsLoading ? (
              <Skeleton className="h-64 w-full" />
            ) : (stats?.byCategory?.length ?? 0) === 0 ? (
              <p className="py-16 text-center text-sm text-muted-foreground">Pas encore de données.</p>
            ) : (
              <div className="flex flex-col items-center gap-4 sm:flex-row">
                <ResponsiveContainer width="50%" height={200}>
                  <PieChart>
                    <Pie data={stats?.byCategory ?? []} dataKey="count" nameKey="categoryName" innerRadius={45} outerRadius={80} paddingAngle={3}>
                      {(stats?.byCategory ?? []).map((_: { categoryId: string; categoryName: string; count: number }, i: number) => (
                        <Cell key={i} fill={PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip contentStyle={{ background: 'hsl(var(--popover))', border: '1px solid hsl(var(--border))', borderRadius: 8, fontSize: 12 }} />
                  </PieChart>
                </ResponsiveContainer>
                <ul className="flex-1 space-y-2 text-sm">
                  {(stats?.byCategory ?? []).map((c: { categoryId: string; categoryName: string; count: number }, i: number) => (
                    <li key={c.categoryId} className="flex items-center justify-between">
                      <span className="flex items-center gap-2">
                        <span className="h-3 w-3 rounded-sm" style={{ background: PIE_COLORS[i % PIE_COLORS.length] }} />
                        {c.categoryName}
                      </span>
                      <span className="font-medium">{c.count}</span>
                    </li>
                  ))}
                </ul>
              </div>
            )}
          </CardContent>
        </Card>
      </div>

      {/* Recent documents */}
      <Card>
        <CardHeader className="flex-row items-center justify-between space-y-0">
          <div>
            <CardTitle className="text-lg">Documents récents</CardTitle>
            <CardDescription>Vos dernières générations.</CardDescription>
          </div>
          <Button asChild variant="ghost" size="sm">
            <Link href="/history">Tout voir <ArrowRight className="ml-1 h-4 w-4" /></Link>
          </Button>
        </CardHeader>
        <CardContent>
          {!recentDocs ? (
            <div className="space-y-3">
              {Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-14 w-full" />)}
            </div>
          ) : recentDocs.length === 0 ? (
            <div className="flex flex-col items-center gap-3 py-12 text-center">
              <FileText className="h-10 w-10 text-muted-foreground/50" />
              <p className="text-sm text-muted-foreground">Aucun document généré pour le moment.</p>
              <Button asChild className="bg-accent text-accent-foreground hover:bg-accent/90">
                <Link href="/chat"><Plus className="mr-2 h-4 w-4" /> Démarrer une conversation</Link>
              </Button>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {recentDocs.map((g: GeneratedDocument) => (
                <li key={g.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                  <div className="flex min-w-0 items-center gap-3">
                    <div className="flex h-9 w-9 flex-none items-center justify-center rounded-md bg-muted text-primary">
                      <FileText className="h-4 w-4" />
                    </div>
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{g.contentPivot}</p>
                      <p className="truncate text-xs text-muted-foreground">{docTypeName(g.documentTypeId)} · {relativeTime(g.updatedAt)}</p>
                    </div>
                  </div>
                  <Badge variant="outline" className={`flex-none ${generatedStatusTone[g.status]}`}>
                    {generatedStatuslabel[g.status]}
                  </Badge>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
