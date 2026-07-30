'use client';

import { create } from 'zustand';
import { persist } from 'zustand/middleware';
import type { AuthSession, User } from '@/types';
import { login as apiLogin, refreshSession as apiRefresh, logout as apiLogout } from '@/lib/api/client';
import { setAuthHooks } from '@/lib/api/http';

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
      logout: () => {
        const s = get().session;
        set({ session: null });
        // Révocation best-effort côté serveur (liste noire Redis du refresh
        // token) — ne bloque jamais la déconnexion locale si l'appel échoue
        // (token déjà expiré, backend injoignable, etc.).
        if (s?.refreshToken) {
          apiLogout(s.refreshToken).catch(() => {});
        }
      },
      // Rafraîchissement silencieux — appelé automatiquement par
      // l'intercepteur 401 (lib/api/http.ts), plus seulement disponible
      // manuellement. Utilise le refresh token, pas l'id utilisateur (le
      // backend n'a aucun moyen de rafraîchir à partir du seul id) — écart
      // 6.5 du rapport.
      refresh: async () => {
        const s = get().session;
        if (!s) return;
        const res = await apiRefresh(s.refreshToken);
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

// Branche l'intercepteur Axios (lib/api/http.ts) sur ce store, une fois qu'il
// est initialisé — voir le commentaire dans http.ts sur pourquoi ce n'est pas
// un import direct.
setAuthHooks({
  getAccessToken: () => useAuth.getState().session?.accessToken,
  refresh: () => useAuth.getState().refresh(),
  logout: () => useAuth.getState().logout(),
});
