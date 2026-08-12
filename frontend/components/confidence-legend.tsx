'use client';

import { useState } from 'react';
import { ChevronDown, Gauge, Info } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { CONFIDENCE_THRESHOLDS, UNSCORED, confidenceThreshold, formatConfidence } from '@/lib/confidence';
import { cn } from '@/lib/utils';

/**
 * Guide des seuils de confiance (§3.4), replié par défaut : c'est une aide à
 * la lecture des pastilles, pas une information dont on a besoin en
 * permanence — l'espace vertical revient à l'éditeur.
 */
export function ConfidenceLegend() {
  const [open, setOpen] = useState(false);

  return (
    <Card>
      <CardHeader className="flex flex-row items-center justify-between space-y-0 py-3">
        <CardTitle className="flex items-center gap-2 text-sm font-medium">
          <Info className="h-4 w-4 text-info" />
          Guide des seuils de confiance ML (§3.4)
        </CardTitle>
        <Button
          type="button"
          variant="ghost"
          size="sm"
          onClick={() => setOpen((v) => !v)}
          aria-expanded={open}
          className="text-xs"
        >
          {open ? 'Réduire' : 'Afficher'}
          <ChevronDown className={cn('ml-1 h-3.5 w-3.5 transition-transform', open && 'rotate-180')} />
        </Button>
      </CardHeader>
      {open && (
        <CardContent className="space-y-2 pb-4">
          <p className="text-xs text-muted-foreground">
            Le score est déclaré par le modèle sur la reformulation qu&apos;il propose. Il indique quelles sections
            relire en priorité — ce n&apos;est pas une probabilité mesurée.
          </p>
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-4">
            {[...CONFIDENCE_THRESHOLDS, UNSCORED].map((threshold) => (
              <div
                key={threshold.level}
                className={cn('rounded-md border border-l-4 bg-muted/30 p-3', threshold.border)}
              >
                <div className={cn('text-xs font-semibold', threshold.text)}>{threshold.range}</div>
                <div className={cn('text-sm font-medium', threshold.text)}>{threshold.interpretation}</div>
                <p className="mt-1 text-xs leading-relaxed text-muted-foreground">{threshold.action}</p>
              </div>
            ))}
          </div>
        </CardContent>
      )}
    </Card>
  );
}

/** Pastille de confiance d'une section — même barème que le guide ci-dessus. */
export function ConfidencePill({ score, className }: { score?: number; className?: string }) {
  const threshold = confidenceThreshold(score);
  return (
    <Badge
      variant="outline"
      className={cn('gap-1 font-semibold', threshold.pill, className)}
      title={`${threshold.interpretation} — ${threshold.action}`}
    >
      <Gauge className="h-3 w-3" />
      {formatConfidence(score)}
    </Badge>
  );
}
