// Client HTTP réel vers le backend Spring Boot. Remplace, domaine par domaine,
// les fonctions mock de lib/api/client.ts (voir commentaire en tête de ce
// fichier). Périmètre connecté à ce stade : auth, users, roles, catégories
// (Bloc 3), document-types + structure (Bloc 3), upload/extraction, export,
// notifications, conversations/messages/documents de référence
// (Bloc 6), générations + streaming SSE (Bloc 6). Le reste (ai-configs,
// dashboard, export réel) reste mocké faute d'endpoints backend (Blocs 7/8).

import axios, { AxiosError, type InternalAxiosRequestConfig } from 'axios';
import type { ApiErrorBody } from '@/types';

export const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL ?? 'http://localhost:8080/api/v1';

export const http = axios.create({
  baseURL: API_BASE_URL,
});

// Indirection volontaire plutôt qu'un import direct de lib/auth-store : ce
// fichier est importé par lib/api/client.ts, lui-même importé par
// lib/auth-store.ts — un import direct de useAuth ici créerait un cycle
// (auth-store → client → http → auth-store). auth-store.ts appelle
// setAuthHooks(...) une fois chargé pour brancher ces callbacks.
interface AuthHooks {
  getAccessToken: () => string | null | undefined;
  refresh: () => Promise<void>;
  logout: () => void;
}

let authHooks: AuthHooks | null = null;

export function setAuthHooks(hooks: AuthHooks): void {
  authHooks = hooks;
}

/**
 * Jeton d'accès courant, pour les rares appels qui ne passent pas par
 * l'instance axios `http` ci-dessus (donc sans l'intercepteur de requête) —
 * aujourd'hui uniquement le flux SSE de génération (lib/api/generator.ts),
 * consommé via `fetch` brut car `EventSource` ne permet pas d'en-tête
 * `Authorization` personnalisé.
 */
export function getCurrentAccessToken(): string | null {
  return authHooks?.getAccessToken() ?? null;
}

http.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = authHooks?.getAccessToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

// Coalesce les refresh concurrents : si plusieurs requêtes échouent en 401 en
// même temps, une seule tentative de refresh est faite, les autres attendent
// son résultat au lieu de déclencher chacune leur propre /auth/refresh.
let refreshInFlight: Promise<string | null> | null = null;

function refreshAccessToken(): Promise<string | null> {
  if (!authHooks) return Promise.resolve(null);
  if (!refreshInFlight) {
    refreshInFlight = authHooks
      .refresh()
      .then(() => authHooks?.getAccessToken() ?? null)
      .catch(() => null)
      .finally(() => {
        refreshInFlight = null;
      });
  }
  return refreshInFlight;
}

http.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as (InternalAxiosRequestConfig & { _retry?: boolean }) | undefined;
    const isAuthRoute = originalRequest?.url?.includes('/auth/');

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry && !isAuthRoute) {
      originalRequest._retry = true;
      const newToken = await refreshAccessToken();
      if (newToken) {
        originalRequest.headers.set('Authorization', `Bearer ${newToken}`);
        return http(originalRequest);
      }
      // Le refresh silencieux a échoué : session vraiment expirée.
      authHooks?.logout();
      if (typeof window !== 'undefined') {
        window.location.href = '/login';
      }
    }

    return Promise.reject(error);
  },
);

/**
 * Normalise une erreur Axios vers le même type ApiError que la couche mock,
 * pour que les hooks/toasts existants (qui lisent err.code / err.message)
 * continuent de fonctionner sans changement.
 */
export class ApiError extends Error {
  code: string;
  path: string;
  constructor(code: string, message: string, path: string) {
    super(message);
    this.code = code;
    this.path = path;
  }
}

export function toApiError(error: unknown, fallbackPath: string): ApiError {
  if (axios.isAxiosError(error)) {
    const body = error.response?.data as ApiErrorBody | undefined;
    if (body?.error) {
      return new ApiError(body.error.code, body.error.message, body.error.path || fallbackPath);
    }
    return new ApiError('NETWORK_ERROR', error.message, fallbackPath);
  }
  return new ApiError('UNKNOWN_ERROR', error instanceof Error ? error.message : String(error), fallbackPath);
}
