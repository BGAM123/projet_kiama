'use client';

import { Check, Loader2, Sparkles, X } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';

/**
 * Comparateur avant/après d'une suggestion d'amélioration IA — jamais
 * appliquée automatiquement : l'utilisateur choisit explicitement d'accepter
 * (copie dans le contenu retenu) ou de rejeter (suggestion effacée, contenu
 * retenu inchangé). Pas d'équivalent existant dans le repo à réutiliser.
 */
export function SectionSuggestionDiff({
  before,
  after,
  onAccept,
  onReject,
  pending,
}: {
  before: string;
  after: string;
  onAccept: () => void;
  onReject: () => void;
  pending?: boolean;
}) {
  return (
    <Card className="border-primary/30 bg-primary/5">
      <CardContent className="space-y-4 p-4">
        <div className="flex items-center gap-2 text-sm font-medium text-primary">
          <Sparkles className="h-4 w-4" /> Suggestion d&apos;amélioration IA
        </div>
        <div className="grid gap-3 sm:grid-cols-2">
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">Avant (votre texte)</p>
            <div className="max-h-56 overflow-y-auto rounded-md border border-border bg-background p-3 text-sm text-muted-foreground whitespace-pre-wrap">
              {before || <span className="italic">Vide</span>}
            </div>
          </div>
          <div className="space-y-1">
            <p className="text-xs font-medium uppercase tracking-wide text-primary">Après (proposition IA)</p>
            <div className="max-h-56 overflow-y-auto rounded-md border border-primary/30 bg-background p-3 text-sm whitespace-pre-wrap">
              {after}
            </div>
          </div>
        </div>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="outline" size="sm" disabled={pending} onClick={onReject}>
            <X className="mr-1.5 h-3.5 w-3.5" /> Rejeter
          </Button>
          <Button type="button" size="sm" disabled={pending} onClick={onAccept}>
            {pending ? <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" /> : <Check className="mr-1.5 h-3.5 w-3.5" />} Accepter
          </Button>
        </div>
      </CardContent>
    </Card>
  );
}
