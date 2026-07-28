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
import { Separator } from '@/components/ui/separator';
import { useAuth } from '@/lib/auth-store';
import { demoCredentials } from '@/lib/api/fixtures';

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

  const { register, handleSubmit, formState: { errors }, setValue } = useForm<FormValues>({
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

  function fillDemo(email: string) {
    setValue('email', email);
    setValue('password', demoCredentials[0].password);
  }

  return (
    <div className="relative flex min-h-screen items-center justify-center overflow-hidden bg-background p-4">
      <div className="absolute inset-0 bg-grid opacity-40" aria-hidden />
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 via-transparent to-accent/10" aria-hidden />

      <div className="relative grid w-full max-w-5xl gap-8 lg:grid-cols-2 lg:items-stretch">
        {/* Branding panel */}
        <div className="hidden flex-col justify-between rounded-xl border border-border/60 bg-primary p-10 text-primary-foreground lg:flex">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-accent text-accent-foreground">
              <FileText className="h-6 w-6" />
            </div>
            <span className="text-2xl font-semibold tracking-tight">DocuAI</span>
          </div>
          <div className="space-y-4">
            <h1 className="text-3xl font-semibold leading-tight">
              Générez des documents professionnels, conformes à votre charte.
            </h1>
            <p className="text-primary-foreground/80">
              Importez vos documents existants, laissez l'IA en extraire la structure, puis produisez en quelques minutes de nouveaux rapports, propositions et comptes rendus via une interface de chat.
            </p>
            <ul className="space-y-2 text-sm text-primary-foreground/85">
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-accent" /> Extraction automatique de la structure documentaire</li>
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-accent" /> Génération IA section par section, en streaming</li>
              <li className="flex items-center gap-2"><Sparkles className="h-4 w-4 text-accent" /> Édition WYSIWYG & export DOCX / PDF / Markdown</li>
            </ul>
          </div>
          <p className="text-xs text-primary-foreground/60">Demo — données simulées, aucune donnée réelle n'est traitée.</p>
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

            <div className="my-6 flex items-center gap-3">
              <Separator className="flex-1" />
              <span className="text-xs uppercase tracking-wider text-muted-foreground">Comptes de démo</span>
              <Separator className="flex-1" />
            </div>

            <div className="space-y-2">
              {demoCredentials.map((c) => (
                <button
                  key={c.email}
                  type="button"
                  onClick={() => fillDemo(c.email)}
                  className="flex w-full items-center justify-between rounded-md border border-border bg-muted/40 px-3 py-2 text-left text-sm transition-colors hover:border-accent hover:bg-accent/10"
                >
                  <div>
                    <p className="font-medium">{c.email}</p>
                    <p className="text-xs text-muted-foreground">{c.role} · mot de passe : {c.password}</p>
                  </div>
                  <span className="text-xs text-accent">Utiliser</span>
                </button>
              ))}
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  );
}
