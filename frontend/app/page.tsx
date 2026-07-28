'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-store';

export default function RootRedirect() {
  const router = useRouter();
  const session = useAuth((s) => s.session);
  const hydrated = useAuth((s) => s.session !== undefined);

  useEffect(() => {
    if (!hydrated) return;
    router.replace(session ? '/dashboard' : '/login');
  }, [hydrated, session, router]);

  return null;
}
