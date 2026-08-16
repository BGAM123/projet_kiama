import type { DocumentTypeStatus, DocumentStatus, DocumentSectionStatus } from '@/types';

export const documentTypeStatusLabel: Record<DocumentTypeStatus, string> = {
  IMPORTE: 'Importé',
  EN_EXTRACTION: 'Extraction en cours',
  STRUCTURE_EXTRAITE: 'Structure extraite',
  EN_VALIDATION: 'En validation',
  ACTIF: 'Actif',
  ECHEC_EXTRACTION: 'Échec extraction',
  ARCHIVE: 'Archivé',
};

export const documentTypeStatusTone: Record<DocumentTypeStatus, string> = {
  IMPORTE: 'bg-info/15 text-info border-info/30',
  EN_EXTRACTION: 'bg-warning/15 text-warning border-warning/30',
  STRUCTURE_EXTRAITE: 'bg-info/15 text-info border-info/30',
  EN_VALIDATION: 'bg-accent/15 text-accent border-accent/30',
  ACTIF: 'bg-success/15 text-success border-success/30',
  ECHEC_EXTRACTION: 'bg-destructive/15 text-destructive border-destructive/30',
  ARCHIVE: 'bg-muted text-muted-foreground border-border',
};

export const documentStatusLabel: Record<DocumentStatus, string> = {
  BROUILLON: 'Brouillon',
  SAUVEGARDE: 'Sauvegardé',
  FINALISE: 'Finalisé',
  ARCHIVE: 'Archivé',
};

export const documentStatusTone: Record<DocumentStatus, string> = {
  BROUILLON: 'bg-muted text-muted-foreground border-border',
  SAUVEGARDE: 'bg-info/15 text-info border-info/30',
  FINALISE: 'bg-success/15 text-success border-success/30',
  ARCHIVE: 'bg-muted text-muted-foreground border-border',
};

export const sectionStatusLabel: Record<DocumentSectionStatus, string> = {
  EMPTY: 'Vide',
  DRAFTED: 'Rédigée',
  AI_IMPROVED: 'Améliorée par l\'IA',
  FINALIZED: 'Finalisée',
};

/** Couleur de la pastille de statut par section, dans la liste des sections. */
export const sectionStatusDot: Record<DocumentSectionStatus, string> = {
  EMPTY: 'bg-muted-foreground/40',
  DRAFTED: 'bg-warning',
  AI_IMPROVED: 'bg-success',
  FINALIZED: 'bg-success',
};

export const toneLabel: Record<string, string> = {
  FORMEL: 'Formel',
  INFORMATIF: 'Informatif',
  PERSUASIF: 'Persuasif',
  CONCIS: 'Concis',
  NEUTRE: 'Neutre',
};

export const languageLabel: Record<string, string> = {
  FR: 'Français',
  EN: 'Anglais',
  ES: 'Espagnol',
  DE: 'Allemand',
};

export const targetLengthLabel: Record<string, string> = {
  COURT: 'Court (1-3 pages)',
  MOYEN: 'Moyen (4-6 pages)',
  LONG: 'Long (8-10 pages)',
  EXTENSIF: 'Extensif (12+ pages)',
};

export const providerLabel: Record<string, string> = {
  OPENAI: 'OpenAI',
  CLAUDE: 'Anthropic Claude',
  GEMINI: 'Google Gemini',
  MISTRAL: 'Mistral AI',
  OLLAMA: 'Ollama (local)',
  DEEPSEEK: 'DeepSeek',
  GROQ: 'Groq',
  QWEN: 'Alibaba Qwen',
};

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('fr-FR', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString('fr-FR', {
    day: '2-digit',
    month: 'short',
    year: 'numeric',
  });
}

export function relativeTime(iso: string): string {
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return 'à l\'instant';
  if (min < 60) return `il y a ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `il y a ${h} h`;
  const d = Math.floor(h / 24);
  if (d < 30) return `il y a ${d} j`;
  return formatDate(iso);
}
