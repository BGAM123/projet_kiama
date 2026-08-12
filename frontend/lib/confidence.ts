import type { DocumentSection } from '@/types';

/**
 * Seuils de confiance ML (§3.4) — un seul barème pour toute l'application :
 * la pastille par section, la pastille du plan, le score global et le guide
 * affiché en tête de l'éditeur lisent tous ce tableau, pour qu'une couleur ne
 * puisse jamais dire une chose à un endroit et une autre ailleurs.
 *
 * Le score est auto-déclaré par le modèle au moment où il propose une
 * reformulation (cf. SectionImprovementResponseParser côté backend) : c'est un
 * signal de priorité de relecture, pas une probabilité calibrée.
 */
export type ConfidenceLevel = 'HIGH' | 'MEDIUM' | 'LOW';

export interface ConfidenceThreshold {
  level: ConfidenceLevel;
  /** Borne inférieure incluse — les bandes sont contiguës et couvrent [0, 100]. */
  min: number;
  range: string;
  interpretation: string;
  action: string;
  /** Classes Tailwind de la pastille (fond + texte + bordure), tokens sémantiques du thème. */
  pill: string;
  /** Classe de fond de la pastille du plan et de la barre de progression. */
  dot: string;
  /** Classe de couleur de texte, pour le pourcentage et le liseré du guide. */
  text: string;
  border: string;
}

export const CONFIDENCE_THRESHOLDS: ConfidenceThreshold[] = [
  {
    level: 'HIGH',
    min: 76,
    range: '76 – 100',
    interpretation: 'Confiance élevée',
    action: 'Contenu exploitable tel quel — une relecture de forme suffit.',
    pill: 'border-success/30 bg-success/15 text-success',
    dot: 'bg-success',
    text: 'text-success',
    border: 'border-l-success',
  },
  {
    level: 'MEDIUM',
    min: 60,
    range: '60 – 75',
    interpretation: 'Confiance moyenne',
    action: 'Relecture attentive : vérifiez les faits et la cohérence avec le reste du document.',
    pill: 'border-warning/30 bg-warning/15 text-warning',
    dot: 'bg-warning',
    text: 'text-warning',
    border: 'border-l-warning',
  },
  {
    level: 'LOW',
    min: 0,
    range: '0 – 59',
    interpretation: 'Confiance faible',
    action: 'Reprise manuelle recommandée — régénérez la section ou réécrivez-la vous-même.',
    pill: 'border-destructive/30 bg-destructive/15 text-destructive',
    dot: 'bg-destructive',
    text: 'text-destructive',
    border: 'border-l-destructive',
  },
];

/** Section jamais évaluée (aucune sortie IA retenue) — neutre, à distinguer d'une confiance faible. */
export const UNSCORED: Omit<ConfidenceThreshold, 'level' | 'min'> & { level: 'UNSCORED' } = {
  level: 'UNSCORED',
  range: '—',
  interpretation: 'Non évaluée',
  action: "Aucune suggestion IA sur cette section : rien à arbitrer, le contenu est celui que vous avez écrit.",
  pill: 'border-border bg-muted text-muted-foreground',
  dot: 'bg-muted-foreground/40',
  text: 'text-muted-foreground',
  border: 'border-l-border',
};

export function confidenceThreshold(score: number | undefined | null) {
  if (score === undefined || score === null) return UNSCORED;
  return CONFIDENCE_THRESHOLDS.find((t) => score >= t.min) ?? CONFIDENCE_THRESHOLDS[CONFIDENCE_THRESHOLDS.length - 1];
}

/** "82 %" pour une section évaluée, "—" sinon — même rendu partout. */
export function formatConfidence(score: number | undefined | null): string {
  return score === undefined || score === null ? '—' : `${Math.round(score)} %`;
}

/**
 * Numérotation hiérarchique du plan (1, 1.1, 1.1.2…), reconstruite depuis la
 * liste à plat des sections et leurs `parentSectionId`. Le backend ne stocke
 * qu'un ordre global : la numérotation est purement une affaire d'affichage.
 */
export function buildSectionNumbers(sections: DocumentSection[]): Map<string, string> {
  const childrenOf = new Map<string | undefined, DocumentSection[]>();
  for (const section of sections) {
    const key = section.parentSectionId;
    const siblings = childrenOf.get(key) ?? [];
    siblings.push(section);
    childrenOf.set(key, siblings);
  }

  const numbers = new Map<string, string>();
  function walk(parentId: string | undefined, prefix: string) {
    const siblings = (childrenOf.get(parentId) ?? []).slice().sort((a, b) => a.order - b.order);
    siblings.forEach((section, index) => {
      const number = prefix ? `${prefix}.${index + 1}` : `${index + 1}`;
      numbers.set(section.id, number);
      walk(section.id, number);
    });
  }
  walk(undefined, '');

  // Filet de sécurité : une section dont le parent n'est pas dans la liste
  // (données partielles) resterait sans numéro et disparaîtrait visuellement.
  sections.forEach((section, index) => {
    if (!numbers.has(section.id)) numbers.set(section.id, `${index + 1}`);
  });
  return numbers;
}
