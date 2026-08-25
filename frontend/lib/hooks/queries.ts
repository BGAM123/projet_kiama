'use client';

import { useQuery } from '@tanstack/react-query';
import {
  getDashboardStats,
  listConversations,
  listDocumentTypes,
  listCategories,
  getDocument,
  listDocuments,
  getStructure,
  listUsers,
  listRoles,
  listAiConfigs,
  listAuditLogs,
  listNotifications,
  listMessages,
  listReferenceDocuments,
  listDocumentReferences,
} from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import type {
  AiModelConfig,
  AppDocument,
  AuditLogEntry,
  Category,
  Conversation,
  DashboardStats,
  DocumentStructure,
  DocumentType,
  Message,
  Notification,
  ReferenceDocument,
  Role,
  User,
} from '@/types';

export function useDashboardStats() {
  const session = useAuth((s) => s.session);
  return useQuery<DashboardStats>({
    queryKey: ['dashboard-stats', session?.user.id],
    queryFn: async () => (await getDashboardStats(session!.user.id)).data as DashboardStats,
    enabled: !!session,
  });
}

export function useConversations() {
  const session = useAuth((s) => s.session);
  return useQuery<Conversation[]>({
    queryKey: ['conversations', session?.user.id],
    queryFn: async () => (await listConversations(session!.user.id)).data as Conversation[],
    enabled: !!session,
  });
}

export function useDocumentTypes() {
  return useQuery<DocumentType[]>({
    queryKey: ['document-types'],
    queryFn: async () => (await listDocumentTypes()).data as DocumentType[],
  });
}

export function useCategories() {
  return useQuery<Category[]>({
    queryKey: ['categories'],
    queryFn: async () => (await listCategories()).data as Category[],
  });
}

export function useDocument(id: string | null) {
  return useQuery<AppDocument>({
    queryKey: ['document', id],
    queryFn: async () => (await getDocument(id!)).data as AppDocument,
    enabled: !!id,
  });
}

export function useDocuments() {
  const session = useAuth((s) => s.session);
  return useQuery<AppDocument[]>({
    queryKey: ['documents', session?.user.id],
    queryFn: async () => (await listDocuments(session!.user.id)).data as AppDocument[],
    enabled: !!session,
  });
}

export function useStructure(documentTypeId: string | null) {
  return useQuery<DocumentStructure>({
    queryKey: ['structure', documentTypeId],
    queryFn: async () => (await getStructure(documentTypeId!)).data as DocumentStructure,
    enabled: !!documentTypeId,
  });
}

export function useUsers() {
  return useQuery<User[]>({
    queryKey: ['users'],
    queryFn: async () => (await listUsers()).data as User[],
  });
}

export function useRoles() {
  return useQuery<Role[]>({
    queryKey: ['roles'],
    queryFn: async () => (await listRoles()).data as Role[],
  });
}

export function useAiConfigs() {
  return useQuery<AiModelConfig[]>({
    queryKey: ['ai-configs'],
    queryFn: async () => (await listAiConfigs()).data as AiModelConfig[],
  });
}

export function useAuditLogs() {
  return useQuery<AuditLogEntry[]>({
    queryKey: ['audit-logs'],
    queryFn: async () => (await listAuditLogs()).data as AuditLogEntry[],
  });
}

export function useNotifications() {
  const session = useAuth((s) => s.session);
  return useQuery<Notification[]>({
    queryKey: ['notifications', session?.user.id],
    queryFn: async () => (await listNotifications(session!.user.id)).data as Notification[],
    enabled: !!session,
  });
}

export function useMessages(conversationId: string | null) {
  return useQuery<Message[]>({
    queryKey: ['messages', conversationId],
    queryFn: async () => (await listMessages(conversationId!)).data as Message[],
    enabled: !!conversationId,
  });
}

export function useRefDocuments(conversationId: string | null) {
  return useQuery<ReferenceDocument[]>({
    queryKey: ['ref-docs', conversationId],
    queryFn: async () => (await listReferenceDocuments(conversationId!)).data as ReferenceDocument[],
    enabled: !!conversationId,
  });
}

/** Documents de référence attachés directement à un Document en cours d'édition (enrichissent les prompts "Améliorer avec l'IA"). */
export function useDocumentReferences(documentId: string | null) {
  return useQuery<ReferenceDocument[]>({
    queryKey: ['document-refs', documentId],
    queryFn: async () => (await listDocumentReferences(documentId!)).data as ReferenceDocument[],
    enabled: !!documentId,
  });
}
