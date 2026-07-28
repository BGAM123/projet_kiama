// Mock API layer.
// Each function mirrors a real REST endpoint (same path, same verb, same shape)
// but resolves against in-memory fixtures with simulated latency.
// To switch to a real backend: replace the body of these functions with Axios
// calls to the same paths; the signatures and return envelopes stay identical.

import {
  aiModelConfigs,
  auditLogs,
  categories,
  conversations,
  documentTypes,
  generatedDocuments,
  messages,
  notifications,
  referenceDocuments,
  roles,
  structures,
  users,
  userPasswords,
} from './fixtures';
import type {
  AiModelConfig,
  AuditLogEntry,
  AuthSession,
  Category,
  Conversation,
  DocumentStructure,
  DocumentType,
  GeneratedDocument,
  LoginPayload,
  Message,
  Notification,
  ReferenceDocument,
  User,
} from '@/types';
import type { ApiSuccess } from '@/types';

const LATENCY_MIN = 280;
const LATENCY_MAX = 750;

function delay(): Promise<void> {
  const ms = LATENCY_MIN + Math.random() * (LATENCY_MAX - LATENCY_MIN);
  return new Promise((r) => setTimeout(r, ms));
}

function ok<T>(data: T, meta?: ApiSuccess<T>['meta']): ApiSuccess<T> {
  return { data, error: null, meta };
}

export class ApiError extends Error {
  code: string;
  path: string;
  constructor(code: string, message: string, path: string) {
    super(message);
    this.code = code;
    this.path = path;
  }
}

async function guard<T>(fn: () => T | Promise<T>): Promise<ApiSuccess<T>> {
  await delay();
  return ok(await fn());
}

const uid = (p: string) => `${p}${Math.random().toString(36).slice(2, 9)}`;

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

export async function login(payload: LoginPayload): Promise<ApiSuccess<AuthSession>> {
  return guard(() => {
    const user = users.find((u) => u.email === payload.email);
    const expected = user ? (userPasswords[user.id] ?? 'demo1234') : null;
    if (!user || !expected || payload.password !== expected) {
      throw new ApiError(
        'AUTH_INVALID_CREDENTIALS',
        'Adresse e-mail ou mot de passe incorrect.',
        '/api/v1/auth/login',
      );
    }
    if (!user.active) {
      throw new ApiError(
        'AUTH_ACCOUNT_DISABLED',
        'Ce compte est désactivé. Contactez un administrateur.',
        '/api/v1/auth/login',
      );
    }
    return {
      accessToken: `mock-access-${user.id}-${Date.now()}`,
      refreshToken: `mock-refresh-${user.id}`,
      user,
    };
  });
}

export async function refreshSession(userId: string): Promise<ApiSuccess<AuthSession>> {
  return guard(() => {
    const user = users.find((u) => u.id === userId);
    if (!user) throw new ApiError('AUTH_SESSION_EXPIRED', 'Session expirée.', '/api/v1/auth/refresh');
    return {
      accessToken: `mock-access-${user.id}-${Date.now()}`,
      refreshToken: `mock-refresh-${user.id}`,
      user,
    };
  });
}

// ---------------------------------------------------------------------------
// Users
// ---------------------------------------------------------------------------

export async function listUsers(): Promise<ApiSuccess<User[]>> {
  return guard(() => [...users]);
}

export async function createUser(input: Omit<User, 'id' | 'createdAt'> & { password?: string }): Promise<ApiSuccess<User>> {
  return guard(() => {
    const id = uid('u');
    const password = input.password || generatePassword();
    const newUser: User = {
      id,
      email: input.email,
      firstName: input.firstName,
      lastName: input.lastName,
      active: input.active,
      roles: input.roles,
      createdAt: new Date().toISOString(),
    };
    users.push(newUser);
    userPasswords[id] = password;
    return newUser;
  });
}

export async function updateUser(id: string, patch: Partial<User>): Promise<ApiSuccess<User>> {
  return guard(() => {
    const idx = users.findIndex((u) => u.id === id);
    if (idx < 0) throw new ApiError('USER_NOT_FOUND', 'Utilisateur introuvable.', `/api/v1/users/${id}`);
    users[idx] = { ...users[idx], ...patch, id };
    return users[idx];
  });
}

export async function deleteUser(id: string): Promise<ApiSuccess<{ id: string }>> {
  return guard(() => {
    const idx = users.findIndex((u) => u.id === id);
    if (idx < 0) throw new ApiError('USER_NOT_FOUND', 'Utilisateur introuvable.', `/api/v1/users/${id}`);
    users.splice(idx, 1);
    delete userPasswords[id];
    return { id };
  });
}

// ---------------------------------------------------------------------------
// Password management
// ---------------------------------------------------------------------------

function generatePassword(): string {
  const lower = 'abcdefghijkmnpqrstuvwxyz';
  const upper = 'ABCDEFGHJKLMNPQRSTUVWXYZ';
  const digits = '23456789';
  const all = lower + upper + digits;
  const pick = (set: string) => set[Math.floor(Math.random() * set.length)];
  let pw = pick(upper) + pick(lower) + pick(digits);
  while (pw.length < 10) pw += pick(all);
  return pw;
}

export async function changePassword(userId: string, current: string, next: string): Promise<ApiSuccess<{ ok: true }>> {
  return guard(() => {
    const stored = userPasswords[userId];
    if (!stored || stored !== current) {
      throw new ApiError('AUTH_INVALID_CREDENTIALS', 'Le mot de passe actuel est incorrect.', '/api/v1/auth/password');
    }
    if (next.length < 8) {
      throw new ApiError('AUTH_PASSWORD_TOO_SHORT', 'Le nouveau mot de passe doit contenir au moins 8 caractères.', '/api/v1/auth/password');
    }
    userPasswords[userId] = next;
    return { ok: true } as const;
  });
}

export async function adminResetPassword(userId: string, next: string): Promise<ApiSuccess<{ ok: true }>> {
  return guard(() => {
    const user = users.find((u) => u.id === userId);
    if (!user) throw new ApiError('USER_NOT_FOUND', 'Utilisateur introuvable.', `/api/v1/users/${userId}/password`);
    userPasswords[userId] = next;
    return { ok: true } as const;
  });
}

// ---------------------------------------------------------------------------
// Categories
// ---------------------------------------------------------------------------

export async function listCategories(): Promise<ApiSuccess<Category[]>> {
  return guard(() => [...categories]);
}

export async function createCategory(input: Omit<Category, 'id'>): Promise<ApiSuccess<Category>> {
  return guard(() => {
    const c: Category = { id: uid('c'), ...input };
    categories.push(c);
    return c;
  });
}

export async function updateCategory(id: string, patch: Partial<Category>): Promise<ApiSuccess<Category>> {
  return guard(() => {
    const idx = categories.findIndex((c) => c.id === id);
    if (idx < 0) throw new ApiError('CATEGORY_NOT_FOUND', 'Catégorie introuvable.', `/api/v1/categories/${id}`);
    categories[idx] = { ...categories[idx], ...patch, id };
    return categories[idx];
  });
}

export async function deleteCategory(id: string): Promise<ApiSuccess<{ id: string }>> {
  return guard(() => {
    const idx = categories.findIndex((c) => c.id === id);
    if (idx < 0) throw new ApiError('CATEGORY_NOT_FOUND', 'Catégorie introuvable.', `/api/v1/categories/${id}`);
    categories.splice(idx, 1);
    return { id };
  });
}

// ---------------------------------------------------------------------------
// Document types & structures
// ---------------------------------------------------------------------------

export async function listDocumentTypes(): Promise<ApiSuccess<DocumentType[]>> {
  return guard(() => [...documentTypes]);
}

export async function getDocumentType(id: string): Promise<ApiSuccess<DocumentType>> {
  return guard(() => {
    const dt = documentTypes.find((d) => d.id === id);
    if (!dt) throw new ApiError('DOCUMENT_TYPE_NOT_FOUND', 'Le Document Type demandé est introuvable.', `/api/v1/document-types/${id}`);
    return dt;
  });
}

export async function updateDocumentType(id: string, patch: Partial<DocumentType>): Promise<ApiSuccess<DocumentType>> {
  return guard(() => {
    const idx = documentTypes.findIndex((d) => d.id === id);
    if (idx < 0) throw new ApiError('DOCUMENT_TYPE_NOT_FOUND', 'Document Type introuvable.', `/api/v1/document-types/${id}`);
    documentTypes[idx] = { ...documentTypes[idx], ...patch, id };
    return documentTypes[idx];
  });
}

export async function deleteDocumentType(id: string): Promise<ApiSuccess<{ id: string }>> {
  return guard(() => {
    const idx = documentTypes.findIndex((d) => d.id === id);
    if (idx < 0) throw new ApiError('DOCUMENT_TYPE_NOT_FOUND', 'Document Type introuvable.', `/api/v1/document-types/${id}`);
    documentTypes[idx].status = 'ARCHIVE';
    return { id };
  });
}

export async function importDocumentType(input: { fileName: string; categoryId: string; name: string }): Promise<ApiSuccess<DocumentType>> {
  return guard(() => {
    const dt: DocumentType = {
      id: uid('dt'),
      name: input.name,
      description: `Document Type importé depuis ${input.fileName}.`,
      categoryId: input.categoryId,
      status: 'EN_EXTRACTION',
      version: 1,
      createdAt: new Date().toISOString(),
    };
    documentTypes.push(dt);
    return dt;
  });
}

export async function getStructure(documentTypeId: string): Promise<ApiSuccess<DocumentStructure>> {
  return guard(() => {
    const s = structures.find((s) => s.documentTypeId === documentTypeId);
    if (!s) throw new ApiError('STRUCTURE_NOT_FOUND', 'Aucune structure trouvée pour ce Document Type.', `/api/v1/document-types/${documentTypeId}/structure`);
    return s;
  });
}

export async function updateStructure(documentTypeId: string, tree: DocumentStructure['tree']): Promise<ApiSuccess<DocumentStructure>> {
  return guard(() => {
    const idx = structures.findIndex((s) => s.documentTypeId === documentTypeId);
    if (idx < 0) throw new ApiError('STRUCTURE_NOT_FOUND', 'Structure introuvable.', `/api/v1/document-types/${documentTypeId}/structure`);
    structures[idx] = { ...structures[idx], tree };
    return structures[idx];
  });
}

// ---------------------------------------------------------------------------
// Conversations & messages
// ---------------------------------------------------------------------------

export async function listConversations(userId: string): Promise<ApiSuccess<Conversation[]>> {
  return guard(() => conversations.filter((c) => c.userId === userId));
}

export async function createConversation(input: { userId: string; documentTypeId: string; title: string }): Promise<ApiSuccess<Conversation>> {
  return guard(() => {
    const c: Conversation = { id: uid('cv'), createdAt: new Date().toISOString(), ...input };
    conversations.push(c);
    return c;
  });
}

export async function listMessages(conversationId: string): Promise<ApiSuccess<Message[]>> {
  return guard(() => messages.filter((m) => m.conversationId === conversationId).sort((a, b) => a.createdAt.localeCompare(b.createdAt)));
}

export async function sendMessage(conversationId: string, content: string): Promise<ApiSuccess<Message>> {
  return guard(() => {
    const m: Message = { id: uid('m'), conversationId, role: 'user', content, createdAt: new Date().toISOString() };
    messages.push(m);
    // Simulated assistant echo (real generation is launched separately)
    const reply: Message = { id: uid('m'), conversationId, role: 'assistant', content: "C'est noté. Ajustez les paramètres à droite puis cliquez sur « Générer le document ».", createdAt: new Date().toISOString() };
    messages.push(reply);
    return m;
  });
}

// ---------------------------------------------------------------------------
// Reference documents
// ---------------------------------------------------------------------------

export async function addReferenceDocument(conversationId: string, fileName: string): Promise<ApiSuccess<ReferenceDocument>> {
  return guard(() => {
    const rd: ReferenceDocument = { id: uid('rd'), conversationId, fileName, storagePath: `/refs/${conversationId}/${fileName}`, importedAt: new Date().toISOString() };
    referenceDocuments.push(rd);
    return rd;
  });
}

export async function listReferenceDocuments(conversationId: string): Promise<ApiSuccess<ReferenceDocument[]>> {
  return guard(() => referenceDocuments.filter((r) => r.conversationId === conversationId));
}

// ---------------------------------------------------------------------------
// Generations
// ---------------------------------------------------------------------------

export async function startGeneration(input: {
  conversationId: string;
  documentTypeId: string;
  userId: string;
  language: GeneratedDocument['language'];
  tone: GeneratedDocument['tone'];
  targetLength: GeneratedDocument['targetLength'];
  contentPivot: string;
}): Promise<ApiSuccess<GeneratedDocument>> {
  return guard(() => {
    const struct = structures.find((s) => s.documentTypeId === input.documentTypeId);
    const sections = (struct?.tree ?? []).filter((n) => n.type !== 'cover').map((n) => ({
      id: uid('sec'),
      label: n.label,
      status: 'PENDING' as const,
      content: '',
    }));
    const gd: GeneratedDocument = {
      id: uid('gd'),
      conversationId: input.conversationId,
      documentTypeId: input.documentTypeId,
      userId: input.userId,
      status: 'EN_GENERATION',
      language: input.language,
      tone: input.tone,
      targetLength: input.targetLength,
      contentPivot: input.contentPivot,
      content: '',
      sections,
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    };
    generatedDocuments.push(gd);
    return gd;
  });
}

export async function getGeneration(id: string): Promise<ApiSuccess<GeneratedDocument>> {
  return guard(() => {
    const gd = generatedDocuments.find((g) => g.id === id);
    if (!gd) throw new ApiError('GENERATION_NOT_FOUND', 'Génération introuvable.', `/api/v1/generations/${id}`);
    return gd;
  });
}

export async function updateGeneration(id: string, patch: Partial<GeneratedDocument>): Promise<ApiSuccess<GeneratedDocument>> {
  return guard(() => {
    const idx = generatedDocuments.findIndex((g) => g.id === id);
    if (idx < 0) throw new ApiError('GENERATION_NOT_FOUND', 'Génération introuvable.', `/api/v1/generations/${id}`);
    generatedDocuments[idx] = { ...generatedDocuments[idx], ...patch, id, updatedAt: new Date().toISOString() };
    return generatedDocuments[idx];
  });
}

// ---------------------------------------------------------------------------
// AI configs
// ---------------------------------------------------------------------------

export async function listAiConfigs(): Promise<ApiSuccess<AiModelConfig[]>> {
  return guard(() => [...aiModelConfigs]);
}

export async function updateAiConfig(id: string, patch: Partial<AiModelConfig>): Promise<ApiSuccess<AiModelConfig>> {
  return guard(() => {
    const idx = aiModelConfigs.findIndex((a) => a.id === id);
    if (idx < 0) throw new ApiError('AI_CONFIG_NOT_FOUND', 'Configuration IA introuvable.', `/api/v1/ai-configs/${id}`);
    if (patch.isDefault) {
      aiModelConfigs.forEach((a) => (a.isDefault = false));
    }
    aiModelConfigs[idx] = { ...aiModelConfigs[idx], ...patch, id };
    return aiModelConfigs[idx];
  });
}

// ---------------------------------------------------------------------------
// Dashboard stats
// ---------------------------------------------------------------------------

export async function getDashboardStats(userId: string): Promise<ApiSuccess<import('@/types').DashboardStats>> {
  return guard(() => {
    const userDocs = generatedDocuments.filter((g) => g.userId === userId);
    const docsThisMonth = userDocs.filter((g) => new Date(g.createdAt).getMonth() === new Date().getMonth()).length;
    const successCount = userDocs.filter((g) => !['ECHEC'].includes(g.status)).length;
    const byCategoryMap = new Map<string, number>();
    userDocs.forEach((g) => {
      const dt = documentTypes.find((d) => d.id === g.documentTypeId);
      const cat = categories.find((c) => c.id === dt?.categoryId);
      const key = cat?.id ?? 'none';
      byCategoryMap.set(key, (byCategoryMap.get(key) ?? 0) + 1);
    });
    const byCategory = Array.from(byCategoryMap.entries()).map(([categoryId, count]) => {
      const cat = categories.find((c) => c.id === categoryId);
      return { categoryId, categoryName: cat?.name ?? 'Autres', count };
    });
    const last7Days = Array.from({ length: 7 }).map((_, i) => {
      const d = new Date();
      d.setDate(d.getDate() - (6 - i));
      const iso = d.toISOString().slice(0, 10);
      const count = userDocs.filter((g) => g.createdAt.slice(0, 10) === iso).length;
      return { date: iso, count };
    });
    return {
      documentsThisMonth: docsThisMonth,
      averageGenerationTimeSec: 47,
      activeDocumentTypes: documentTypes.filter((d) => d.status === 'ACTIF').length,
      successRate: userDocs.length ? Math.round((successCount / userDocs.length) * 100) : 100,
      byCategory,
      last7Days,
    };
  });
}

// ---------------------------------------------------------------------------
// Audit logs & notifications
// ---------------------------------------------------------------------------

export async function listAuditLogs(): Promise<ApiSuccess<AuditLogEntry[]>> {
  return guard(() => [...auditLogs].sort((a, b) => b.timestamp.localeCompare(a.timestamp)));
}

export async function listNotifications(userId: string): Promise<ApiSuccess<Notification[]>> {
  return guard(() => notifications.filter((n) => n.userId === userId).sort((a, b) => b.createdAt.localeCompare(a.createdAt)));
}

export async function markNotificationRead(id: string, read: boolean): Promise<ApiSuccess<Notification>> {
  return guard(() => {
    const idx = notifications.findIndex((n) => n.id === id);
    if (idx < 0) throw new ApiError('NOTIFICATION_NOT_FOUND', 'Notification introuvable.', `/api/v1/notifications/${id}`);
    notifications[idx].read = read;
    return notifications[idx];
  });
}
