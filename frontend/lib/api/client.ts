// Mock API layer — PARTIELLEMENT MIGRÉE vers le vrai backend Spring Boot.
//
// Périmètre réel (via lib/api/http.ts) à ce stade de l'intégration : auth
// (login/refresh/logout/password), users (CRUD complet + reset mot de passe
// admin), roles (lecture), upload/extraction, export, notifications, audit
// logs. Les fonctions encore mockées ci-dessous n'ont pas d'endpoint backend
// correspondant (catégories, document-types CRUD, conversations, générations,
// streaming, ai-configs, dashboard) et continuent de résoudre contre les
// fixtures en mémoire.
//
// Chaque fonction migrée garde exactement la même signature qu'avant, pour
// que les hooks React Query et les composants qui les consomment n'aient pas
// à changer.

import { http, toApiError } from './http';
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
  structures,
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
  Role,
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
// Adaptateurs backend réel — le backend Spring Boot ne renvoie pas exactement
// la même forme que les fixtures mock, cf. rapport d'écarts :
//  - UserDTO backend : { id, firstName, lastName, email, active, createdAt,
//    roles: [{id, name, description}] } — pas de "permissions" imbriquées
//    par rôle (contrairement au type frontend Role.permissions).
//  - JwtResponse backend : roles/permissions sont deux tableaux plats
//    (ex. roles: ["ROLE_ADMIN"], permissions: ["USERS_MANAGE", ...]),
//    alors que le frontend attend des permissions imbriquées par rôle
//    (User.roles[].permissions[]). On reconstruit donc une forme équivalente
//    côté client : chaque rôle nommé porte l'ensemble des permissions de
//    l'utilisateur (suffisant pour hasRole()/hasPermission(), qui ne font que
//    des tests d'existence, jamais un mapping strict rôle → permissions).
// ---------------------------------------------------------------------------

interface BackendPermissionDTO {
  id: string;
  code: string;
  description: string;
}

interface BackendRoleDTO {
  id: string;
  name: string;
  description: string;
  // Absent de la réponse /auth/login (voir adaptJwtResponse ci-dessous), mais
  // présent sur RoleDTO depuis /roles et /users (Bloc 2 du backend).
  permissions?: BackendPermissionDTO[];
}

interface BackendUserDTO {
  id: string;
  firstName: string;
  lastName: string;
  email: string;
  active: boolean;
  createdAt: string;
  roles: BackendRoleDTO[];
}

interface BackendJwtResponse {
  accessToken: string;
  refreshToken: string;
  type: string;
  email: string;
  roles: string[];
  permissions: string[];
  user: BackendUserDTO;
}

function adaptRoleDTO(dto: BackendRoleDTO): Role {
  return {
    id: dto.id,
    name: dto.name,
    description: dto.description,
    permissions: (dto.permissions ?? []).map((p) => ({ id: p.id, code: p.code, description: p.description })),
  };
}

function adaptUserDTO(dto: BackendUserDTO): User {
  return {
    id: dto.id,
    email: dto.email,
    firstName: dto.firstName,
    lastName: dto.lastName,
    active: dto.active,
    createdAt: dto.createdAt,
    roles: (dto.roles ?? []).map(adaptRoleDTO),
  };
}

function adaptJwtResponse(jwt: BackendJwtResponse): AuthSession {
  const permissions = (jwt.permissions ?? []).map((code) => ({ id: code, code, description: '' }));
  const roleNames = (jwt.roles ?? []).map((r) => r.replace(/^ROLE_/, ''));
  const user: User = {
    ...adaptUserDTO(jwt.user),
    roles: roleNames.map((name) => ({ id: name, name, description: '', permissions })),
  };
  return {
    accessToken: jwt.accessToken,
    refreshToken: jwt.refreshToken,
    user,
  };
}

// ---------------------------------------------------------------------------
// Auth — branché sur le vrai backend (POST /auth/login, /auth/refresh,
// /auth/logout). Le token mock (`mock-access-…`) et le refresh jamais
// déclenché automatiquement sont remplacés par de vrais JWT + intercepteur
// 401 (lib/api/http.ts) — écarts 6.1, 6.2, 6.5 du rapport.
// ---------------------------------------------------------------------------

export async function login(payload: LoginPayload): Promise<ApiSuccess<AuthSession>> {
  try {
    const res = await http.post<{ data: BackendJwtResponse }>('/auth/login', payload);
    return ok(adaptJwtResponse(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/auth/login');
  }
}

// Signature conservée en `refreshToken` (et non plus `userId`) — le backend
// n'a aucun moyen de rafraîchir un token à partir du seul id utilisateur, il
// lui faut le refresh token. lib/auth-store.ts a été mis à jour en conséquence.
export async function refreshSession(refreshToken: string): Promise<ApiSuccess<AuthSession>> {
  try {
    const res = await http.post<{ data: BackendJwtResponse }>('/auth/refresh', { refreshToken });
    return ok(adaptJwtResponse(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/auth/refresh');
  }
}

export async function logout(refreshToken: string): Promise<ApiSuccess<{ ok: boolean }>> {
  try {
    const res = await http.post<{ data: { ok: boolean } }>('/auth/logout', { refreshToken });
    return ok(res.data.data);
  } catch (e) {
    // La déconnexion locale doit réussir même si l'appel serveur échoue
    // (token déjà expiré, backend injoignable, etc.) — on ne bloque jamais
    // l'utilisateur sur cet appel.
    throw toApiError(e, '/api/v1/auth/logout');
  }
}

// ---------------------------------------------------------------------------
// Users — branché sur les vrais endpoints /users du Bloc 2 (CRUD complet +
// reset mot de passe admin, cf. UserController).
// ---------------------------------------------------------------------------

export async function listUsers(): Promise<ApiSuccess<User[]>> {
  try {
    const res = await http.get<{ data: BackendUserDTO[] }>('/users');
    return ok(res.data.data.map(adaptUserDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/users');
  }
}

export async function createUser(input: Omit<User, 'id' | 'createdAt'> & { password?: string }): Promise<ApiSuccess<User>> {
  try {
    const res = await http.post<{ data: BackendUserDTO }>('/users', {
      email: input.email,
      firstName: input.firstName,
      lastName: input.lastName,
      active: input.active,
      password: input.password,
      roleIds: input.roles.map((r) => r.id),
    });
    return ok(adaptUserDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/users');
  }
}

export async function updateUser(id: string, patch: Partial<User>): Promise<ApiSuccess<User>> {
  try {
    const res = await http.patch<{ data: BackendUserDTO }>(`/users/${id}`, {
      email: patch.email,
      firstName: patch.firstName,
      lastName: patch.lastName,
      active: patch.active,
      roleIds: patch.roles?.map((r) => r.id),
    });
    return ok(adaptUserDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/users/${id}`);
  }
}

export async function deleteUser(id: string): Promise<ApiSuccess<{ id: string }>> {
  try {
    await http.delete(`/users/${id}`);
    return ok({ id });
  } catch (e) {
    throw toApiError(e, `/api/v1/users/${id}`);
  }
}

// ---------------------------------------------------------------------------
// Roles — lecture réelle (GET /roles), nécessaire pour résoudre les vrais
// UUID de rôle envoyés à createUser/updateUser (les fixtures locales ont des
// id factices 'r1'/'r2' qui ne correspondent à rien côté backend).
// ---------------------------------------------------------------------------

export async function listRoles(): Promise<ApiSuccess<Role[]>> {
  try {
    const res = await http.get<{ data: BackendRoleDTO[] }>('/roles');
    return ok(res.data.data.map(adaptRoleDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/roles');
  }
}

// ---------------------------------------------------------------------------
// Password management — /auth/password (soi-même) et /users/{id}/password
// (reset admin), cf. AuthController / UserController.
// ---------------------------------------------------------------------------

export async function changePassword(userId: string, current: string, next: string): Promise<ApiSuccess<{ ok: true }>> {
  try {
    // userId conservé dans la signature pour compatibilité avec les appelants
    // existants (mon-compte/mot-de-passe) ; le backend identifie l'utilisateur
    // courant via le JWT, ce paramètre n'est pas envoyé.
    await http.post('/auth/password', { currentPassword: current, newPassword: next });
    return ok({ ok: true } as const);
  } catch (e) {
    throw toApiError(e, '/api/v1/auth/password');
  }
}

export async function adminResetPassword(userId: string, next: string): Promise<ApiSuccess<{ ok: true }>> {
  try {
    await http.patch(`/users/${userId}/password`, { newPassword: next });
    return ok({ ok: true } as const);
  } catch (e) {
    throw toApiError(e, `/api/v1/users/${userId}/password`);
  }
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
// Audit logs & notifications — branchés sur le backend réel.
// ---------------------------------------------------------------------------

interface BackendActivityLog {
  id: string;
  userId: string | null;
  action: string;
  typeEntite: string | null;
  idEntite: string | null;
  dateAction: string;
  adresseIp: string | null;
}

function adaptActivityLog(log: BackendActivityLog): AuditLogEntry {
  return {
    id: log.id,
    userId: log.userId ?? '',
    // Le backend ne joint pas le nom de l'utilisateur sur journal_activite —
    // seul l'id est disponible. Amélioration possible côté backend (jointure
    // ou dénormalisation), hors périmètre de cette itération.
    userName: '',
    action: log.action,
    entityType: log.typeEntite ?? '',
    entityId: log.idEntite ?? '',
    timestamp: log.dateAction,
    ipAddress: log.adresseIp ?? '',
  };
}

export async function listAuditLogs(): Promise<ApiSuccess<AuditLogEntry[]>> {
  try {
    // GET /api/v1/admin/logs (pas /api/v1/audit-logs comme prévu côté mock) —
    // renvoie les 10 dernières entrées seulement, pas de pagination serveur
    // pour l'instant (écart 1.11 / 10.1 du rapport).
    const res = await http.get<{ data: BackendActivityLog[] }>('/admin/logs');
    return ok(res.data.data.map(adaptActivityLog));
  } catch (e) {
    throw toApiError(e, '/api/v1/admin/logs');
  }
}

interface BackendNotification {
  id: string;
  userId: string;
  type: string;
  contenu: string;
  lue: boolean;
  dateCreation: string;
}

function adaptNotification(n: BackendNotification): Notification {
  return {
    id: n.id,
    userId: n.userId,
    type: (n.type as Notification['type']) ?? 'INFO',
    content: n.contenu,
    read: n.lue,
    createdAt: n.dateCreation,
  };
}

export async function listNotifications(userId: string): Promise<ApiSuccess<Notification[]>> {
  try {
    // GET /api/v1/notifications/user/{userId} (path param, pas ?userId=) —
    // écart 1.10 du rapport, corrigé côté frontend.
    const res = await http.get<{ data: BackendNotification[] }>(`/notifications/user/${userId}`);
    return ok(res.data.data.map(adaptNotification));
  } catch (e) {
    throw toApiError(e, `/api/v1/notifications/user/${userId}`);
  }
}

export async function markNotificationRead(id: string, read: boolean): Promise<ApiSuccess<{ ok: true }>> {
  if (!read) {
    // Le backend n'expose que PUT /notifications/{id}/read (marque "lue"),
    // pas de "marquer non lue" — fonctionnalité absente côté backend
    // (hors périmètre validé). On échoue explicitement plutôt que de
    // prétendre un succès qui ne serait pas persisté.
    throw new ApiError(
      'UNSUPPORTED_OPERATION',
      'Le backend ne permet pas encore de marquer une notification comme non lue.',
      `/api/v1/notifications/${id}/read`,
    );
  }
  try {
    // PUT (pas PATCH) /api/v1/notifications/{id}/read — écart 1.10.
    await http.put(`/notifications/${id}/read`);
    return ok({ ok: true } as const);
  } catch (e) {
    throw toApiError(e, `/api/v1/notifications/${id}/read`);
  }
}

// ---------------------------------------------------------------------------
// Upload & extraction de fichier — réel (POST /api/v1/documents/upload).
//
// Ce endpoint existe côté backend et fait une vraie extraction Tika + upload
// MinIO, mais il n'y a pas encore de notion de "Document Type" côté serveur
// (écart 1.2/8.4, hors périmètre de cette itération). L'écran d'import
// (documents-types/import/page.tsx) appelle donc CE endpoint pour une
// extraction réelle du texte, puis persiste le Document Type lui-même via
// importDocumentType() (mock) faute de endpoint backend équivalent.
// ---------------------------------------------------------------------------

export interface ExtractedContentDetail {
  fileName: string;
  mimeType: string;
  rawText: string;
  fileUrl: string;
}

export async function uploadAndExtractDocument(file: File): Promise<ApiSuccess<ExtractedContentDetail>> {
  try {
    const form = new FormData();
    // Nom de champ multipart exact attendu par le backend :
    // @RequestParam("file") MultipartFile file (DocumentController.java).
    form.append('file', file);
    // Ne PAS fixer le header Content-Type manuellement : le navigateur doit
    // générer lui-même le boundary multipart. Le forcer à
    // "multipart/form-data" sans boundary casse le parsing côté serveur.
    const res = await http.post<{ data: ExtractedContentDetail }>('/documents/upload', form);
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, '/api/v1/documents/upload');
  }
}

// ---------------------------------------------------------------------------
// Export de document — réel (POST /api/v1/export). Remplace la simulation
// locale (Blob de texte brut renommé en .pdf/.docx) par un vrai fichier
// binaire généré côté serveur (écart 1.13 du rapport).
// ---------------------------------------------------------------------------

export type ExportFormat = 'DOCX' | 'PDF' | 'Markdown';

const EXPORT_FORMAT_MAP: Record<ExportFormat, 'DOCX' | 'PDF' | 'MARKDOWN'> = {
  DOCX: 'DOCX',
  PDF: 'PDF',
  Markdown: 'MARKDOWN',
};

export async function exportDocument(input: { title: string; content: string; format: ExportFormat }): Promise<Blob> {
  try {
    const res = await http.post(
      '/export',
      { title: input.title, content: input.content, format: EXPORT_FORMAT_MAP[input.format] },
      { responseType: 'blob' },
    );
    return res.data as Blob;
  } catch (e) {
    throw toApiError(e, '/api/v1/export');
  }
}
