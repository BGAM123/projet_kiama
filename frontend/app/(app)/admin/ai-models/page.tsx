'use client';

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { Sparkles, Star, Check, Loader2, Save } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Switch } from '@/components/ui/switch';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { useAiConfigs } from '@/lib/hooks/queries';
import { updateAiConfig } from '@/lib/api/client';
import { providerLabel } from '@/lib/format';
import { cn } from '@/lib/utils';
import type { AiModelConfig, AiProvider } from '@/types';

const providerColor: Record<AiProvider, string> = {
  OPENAI: 'bg-emerald-500/15 text-emerald-600 dark:text-emerald-400',
  CLAUDE: 'bg-orange-500/15 text-orange-600 dark:text-orange-400',
  GEMINI: 'bg-blue-500/15 text-blue-600 dark:text-blue-400',
  MISTRAL: 'bg-rose-500/15 text-rose-600 dark:text-rose-400',
  OLLAMA: 'bg-slate-500/15 text-slate-600 dark:text-slate-300',
  DEEPSEEK: 'bg-indigo-500/15 text-indigo-600 dark:text-indigo-400',
  GROQ: 'bg-amber-500/15 text-amber-600 dark:text-amber-400',
  QWEN: 'bg-fuchsia-500/15 text-fuchsia-600 dark:text-fuchsia-400',
};

export default function AdminAiModelsPage() {
  const { data: configs, isLoading } = useAiConfigs();
  const qc = useQueryClient();
  const [editing, setEditing] = useState<AiModelConfig | null>(null);
  const [draftKey, setDraftKey] = useState('');

  const update = useMutation({
    mutationFn: async ({ id, patch }: { id: string; patch: Partial<AiModelConfig> }) => (await updateAiConfig(id, patch)).data,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['ai-configs'] }),
  });

  function toggleActive(c: AiModelConfig) {
    update.mutate({ id: c.id, patch: { active: !c.active } });
    toast.success(`${providerLabel[c.provider]} ${c.active ? 'désactivé' : 'activé'}.`);
  }

  function setDefault(c: AiModelConfig) {
    update.mutate({ id: c.id, patch: { isDefault: true } });
    toast.success(`${providerLabel[c.provider]} défini comme fournisseur par défaut.`);
  }

  function openEdit(c: AiModelConfig) {
    setEditing(c);
    setDraftKey('');
  }

  function saveKey() {
    if (!editing) return;
    update.mutate(
      { id: editing.id, patch: { apiKeyRef: draftKey || editing.apiKeyRef, modelName: editing.modelName } },
      {
        onSuccess: () => {
          toast.success('Configuration enregistrée.');
          setEditing(null);
        },
      },
    );
  }

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Modèles IA" description="Configurez les fournisseurs d'IA disponibles pour la génération." icon={Sparkles} />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {isLoading
          ? Array.from({ length: 6 }).map((_, i) => <Skeleton key={i} className="h-40 w-full" />)
          : configs?.map((c: AiModelConfig) => (
              <Card key={c.id} className={cn('transition-opacity', !c.active && 'opacity-60')}>
                <CardHeader className="pb-3">
                  <div className="flex items-start justify-between">
                    <div className="flex items-center gap-2">
                      <span className={cn('flex h-9 w-9 items-center justify-center rounded-lg text-xs font-bold', providerColor[c.provider])}>
                        {providerLabel[c.provider].slice(0, 2).toUpperCase()}
                      </span>
                      <div>
                        <CardTitle className="text-base">{providerLabel[c.provider]}</CardTitle>
                        <CardDescription className="font-mono text-xs">{c.modelName}</CardDescription>
                      </div>
                    </div>
                    <Switch checked={c.active} onCheckedChange={() => toggleActive(c)} aria-label={`Activer ${providerLabel[c.provider]}`} />
                  </div>
                </CardHeader>
                <CardContent className="space-y-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="text-muted-foreground">Clé API</span>
                    <span className="font-mono">{c.apiKeyRef}</span>
                  </div>
                  <div className="flex items-center justify-between">
                    {c.isDefault ? (
                      <Badge variant="outline" className="border-accent/30 bg-accent/15 text-accent"><Star className="mr-1 h-3 w-3" /> Par défaut</Badge>
                    ) : (
                      <Button variant="ghost" size="sm" onClick={() => setDefault(c)} disabled={!c.active}>Définir par défaut</Button>
                    )}
                    <Button variant="outline" size="sm" onClick={() => openEdit(c)}>Configurer</Button>
                  </div>
                </CardContent>
              </Card>
            ))}
      </div>

      <Dialog open={!!editing} onOpenChange={(o) => !o && setEditing(null)}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2"><Sparkles className="h-5 w-5 text-accent" /> {editing ? providerLabel[editing.provider] : ''}</DialogTitle>
            <DialogDescription>Modifiez le modèle et la clé API. La clé est masquée pour des raisons de sécurité.</DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="model">Nom du modèle</Label>
              <Input id="model" defaultValue={editing?.modelName} onChange={(e) => editing && setEditing({ ...editing, modelName: e.target.value })} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="key">Clé API</Label>
              <Input id="key" type="password" placeholder={editing?.apiKeyRef} value={draftKey} onChange={(e) => setDraftKey(e.target.value)} />
              <p className="text-xs text-muted-foreground">Laissez vide pour conserver la clé actuelle ({editing?.apiKeyRef}).</p>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setEditing(null)}>Annuler</Button>
            <Button onClick={saveKey} disabled={update.isPending} className="bg-accent text-accent-foreground hover:bg-accent/90">
              {update.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Save className="mr-2 h-4 w-4" />} Enregistrer
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
