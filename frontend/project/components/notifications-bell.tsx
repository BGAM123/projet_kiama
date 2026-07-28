'use client';

import { useEffect, useRef, useState } from 'react';
import { Bell, Check, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  listNotifications,
  markNotificationRead,
} from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import { relativeTime } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { Notification } from '@/types';

export function NotificationsBell() {
  const session = useAuth((s) => s.session);
  const qc = useQueryClient();
  const [open, setOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  const { data } = useQuery({
    queryKey: ['notifications', session?.user.id],
    queryFn: async () => (await listNotifications(session!.user.id)).data as Notification[],
    enabled: !!session,
  });
  const notifications: Notification[] = data ?? [];

  const unread = notifications.filter((n) => !n.read).length;

  const mark = useMutation({
    mutationFn: async ({ id, read }: { id: string; read: boolean }) =>
      (await markNotificationRead(id, read)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notifications'] }),
  });

  useEffect(() => {
    function onClick(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', onClick);
    return () => document.removeEventListener('mousedown', onClick);
  }, []);

  if (!session) return null;

  const toneByType: Record<Notification['type'], string> = {
    INFO: 'bg-info/15 text-info',
    SUCCESS: 'bg-success/15 text-success',
    WARNING: 'bg-warning/15 text-warning',
    ERROR: 'bg-destructive/15 text-destructive',
  };

  return (
    <div className="relative" ref={ref}>
      <Button variant="ghost" size="icon" aria-label={`Notifications${unread ? ` (${unread} non lues)` : ''}`} onClick={() => setOpen((o) => !o)}>
        <div className="relative">
          <Bell className="h-5 w-5" />
          {unread > 0 && (
            <span className="absolute -right-1 -top-1 flex h-4 min-w-4 items-center justify-center rounded-full bg-accent px-1 text-[10px] font-bold text-accent-foreground">
              {unread}
            </span>
          )}
        </div>
      </Button>
      {open && (
        <div className="absolute right-0 z-50 mt-2 w-80 origin-top-right animate-fade-in-up rounded-lg border border-border bg-popover p-1 shadow-lg">
          <div className="flex items-center justify-between px-3 py-2">
            <span className="text-sm font-semibold">Notifications</span>
            {unread > 0 && <span className="text-xs text-muted-foreground">{unread} non lue(s)</span>}
          </div>
          <div className="max-h-80 overflow-y-auto scrollbar-thin">
            {notifications.length === 0 ? (
              <p className="px-3 py-6 text-center text-sm text-muted-foreground">Aucune notification.</p>
            ) : (
              notifications.map((n) => (
                <div key={n.id} className={cn('flex gap-2 rounded-md px-3 py-2 text-sm hover:bg-muted/60', !n.read && 'bg-accent/5')}>
                  <span className={cn('mt-1.5 h-2 w-2 flex-none rounded-full', toneByType[n.type])} />
                  <div className="min-w-0 flex-1">
                    <p className="text-sm leading-snug">{n.content}</p>
                    <p className="mt-0.5 text-xs text-muted-foreground">{relativeTime(n.createdAt)}</p>
                  </div>
                  <button
                    aria-label={n.read ? 'Marquer comme non lue' : 'Marquer comme lue'}
                    className="flex-none self-center text-muted-foreground hover:text-foreground"
                    onClick={() => mark.mutate({ id: n.id, read: !n.read })}
                  >
                    {n.read ? <X className="h-3.5 w-3.5" /> : <Check className="h-3.5 w-3.5" />}
                  </button>
                </div>
              ))
            )}
          </div>
        </div>
      )}
    </div>
  );
}
