'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/lib/auth-store';

interface GuardProps {
  children: React.ReactNode;
  requireRole?: 'ADMIN' | 'UTILISATEUR';
}

// Client-side route guard. Redirects to /login when unauthenticated, and to
// /access-denied when the session lacks the required role.
export function Guard({ children, requireRole }: GuardProps) {
  const router = useRouter();
  const session = useAuth((s) => s.session);
  const hasRole = useAuth((s) => s.hasRole);

  useEffect(() => {
    // persist rehydrates synchronously; if still null after mount, redirect.
    if (!session) {
      router.replace('/login');
      return;
    }
    if (requireRole && !hasRole(requireRole)) {
      router.replace('/access-denied');
    }
  }, [session, requireRole, hasRole, router]);

  if (!session) return null;
  if (requireRole && !hasRole(requireRole)) return null;
  return <>{children}</>;
}
