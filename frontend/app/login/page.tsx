'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';

import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { toast } from 'sonner';
import { FileText, Loader2, Lock, Mail, Sparkles } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent } from '@/components/ui/card';
import { useAuth } from '@/lib/auth-store';

const schema = z.object({
  email: z.string().email('Adresse e-mail invalide.'),
  password: z.string().min(1, 'Le mot de passe est requis.'),
});

type FormValues = z.infer<typeof schema>;

export default function LoginPage() {
  const router = useRouter();
  const login = useAuth((s) => s.login);
  const loading = useAuth((s) => s.loading);
  const [serverError, setServerError] = useState<string | null>(null);

  const { register, handleSubmit, formState: { errors } } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { email: '', password: '' },
  });

  async function onSubmit(values: FormValues) {
    setServerError(null);
    try {
      await login(values.email, values.password);
      toast.success('Connexion réussie', { description: 'Bienvenue sur DocuAI.' });
      router.replace('/dashboard');
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Connexion impossible.';
      setServerError(msg);
    }
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background p-4">
      <div className="absolute inset-0 bg-grid opacity-40" aria-hidden />
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-primary/10" aria-hidden />

      <div className="relative grid w-full max-w-5xl gap-8 lg:grid-cols-2 lg:items-stretch">
        {/* Branding panel */}
        <div className="hidden flex-col justify-between rounded-xl border border-border/60 bg-gradient-to-br from-primary to-primary/85 p-10 text-primary-foreground shadow-xl lg:flex">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-primary-foreground/15 text-primary-foreground ring-1 ring-inset ring-primary-foreground/20">
              <FileText className="h-6 w-6" />
            </div>
            <span className="text-2xl font-semibold tracking-tight">DocuAI</span>
          </div>
          <div className="space-y-4">
            <h1 className="text-3xl font-semibold leading-tight">
              Générez des documents professionnels, conformes à votre charte.
            </h1>
            <p className="text-primary-foreground/80">
              Importez vos documents existants ou décrivez-les en langage naturel pour en obtenir le squelette, puis rédigez chaque section vous-même — avec l'IA comme assistant de reformulation à la demande.
            </p>
            <ul className="space-y-2 text-sm text-primary-foreground/85">
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-primary-foreground/70" /> Extraction ou génération IA de la structure documentaire</li>
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-primary-foreground/70" /> Rédaction manuelle assistée, section par section</li>
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-primary-foreground/70" /> Édition WYSIWYG & export DOCX / PDF / Markdown</li>
            </ul>
          </div>
        </div>

        {/* Login form */}
        <Card className="flex flex-col justify-center shadow-lg">
          <CardContent className="p-8 sm:p-10">
            <div className="mb-6 flex items-center gap-2 lg:hidden">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-primary text-primary-foreground">
                <FileText className="h-5 w-5" />
              </div>
              <span className="text-xl font-semibold tracking-tight">DocuAI</span>
            </div>
            <h2 className="text-2xl font-semibold tracking-tight">Connexion</h2>
            <p className="mt-1 text-sm text-muted-foreground">Accédez à votre espace de génération documentaire.</p>

            <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
              <div className="space-y-2">
                <Label htmlFor="email">Adresse e-mail</Label>
                <div className="relative">
                  <Mail className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input id="email" type="email" autoComplete="email" placeholder="vous@entreprise.fr" className="pl-9" {...register('email')} aria-invalid={!!errors.email} />
                </div>
                {errors.email && <p className="text-xs text-destructive">{errors.email.message}</p>}
              </div>

              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label htmlFor="password">Mot de passe</Label>
                  <button type="button" className="text-xs text-muted-foreground underline-offset-4 hover:underline" onClick={() => toast.info('Démo — fonctionnalité non activée.')}>
                    Mot de passe oublié ?
                  </button>
                </div>
                <div className="relative">
                  <Lock className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input id="password" type="password" autoComplete="current-password" placeholder="••••••••" className="pl-9" {...register('password')} aria-invalid={!!errors.password} />
                </div>
                {errors.password && <p className="text-xs text-destructive">{errors.password.message}</p>}
              </div>

              {serverError && (
                <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  {serverError}
                </div>
              )}

              <Button type="submit" className="w-full" disabled={loading}>
                {loading ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Connexion…</> : 'Se connecter'}
              </Button>
            </form>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
