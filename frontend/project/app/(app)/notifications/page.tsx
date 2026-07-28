'use client';

import { Bell, Check, X } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Skeleton } from '@/components/ui/skeleton';
import { useNotifications } from '@/lib/hooks/queries';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { markNotificationRead } from '@/lib/api/client';
import { relativeTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Notification } from '@/types';

export default function NotificationsPage() {
  const { data: notifications, isLoading } = useNotifications();
  const qc = useQueryClient();

  const mark = useMutation({
    mutationFn: async ({ id, read }: { id: string; read: boolean }) => (await markNotificationRead(id, read)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  const toneByType: Record<Notification['type'], string> = {
    INFO: 'bg-info/15 text-info',
    SUCCESS: 'bg-success/15 text-success',
    WARNING: 'bg-warning/15 text-warning',
    ERROR: 'bg-destructive/15 text-destructive',
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Notifications" description="Vos alertes et messages système." icon={Bell} />
      <Card>
        <CardContent className="p-0">
          {isLoading ? (
            <div className="space-y-3 p-4">{Array.from({ length: 4 }).map((_, i) => <Skeleton key={i} className="h-16 w-full" />)}</div>
          ) : !notifications?.length ? (
            <div className="flex flex-col items-center gap-3 py-16 text-center">
              <Bell className="h-10 w-10 text-muted-foreground/50" />
              <p className="text-sm text-muted-foreground">Aucune notification.</p>
            </div>
          ) : (
            <ul className="divide-y divide-border">
              {notifications.map((n: Notification) => (
                <li key={n.id} className={cn('flex items-start gap-3 p-4', !n.read && 'bg-accent/5')}>
                  <span className={cn('mt-1.5 h-2.5 w-2.5 flex-none rounded-full', toneByType[n.type])} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm leading-snug">{n.content}</p>
                    <p className="mt-1 text-xs text-muted-foreground">{relativeTime(n.createdAt)}</p>
                  </div>
                  <Button variant="ghost" size="icon" className="h-7 w-7 flex-none" aria-label={n.read ? 'Marquer non lue' : 'Marquer lue'} onClick={() => mark.mutate({ id: n.id, read: !n.read })}>
                    {n.read ? <X className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </CardContent>
      </Card>
    </div>
  );
}
