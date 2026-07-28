'use client';

import { useQuery } from '@tanstack/react-query';
import {
  getDashboardStats,
  listConversations,
  listDocumentTypes,
  listCategories,
  getGeneration,
  getStructure,
  listUsers,
  listAiConfigs,
  listAuditLogs,
  listNotifications,
  listMessages,
  listReferenceDocuments,
} from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import type {
  AiModelConfig,
  AuditLogEntry,
  Category,
  Conversation,
  DashboardStats,
  DocumentStructure,
  DocumentType,
  GeneratedDocument,
  Message,
  Notification,
  ReferenceDocument,
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

export function useGeneration(id: string | null) {
  return useQuery<GeneratedDocument>({
    queryKey: ['generation', id],
    queryFn: async () => (await getGeneration(id!)).data as GeneratedDocument,
    enabled: !!id,
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
