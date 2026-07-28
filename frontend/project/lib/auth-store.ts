'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthSession, User } from '@/types';
import { login as apiLogin, refreshSession as apiRefresh } from '@/lib/api/client';

interface AuthState {
  session: AuthSession | null;
  loading: boolean;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<void>;
  hasPermission: (code: string) => boolean;
  hasRole: (role: 'ADMIN' | 'UTILISATEUR') => boolean;
}

export const useAuth = create<AuthState>()(
  persist(
    (set, get) => ({
      session: null,
      loading: false,
      login: async (email, password) => {
        set({ loading: true });
        try {
          const res = await apiLogin({ email, password });
          set({ session: res.data, loading: false });
        } catch (e) {
          set({ loading: false });
          throw e;
        }
      },
      logout: () => set({ session: null }),
      refresh: async () => {
        const s = get().session;
        if (!s) return;
        const res = await apiRefresh(s.user.id);
        set({ session: res.data });
      },
      hasPermission: (code) => {
        const s = get().session;
        if (!s) return false;
        return s.user.roles.some((r) => r.permissions.some((p) => p.code === code));
      },
      hasRole: (role) => {
        const s = get().session;
        if (!s) return false;
        return s.user.roles.some((r) => r.name === role);
      },
    }),
    {
      name: 'docuai-auth',
      partialize: (s) => ({ session: s.session }),
    },
  ),
);

export function currentUser(s: AuthSession | null): User | null {
  return s?.user ?? null;
}
