'use client';

import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { KeyRound, Loader2, ShieldCheck } from 'lucide-react';
import { toast } from 'sonner';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { useAuth } from '@/lib/auth-store';
import { changePassword } from '@/lib/api/client';

const schema = z
  .object({
    current: z.string().min(1, 'Le mot de passe actuel est requis.'),
    next: z.string().min(8, 'Le nouveau mot de passe doit contenir au moins 8 caractères.'),
    confirm: z.string().min(1, 'Veuillez confirmer le nouveau mot de passe.'),
  })
  .refine((v) => v.next === v.confirm, {
    path: ['confirm'],
    message: 'Les deux mots de passe ne correspondent pas.',
  });

type FormValues = z.infer<typeof schema>;

export default function ChangePasswordPage() {
  const user = useAuth((s) => s.session?.user ?? null);
  const [serverError, setServerError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    formState: { errors, isSubmitting },
  } = useForm<FormValues>({ resolver: zodResolver(schema) });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    if (!user) return;
    try {
      await changePassword(user.id, values.current, values.next);
      toast.success('Mot de passe modifié', { description: 'Votre nouveau mot de passe est actif.' });
      reset({ current: '', next: '', confirm: '' });
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'La modification a échoué.';
      setServerError(msg);
    }
  }

  return (
    <div className="mx-auto max-w-xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Modifier mon mot de passe"
        description="Choisissez un nouveau mot de passe pour votre compte."
        icon={KeyRound}
      />

      <Card>
        <CardContent className="p-6">
          {user && (
            <div className="mb-5 flex items-center gap-3 rounded-md border border-border bg-muted/40 px-4 py-3">
              <ShieldCheck className="h-5 w-5 flex-none text-accent" />
              <div>
                <p className="text-sm font-medium">{user.firstName} {user.lastName}</p>
                <p className="text-xs text-muted-foreground">{user.email}</p>
              </div>
            </div>
          )}

          <form onSubmit={handleSubmit(onSubmit)} className="space-y-4" noValidate>
            <div className="space-y-2">
              <Label htmlFor="current">Mot de passe actuel</Label>
              <Input id="current" type="password" autoComplete="current-password" placeholder="••••••••" {...register('current')} aria-invalid={!!errors.current} />
              {errors.current && <p className="text-xs text-destructive">{errors.current.message}</p>}
            </div>

            <div className="space-y-2">
              <Label htmlFor="next">Nouveau mot de passe</Label>
              <Input id="next" type="password" autoComplete="new-password" placeholder="••••••••" {...register('next')} aria-invalid={!!errors.next} />
              {errors.next && <p className="text-xs text-destructive">{errors.next.message}</p>}
              <p className="text-xs text-muted-foreground">Minimum 8 caractères.</p>
            </div>

            <div className="space-y-2">
              <Label htmlFor="confirm">Confirmer le nouveau mot de passe</Label>
              <Input id="confirm" type="password" autoComplete="new-password" placeholder="••••••••" {...register('confirm')} aria-invalid={!!errors.confirm} />
              {errors.confirm && <p className="text-xs text-destructive">{errors.confirm.message}</p>}
            </div>

            {serverError && (
              <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                {serverError}
              </div>
            )}

            <Button type="submit" className="bg-accent text-accent-foreground hover:bg-accent/90" disabled={isSubmitting}>
              {isSubmitting ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Enregistrement…</> : 'Enregistrer le nouveau mot de passe'}
            </Button>
          </form>
        </CardContent>
      </Card>
    </div>
  );
}
