// Core domain types for DocuAI frontend
// camelCase convention; all entities correspond to backend models.

export type RoleName = 'ADMIN' | 'UTILISATEUR';

export interface Permission {
  id: string;
  code: string;
  description: string;
}

export interface Role {
  id: string;
  name: RoleName | string;
  description: string;
  permissions: Permission[];
}

export interface User {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  active: boolean;
  roles: Role[];
  createdAt: string;
}

export interface Category {
  id: string;
  name: string;
  description: string;
}

export type DocumentTypeStatus =
  | 'IMPORTE'
  | 'EN_EXTRACTION'
  | 'STRUCTURE_EXTRAITE'
  | 'EN_VALIDATION'
  | 'ACTIF'
  | 'ECHEC_EXTRACTION'
  | 'ARCHIVE';

export interface DocumentType {
  id: string;
  name: string;
  description: string;
  categoryId: string;
  status: DocumentTypeStatus;
  version: number;
  createdAt: string;
}

export type StructureNodeType =
  | 'heading'
  | 'paragraph'
  | 'table'
  | 'list'
  | 'cover';

export interface StructureNode {
  id: string;
  type: StructureNodeType;
  level?: number;
  label: string;
  children?: StructureNode[];
  columns?: string[];
}

export interface DocumentStructure {
  id: string;
  documentTypeId: string;
  tree: StructureNode[];
  hasToc: boolean;
}

export interface Conversation {
  id: string;
  userId: string;
  documentTypeId: string;
  title: string;
  createdAt: string;
}

export type MessageRole = 'user' | 'assistant';

export interface Message {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  createdAt: string;
}

export interface ReferenceDocument {
  id: string;
  conversationId: string;
  fileName: string;
  storagePath: string;
  importedAt: string;
}

export type GeneratedDocumentStatus =
  | 'BROUILLON'
  | 'EN_GENERATION'
  | 'GENERE'
  | 'ECHEC'
  | 'EN_EDITION'
  | 'EXPORTE'
  | 'ARCHIVE';

export type Tone = 'FORMEL' | 'INFORMATIF' | 'PERSUASIF' | 'CONCIS' | 'NEUTRE';
export type Language = 'FR' | 'EN' | 'ES' | 'DE';
export type TargetLength = 'COURT' | 'MOYEN' | 'LONG' | 'EXTENSIF';

export interface GeneratedDocument {
  id: string;
  conversationId: string;
  documentTypeId: string;
  userId: string;
  status: GeneratedDocumentStatus;
  language: Language;
  tone: Tone;
  targetLength: TargetLength;
  contentPivot: string;
  content: string;
  sections: GenerationSection[];
  createdAt: string;
  updatedAt: string;
}

export interface GenerationSection {
  id: string;
  label: string;
  status: 'PENDING' | 'GENERATING' | 'DONE' | 'FAILED';
  content: string;
}

export type AiProvider =
  | 'OPENAI'
  | 'CLAUDE'
  | 'GEMINI'
  | 'MISTRAL'
  | 'OLLAMA'
  | 'DEEPSEEK';

export interface AiModelConfig {
  id: string;
  provider: AiProvider;
  modelName: string;
  apiKeyRef: string;
  isDefault: boolean;
  active: boolean;
}

export interface AuditLogEntry {
  id: string;
  userId: string;
  userName: string;
  action: string;
  entityType: string;
  entityId: string;
  timestamp: string;
  ipAddress: string;
}

export interface Notification {
  id: string;
  userId: string;
  type: 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR';
  content: string;
  read: boolean;
  createdAt: string;
}

export interface DashboardStats {
  documentsThisMonth: number;
  averageGenerationTimeSec: number;
  activeDocumentTypes: number;
  successRate: number;
  byCategory: { categoryId: string; categoryName: string; count: number }[];
  last7Days: { date: string; count: number }[];
}

// Standard API envelope (section 4)
export interface ApiSuccess<T> {
  data: T;
  error: null;
  meta?: { total?: number; page?: number; pageSize?: number };
}

export interface ApiErrorBody {
  error: {
    code: string;
    message: string;
    timestamp: string;
    path: string;
  };
}

export interface AuthSession {
  accessToken: string;
  refreshToken: string;
  user: User;
}

export interface LoginPayload {
  email: string;
  password: string;
}
