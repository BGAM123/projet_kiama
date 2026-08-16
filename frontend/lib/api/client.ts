// API client — intégralement branché sur le vrai backend Spring Boot.
//
// Périmètre : auth (login/refresh/logout/password), users (CRUD complet +
// reset mot de passe admin), roles (lecture), catégories (CRUD complet,
// Bloc 3), document-types (CRUD référentiel + structure, Bloc 3 ;
// import/ré-extraction/validation, Bloc 4), upload/extraction bas niveau,
// conversations/messages/documents de référence + générations/streaming
// (Bloc 6), export DOCX/PDF/Markdown, dashboard et configurations IA
// (Bloc 7/8), notifications, audit logs.
//
// Chaque fonction garde une signature stable pour que les hooks React Query
// et les composants qui les consomment n'aient pas à changer.

import { http, toApiError } from './http';
import type {
  AiModelConfig,
  AppDocument,
  AuditLogEntry,
  AuthSession,
  Category,
  Conversation,
  DashboardStats,
  DocumentSection,
  DocumentStructure,
  DocumentType,
  LoginPayload,
  Message,
  Notification,
  ReferenceDocument,
  Role,
  SectionConstraints,
  StructureNode,
  User,
} from '@/types';
import type { ApiSuccess } from '@/types';

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
// Categories — branché sur les vrais endpoints /categories du Bloc 3 (CRUD
// complet, lecture ouverte à tout authentifié, mutations réservées
// CATEGORY_MANAGE, cf. CategoryController). CategoryDTO backend correspond
// exactement à Category côté frontend, à ceci près que `description` est
// omise (JsonInclude NON_NULL) plutôt que renvoyée à null — on la
// reconstruit en chaîne vide pour respecter le type non-optionnel.
// ---------------------------------------------------------------------------

interface BackendCategoryDTO {
  id: string;
  name: string;
  description?: string | null;
}

function adaptCategoryDTO(dto: BackendCategoryDTO): Category {
  return { id: dto.id, name: dto.name, description: dto.description ?? '' };
}

export async function listCategories(): Promise<ApiSuccess<Category[]>> {
  try {
    const res = await http.get<{ data: BackendCategoryDTO[] }>('/categories');
    return ok(res.data.data.map(adaptCategoryDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/categories');
  }
}

export async function createCategory(input: Omit<Category, 'id'>): Promise<ApiSuccess<Category>> {
  try {
    const res = await http.post<{ data: BackendCategoryDTO }>('/categories', {
      name: input.name,
      description: input.description,
    });
    return ok(adaptCategoryDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/categories');
  }
}

export async function updateCategory(id: string, patch: Partial<Category>): Promise<ApiSuccess<Category>> {
  try {
    const res = await http.patch<{ data: BackendCategoryDTO }>(`/categories/${id}`, {
      name: patch.name,
      description: patch.description,
    });
    return ok(adaptCategoryDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/categories/${id}`);
  }
}

export async function deleteCategory(id: string): Promise<ApiSuccess<{ id: string }>> {
  try {
    // Le backend refuse la suppression (409) si un Document Type est encore
    // rattaché à la catégorie — l'erreur remonte telle quelle via toApiError
    // pour être affichée par l'appelant (cf. admin/categories/page.tsx).
    await http.delete(`/categories/${id}`);
    return ok({ id });
  } catch (e) {
    throw toApiError(e, `/api/v1/categories/${id}`);
  }
}

// ---------------------------------------------------------------------------
// Document types & structures — branché sur les vrais endpoints
// /document-types du Bloc 3 (cf. DocumentTypeController). Le CRUD référentiel
// (nom/description/catégorie), l'archivage et la lecture/correction de la
// structure sont réels. L'import de fichier (création d'un nouveau Document
// Type) et les transitions de statut pilotées par le pipeline d'extraction
// (IMPORTE → EN_EXTRACTION → … → ACTIF) sont explicitement hors périmètre de
// ce contrôleur (Bloc 4, pas encore livré côté backend) : `importDocumentType`
// reste donc mocké ci-dessous, faute d'endpoint équivalent.
// ---------------------------------------------------------------------------

interface BackendStructureNodeDTO {
  id: string;
  type: string;
  level?: number | null;
  label: string;
  children?: BackendStructureNodeDTO[] | null;
  columns?: string[] | null;
  tableColumns?: { name: string; type?: string }[] | null;
  suggestedRowCount?: number | null;
  required?: boolean | null;
  constraints?: SectionConstraints | null;
}

function adaptStructureNodeDTO(dto: BackendStructureNodeDTO): StructureNode {
  return {
    id: dto.id,
    type: dto.type as StructureNode['type'],
    level: dto.level ?? undefined,
    label: dto.label,
    children: dto.children?.map(adaptStructureNodeDTO),
    columns: dto.columns ?? undefined,
    tableColumns: dto.tableColumns ?? undefined,
    suggestedRowCount: dto.suggestedRowCount ?? undefined,
    // Non édités par l'UI actuelle (éditeur limité au renommage/ajout/suppression
    // de sections) — repassés tels quels pour ne pas les perdre au premier
    // enregistrement si un Document Type en a déjà (ex. défini via l'API).
    required: dto.required ?? undefined,
    constraints: dto.constraints ?? undefined,
  };
}

interface BackendDocumentTypeDTO {
  id: string;
  name: string;
  description?: string | null;
  categoryId: string;
  status: string;
  version: number;
  createdAt: string;
}

function adaptDocumentTypeDTO(dto: BackendDocumentTypeDTO): DocumentType {
  return {
    id: dto.id,
    name: dto.name,
    description: dto.description ?? '',
    categoryId: dto.categoryId,
    status: dto.status as DocumentType['status'],
    version: dto.version,
    createdAt: dto.createdAt,
  };
}

interface BackendDocumentStructureDTO {
  id: string;
  documentTypeId: string;
  tree: BackendStructureNodeDTO[];
  hasToc?: boolean | null;
  source?: string | null;
}

function adaptDocumentStructureDTO(dto: BackendDocumentStructureDTO): DocumentStructure {
  return {
    id: dto.id,
    documentTypeId: dto.documentTypeId,
    tree: (dto.tree ?? []).map(adaptStructureNodeDTO),
    hasToc: dto.hasToc ?? false,
    source: (dto.source as DocumentStructure['source']) ?? undefined,
  };
}

export async function listDocumentTypes(): Promise<ApiSuccess<DocumentType[]>> {
  try {
    const res = await http.get<{ data: BackendDocumentTypeDTO[] }>('/document-types');
    return ok(res.data.data.map(adaptDocumentTypeDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/document-types');
  }
}

export async function getDocumentType(id: string): Promise<ApiSuccess<DocumentType>> {
  try {
    const res = await http.get<{ data: BackendDocumentTypeDTO }>(`/document-types/${id}`);
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${id}`);
  }
}

// Ne couvre que le CRUD référentiel réellement supporté par le backend
// (nom/description/catégorie) — un éventuel `patch.status` (ex. ancien flux
// mock "Valider & activer") est ignoré côté serveur : UpdateDocumentTypeRequest
// n'a pas de champ statut, celui-ci n'évoluant que via le pipeline
// d'extraction (Bloc 4). Voir documents-types/[id]/structure/page.tsx, où le
// bouton d'activation est désactivé tant que ce pipeline n'existe pas.
export async function updateDocumentType(id: string, patch: Partial<DocumentType>): Promise<ApiSuccess<DocumentType>> {
  try {
    const res = await http.patch<{ data: BackendDocumentTypeDTO }>(`/document-types/${id}`, {
      name: patch.name,
      description: patch.description,
      categoryId: patch.categoryId,
    });
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${id}`);
  }
}

export async function deleteDocumentType(id: string): Promise<ApiSuccess<{ id: string }>> {
  try {
    // DELETE /document-types/{id} = archivage logique (statut -> ARCHIVE)
    // côté backend, pas une suppression physique — même sémantique que
    // l'ancien mock (cf. bouton "Archiver" de documents-types/page.tsx).
    await http.delete(`/document-types/${id}`);
    return ok({ id });
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${id}`);
  }
}

// Bloc 4 — POST /document-types/import fait tout en un appel côté backend :
// upload + extraction du texte (Tika) + construction de l'arbre de structure
// (POI pour .docx, découpage en paragraphes pour pdf/txt/md/doc) + création
// du DocumentType (statut STRUCTURE_EXTRAITE, ou ECHEC_EXTRACTION si
// l'extraction de structure échoue — le fichier reste importé et stocké
// dans ce cas, cf. POST .../extract pour relancer).
export async function importDocumentType(input: { file: File; name: string; description?: string; categoryId: string }): Promise<ApiSuccess<DocumentType>> {
  try {
    const form = new FormData();
    form.append('file', input.file);
    form.append('name', input.name);
    if (input.description) form.append('description', input.description);
    form.append('categoryId', input.categoryId);
    // Ne PAS fixer le header Content-Type manuellement, cf. uploadAndExtractDocument.
    const res = await http.post<{ data: BackendDocumentTypeDTO }>('/document-types/import', form);
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/document-types/import');
  }
}

/**
 * Flux "décrire en texte -> squelette généré par IA" : coexiste avec
 * `importDocumentType` (import de fichier) — mêmes statuts en sortie
 * (STRUCTURE_EXTRAITE, ou ECHEC_EXTRACTION si le LLM n'a pas produit un
 * squelette conforme après ses tentatives de correction côté serveur).
 */
export async function generateDocumentType(input: { description: string; name: string; categoryId: string }): Promise<ApiSuccess<DocumentType>> {
  try {
    const res = await http.post<{ data: BackendDocumentTypeDTO }>('/document-types/generate', input);
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/document-types/generate');
  }
}

/** Relance l'extraction de structure à partir du fichier source déjà stocké (ex. après un ECHEC_EXTRACTION). */
export async function reextractDocumentType(id: string): Promise<ApiSuccess<DocumentType>> {
  try {
    const res = await http.post<{ data: BackendDocumentTypeDTO }>(`/document-types/${id}/extract`);
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${id}/extract`);
  }
}

/** Valide et active un Document Type dont la structure a été extraite (STRUCTURE_EXTRAITE|EN_VALIDATION -> ACTIF). */
export async function validateDocumentType(id: string): Promise<ApiSuccess<DocumentType>> {
  try {
    const res = await http.post<{ data: BackendDocumentTypeDTO }>(`/document-types/${id}/validate`);
    return ok(adaptDocumentTypeDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${id}/validate`);
  }
}

export async function getStructure(documentTypeId: string): Promise<ApiSuccess<DocumentStructure>> {
  try {
    const res = await http.get<{ data: BackendDocumentStructureDTO }>(`/document-types/${documentTypeId}/structure`);
    return ok(adaptDocumentStructureDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${documentTypeId}/structure`);
  }
}

export async function updateStructure(documentTypeId: string, tree: DocumentStructure['tree']): Promise<ApiSuccess<DocumentStructure>> {
  try {
    const res = await http.put<{ data: BackendDocumentStructureDTO }>(`/document-types/${documentTypeId}/structure`, { tree });
    return ok(adaptDocumentStructureDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/document-types/${documentTypeId}/structure`);
  }
}

// ---------------------------------------------------------------------------
// Conversations & messages — branché sur les vrais endpoints /conversations
// du Bloc 6 (permission CONVERSATION_USE, cf. ConversationController).
// L'envoi d'un message (sendMessage) déclenche réellement un appel IA côté
// serveur pour la réponse assistant — celle-ci n'est pas renvoyée par
// l'appel lui-même (qui renvoie le message utilisateur créé, comme le mock)
// mais apparaît après invalidation de la query ['messages', conversationId].
// ---------------------------------------------------------------------------

interface BackendConversationDTO {
  id: string;
  userId: string;
  documentTypeId: string;
  title?: string | null;
  createdAt: string;
}

function adaptConversationDTO(dto: BackendConversationDTO): Conversation {
  return { id: dto.id, userId: dto.userId, documentTypeId: dto.documentTypeId, title: dto.title ?? '', createdAt: dto.createdAt };
}

interface BackendMessageDTO {
  id: string;
  conversationId: string;
  role: string;
  content: string;
  createdAt: string;
}

function adaptMessageDTO(dto: BackendMessageDTO): Message {
  return { id: dto.id, conversationId: dto.conversationId, role: dto.role as Message['role'], content: dto.content, createdAt: dto.createdAt };
}

export async function listConversations(userId: string): Promise<ApiSuccess<Conversation[]>> {
  try {
    const res = await http.get<{ data: BackendConversationDTO[] }>('/conversations', { params: { userId } });
    return ok(res.data.data.map(adaptConversationDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/conversations');
  }
}

export async function createConversation(input: { userId: string; documentTypeId: string; title: string }): Promise<ApiSuccess<Conversation>> {
  try {
    const res = await http.post<{ data: BackendConversationDTO }>('/conversations', input);
    return ok(adaptConversationDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/conversations');
  }
}

/**
 * Historique complet d'une conversation. L'endpoint backend est paginé
 * (ordre chronologique ascendant, page 0 = les plus anciens — voir
 * ConversationController#listMessages) alors que l'UI actuelle affiche tout
 * l'historique d'un bloc, sans pagination visuelle : on reconstitue donc la
 * liste complète ici en enchaînant les pages, plutôt que de se limiter à la
 * première (ce qui masquerait silencieusement les messages les plus récents
 * dès qu'une conversation dépasse une page).
 */
export async function listMessages(conversationId: string): Promise<ApiSuccess<Message[]>> {
  try {
    const pageSize = 200;
    const all: BackendMessageDTO[] = [];
    let page = 0;
    for (;;) {
      const res = await http.get<{ data: BackendMessageDTO[]; meta?: { totalPages?: number } }>(
        `/conversations/${conversationId}/messages`,
        { params: { page, size: pageSize } },
      );
      all.push(...res.data.data);
      const totalPages = res.data.meta?.totalPages ?? 1;
      page += 1;
      if (page >= totalPages || res.data.data.length === 0) {
        break;
      }
    }
    return ok(all.map(adaptMessageDTO));
  } catch (e) {
    throw toApiError(e, `/api/v1/conversations/${conversationId}/messages`);
  }
}

export async function sendMessage(conversationId: string, content: string): Promise<ApiSuccess<Message>> {
  try {
    const res = await http.post<{ data: BackendMessageDTO }>(`/conversations/${conversationId}/messages`, { content });
    return ok(adaptMessageDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/conversations/${conversationId}/messages`);
  }
}

// ---------------------------------------------------------------------------
// Reference documents — upload réel (MinIO + indexation RAG côté serveur,
// dégradée sans échouer l'import si l'indexation échoue).
// ---------------------------------------------------------------------------

interface BackendReferenceDocumentDTO {
  id: string;
  conversationId: string;
  fileName: string;
  storagePath: string;
  importedAt: string;
}

function adaptReferenceDocumentDTO(dto: BackendReferenceDocumentDTO): ReferenceDocument {
  return { id: dto.id, conversationId: dto.conversationId, fileName: dto.fileName, storagePath: dto.storagePath, importedAt: dto.importedAt };
}

export async function addReferenceDocument(conversationId: string, file: File): Promise<ApiSuccess<ReferenceDocument>> {
  try {
    const form = new FormData();
    // Ne PAS fixer le header Content-Type manuellement, cf. uploadAndExtractDocument.
    form.append('file', file);
    const res = await http.post<{ data: BackendReferenceDocumentDTO }>(`/conversations/${conversationId}/reference-documents`, form);
    return ok(adaptReferenceDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/conversations/${conversationId}/reference-documents`);
  }
}

export async function listReferenceDocuments(conversationId: string): Promise<ApiSuccess<ReferenceDocument[]>> {
  try {
    const res = await http.get<{ data: BackendReferenceDocumentDTO[] }>(`/conversations/${conversationId}/reference-documents`);
    return ok(res.data.data.map(adaptReferenceDocumentDTO));
  } catch (e) {
    throw toApiError(e, `/api/v1/conversations/${conversationId}/reference-documents`);
  }
}

// ---------------------------------------------------------------------------
// Documents — édition manuelle assistée par section (branché sur les vrais
// endpoints /documents du Bloc 2 de la refonte, cf. DocumentController).
// Remplace l'ancien flux conversationnel de génération (/generations, SSE) :
// création à partir d'un Document Type (sections vides initialisées depuis
// son squelette), sauvegarde du contenu rédigé section par section,
// amélioration/acceptation/rejet de suggestions IA, finalisation + export.
// ---------------------------------------------------------------------------

interface BackendDocumentSectionDTO {
  id: string;
  parentSectionId?: string | null;
  type: string;
  level?: number | null;
  label: string;
  tableColumns?: { name: string; type?: string }[] | null;
  order: number;
  userContent?: string | null;
  aiSuggestedContent?: string | null;
  confidenceScore?: number | null;
  status: string;
}

function adaptDocumentSectionDTO(dto: BackendDocumentSectionDTO): DocumentSection {
  return {
    id: dto.id,
    parentSectionId: dto.parentSectionId ?? undefined,
    type: dto.type as DocumentSection['type'],
    level: dto.level ?? undefined,
    label: dto.label,
    tableColumns: dto.tableColumns ?? undefined,
    order: dto.order,
    userContent: dto.userContent ?? undefined,
    aiSuggestedContent: dto.aiSuggestedContent ?? undefined,
    confidenceScore: dto.confidenceScore ?? undefined,
    status: dto.status as DocumentSection['status'],
  };
}

interface BackendDocumentDTO {
  id: string;
  documentTypeId?: string | null;
  userId: string;
  status: string;
  language: string;
  tone: string;
  title?: string | null;
  sections?: BackendDocumentSectionDTO[] | null;
  contentHtml?: string | null;
  globalConfidenceScore?: number | null;
  createdAt: string;
  updatedAt?: string | null;
  exportUrl?: string | null;
}

function adaptDocumentDTO(dto: BackendDocumentDTO): AppDocument {
  return {
    id: dto.id,
    documentTypeId: dto.documentTypeId ?? undefined,
    userId: dto.userId,
    status: dto.status as AppDocument['status'],
    language: dto.language as AppDocument['language'],
    tone: dto.tone as AppDocument['tone'],
    title: dto.title ?? undefined,
    sections: (dto.sections ?? []).map(adaptDocumentSectionDTO),
    contentHtml: dto.contentHtml ?? undefined,
    globalConfidenceScore: dto.globalConfidenceScore ?? undefined,
    createdAt: dto.createdAt,
    updatedAt: dto.updatedAt ?? undefined,
    exportUrl: dto.exportUrl ?? undefined,
  };
}

export async function createDocument(documentTypeId: string): Promise<ApiSuccess<AppDocument>> {
  try {
    const res = await http.post<{ data: BackendDocumentDTO }>('/documents', { documentTypeId });
    return ok(adaptDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/documents');
  }
}

/**
 * Second point d'entrée de la rédaction : importe un fichier existant
 * (.docx/.pdf/.md/.txt/.doc) et le renvoie directement éditable dans
 * l'éditeur type Word, sans passer par un Document Type. Le .docx conserve sa
 * mise en forme (gras, couleurs, tableaux, images…) ; les autres formats sont
 * reformés en paragraphes de texte brut.
 */
export async function importDocument(file: File): Promise<ApiSuccess<AppDocument>> {
  try {
    const form = new FormData();
    // Ne PAS fixer le header Content-Type manuellement, cf. uploadAndExtractDocument.
    form.append('file', file);
    const res = await http.post<{ data: BackendDocumentDTO }>('/documents/import', form);
    return ok(adaptDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, '/api/v1/documents/import');
  }
}

export async function listDocuments(userId: string): Promise<ApiSuccess<AppDocument[]>> {
  try {
    const res = await http.get<{ data: BackendDocumentDTO[] }>('/documents', { params: { userId } });
    return ok(res.data.data.map(adaptDocumentDTO));
  } catch (e) {
    throw toApiError(e, '/api/v1/documents');
  }
}

export async function getDocument(id: string): Promise<ApiSuccess<AppDocument>> {
  try {
    const res = await http.get<{ data: BackendDocumentDTO }>(`/documents/${id}`);
    return ok(adaptDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${id}`);
  }
}

/**
 * Sauvegarde du document mis en forme dans l'éditeur type Word (autosave
 * debouncée, cf. app/(app)/documents/[id]/page.tsx). Le HTML est stocké tel
 * quel : c'est lui que l'export serveur relit pour produire le DOCX/PDF.
 */
export async function updateDocumentContent(id: string, contentHtml: string): Promise<ApiSuccess<AppDocument>> {
  try {
    const res = await http.put<{ data: BackendDocumentDTO }>(`/documents/${id}/content`, { contentHtml });
    return ok(adaptDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${id}/content`);
  }
}

/** Autosave par section — flux hérité, conservé pour les documents créés avant l'éditeur type Word. */
export async function updateDocumentSection(documentId: string, sectionId: string, content: string): Promise<ApiSuccess<DocumentSection>> {
  try {
    const res = await http.put<{ data: BackendDocumentSectionDTO }>(`/documents/${documentId}/sections/${sectionId}`, { content });
    return ok(adaptDocumentSectionDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${documentId}/sections/${sectionId}`);
  }
}

/**
 * Amélioration rédactionnelle du passage sélectionné dans l'éditeur type Word.
 * La suggestion est retournée sans être appliquée : c'est l'utilisateur qui
 * décide de remplacer sa sélection après comparaison avant/après.
 */
export async function improveDocumentSelection(
  documentId: string,
  text: string,
): Promise<ApiSuccess<{ aiSuggestedContent: string; confidenceScore?: number }>> {
  try {
    const res = await http.post<{ data: { aiSuggestedContent: string; confidenceScore?: number } }>(
      `/documents/${documentId}/improve-selection`,
      { text },
    );
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${documentId}/improve-selection`);
  }
}

/** Retourne la suggestion IA sans jamais l'appliquer — voir applySectionSuggestion/rejectSectionSuggestion. `confidenceScore` est absent quand le fournisseur n'a pas respecté le format de réponse attendu. */
export async function improveDocumentSection(
  documentId: string,
  sectionId: string,
): Promise<ApiSuccess<{ aiSuggestedContent: string; confidenceScore?: number }>> {
  try {
    const res = await http.post<{ data: { aiSuggestedContent: string; confidenceScore?: number } }>(`/documents/${documentId}/sections/${sectionId}/improve`);
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${documentId}/sections/${sectionId}/improve`);
  }
}

export async function applySectionSuggestion(documentId: string, sectionId: string): Promise<ApiSuccess<DocumentSection>> {
  try {
    const res = await http.post<{ data: BackendDocumentSectionDTO }>(`/documents/${documentId}/sections/${sectionId}/apply-suggestion`);
    return ok(adaptDocumentSectionDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${documentId}/sections/${sectionId}/apply-suggestion`);
  }
}

export async function rejectSectionSuggestion(documentId: string, sectionId: string): Promise<ApiSuccess<DocumentSection>> {
  try {
    const res = await http.post<{ data: BackendDocumentSectionDTO }>(`/documents/${documentId}/sections/${sectionId}/reject-suggestion`);
    return ok(adaptDocumentSectionDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${documentId}/sections/${sectionId}/reject-suggestion`);
  }
}

export async function finalizeDocument(id: string): Promise<ApiSuccess<AppDocument>> {
  try {
    const res = await http.post<{ data: BackendDocumentDTO }>(`/documents/${id}/finalize`);
    return ok(adaptDocumentDTO(res.data.data));
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${id}/finalize`);
  }
}

/**
 * Téléchargement du document dans le format demandé — le contenu est
 * réassemblé côté serveur depuis les sections à chaque appel (donc toujours à
 * jour), indépendamment de l'archive DOCX déposée sur MinIO à la finalisation.
 */
export async function exportDocumentFile(id: string, format: ExportFormat): Promise<Blob> {
  try {
    const res = await http.get(`/documents/${id}/export`, {
      params: { format: EXPORT_FORMAT_MAP[format] },
      responseType: 'blob',
    });
    return res.data as Blob;
  } catch (e) {
    throw toApiError(e, `/api/v1/documents/${id}/export`);
  }
}

// ---------------------------------------------------------------------------
// AI configs — branché sur /api/v1/ai-configs (Bloc 8).
// ---------------------------------------------------------------------------

export async function listAiConfigs(): Promise<ApiSuccess<AiModelConfig[]>> {
  try {
    const res = await http.get<{ data: AiModelConfig[] }>('/ai-configs');
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, '/api/v1/ai-configs');
  }
}

export async function updateAiConfig(id: string, patch: Partial<AiModelConfig>): Promise<ApiSuccess<AiModelConfig>> {
  try {
    const res = await http.patch<{ data: AiModelConfig }>(`/ai-configs/${id}`, patch);
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, `/api/v1/ai-configs/${id}`);
  }
}

// ---------------------------------------------------------------------------
// Dashboard stats — branché sur /api/v1/dashboard/stats (Bloc 8).
// ---------------------------------------------------------------------------

export async function getDashboardStats(userId: string): Promise<ApiSuccess<DashboardStats>> {
  try {
    const res = await http.get<{ data: DashboardStats }>('/dashboard/stats', { params: { userId } });
    return ok(res.data.data);
  } catch (e) {
    throw toApiError(e, '/api/v1/dashboard/stats');
  }
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

export async function exportDocument(input: {
  title: string;
  content: string;
  format: ExportFormat;
  headerText?: string;
  footerText?: string;
}): Promise<Blob> {
  try {
    const res = await http.post(
      '/export',
      {
        title: input.title,
        content: input.content,
        format: EXPORT_FORMAT_MAP[input.format],
        headerText: input.headerText,
        footerText: input.footerText,
      },
      { responseType: 'blob' },
    );
    return res.data as Blob;
  } catch (e) {
    throw toApiError(e, '/api/v1/export');
  }
}
