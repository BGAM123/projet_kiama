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
  | 'cover'
  /** Emplacement de paragraphe à rédiger manuellement — flux "décrire en texte -> squelette généré par IA" uniquement, jamais de contenu rédigé. */
  | 'paragraph_placeholder';

/** Contraintes de format/longueur d'une section (validées par section, avec retry correctif, à la génération — voir SectionConstraintValidator côté backend). */
export interface SectionConstraints {
  minLength?: number;
  maxLength?: number;
  format?: string;
  pattern?: string;
}

/** Colonne attendue d'une section `type === 'table'` — flux de génération de squelette par IA (distinct de `columns`, simples noms produits par l'extraction déterministe d'un fichier importé). */
export interface TableColumnDef {
  name: string;
  type?: string;
}

export interface StructureNode {
  id: string;
  type: StructureNodeType;
  level?: number;
  label: string;
  children?: StructureNode[];
  columns?: string[];
  tableColumns?: TableColumnDef[];
  suggestedRowCount?: number;
  /** Section obligatoire — absent traité comme `true` côté backend (comportement historique). */
  required?: boolean;
  constraints?: SectionConstraints;
}

/** Origine de la structure : import de fichier + extraction déterministe (historique) ou flux "décrire en texte -> squelette généré par IA". */
export type DocumentStructureSource = 'IMPORTED' | 'AI_GENERATED';

export interface DocumentStructure {
  id: string;
  documentTypeId: string;
  tree: StructureNode[];
  hasToc: boolean;
  headerText?: string;
  footerText?: string;
  source?: DocumentStructureSource;
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

export type Tone = 'FORMEL' | 'INFORMATIF' | 'PERSUASIF' | 'CONCIS' | 'NEUTRE';
export type Language = 'FR' | 'EN' | 'ES' | 'DE';

/** Cycle de vie d'un document en édition manuelle assistée — remplace l'ancien statut de génération/échec IA (plus de flux conversationnel). */
export type DocumentStatus = 'BROUILLON' | 'FINALISE' | 'ARCHIVE';

export type DocumentSectionType = 'TITLE' | 'SUBTITLE' | 'SUB_SUBTITLE' | 'TABLE' | 'PARAGRAPH_PLACEHOLDER';

/** EMPTY (pas encore rédigée) -> DRAFTED (écrite par l'utilisateur) -> AI_IMPROVED (suggestion IA appliquée) -> FINALIZED. */
export type DocumentSectionStatus = 'EMPTY' | 'DRAFTED' | 'AI_IMPROVED' | 'FINALIZED';

export interface DocumentSection {
  id: string;
  parentSectionId?: string;
  type: DocumentSectionType;
  level?: number;
  label: string;
  tableColumns?: TableColumnDef[];
  order: number;
  userContent?: string;
  /** Suggestion de reformulation IA en attente de décision — jamais appliquée automatiquement (voir "Améliorer avec l'IA"). */
  aiSuggestedContent?: string;
  /**
   * Confiance (0-100) auto-déclarée par le modèle sur sa dernière sortie pour
   * cette section — absente tant qu'aucune amélioration n'a été produite, et
   * effacée dès que l'utilisateur réécrit la section ou rejette la suggestion.
   * Alimente la pastille de confiance et les seuils §3.4 de l'éditeur.
   */
  confidenceScore?: number;
  status: DocumentSectionStatus;
}

/**
 * Document en édition manuelle assistée. Nommé `AppDocument` (et non
 * `Document`) pour ne pas entrer en collision avec le type DOM global du
 * même nom.
 */
export interface AppDocument {
  id: string;
  documentTypeId: string;
  userId: string;
  status: DocumentStatus;
  language: Language;
  tone: Tone;
  sections: DocumentSection[];
  /** Moyenne des scores des sections évaluées — absente tant qu'aucune section n'a de score. */
  globalConfidenceScore?: number;
  createdAt: string;
  updatedAt?: string;
  /** URL de téléchargement pré-signée — absente tant que le document n'a pas été finalisé. */
  exportUrl?: string;
}

export type AiProvider =
  | 'OPENAI'
  | 'CLAUDE'
  | 'GEMINI'
  | 'MISTRAL'
  | 'OLLAMA'
  | 'DEEPSEEK'
  | 'GROQ'
  | 'QWEN';

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
