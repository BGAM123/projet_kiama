'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { QueryProvider } from '@/components/query-provider';
import { AppShell } from '@/components/app-shell';
import { Guard } from '@/components/guard';
import { useAuth } from '@/lib/auth-store';

export default function AppLayout({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const session = useAuth((s) => s.session);
  const hydrated = useAuth((s) => s.session !== undefined);

  useEffect(() => {
    // Zustand persist rehydrates lazily; once we know there's no session, send to login.
    if (hydrated && !session && typeof window !== 'undefined') {
      router.replace('/login');
    }
  }, [hydrated, session, router]);

  return (
    <QueryProvider>
      <Guard>
        <AppShell>{children}</AppShell>
      </Guard>
    </QueryProvider>
  );
}
