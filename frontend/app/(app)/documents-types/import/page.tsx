'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  ArrowLeft,
  CheckCircle2,
  FileUp,
  Loader2,
  Upload,
  X,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Progress } from '@/components/ui/progress';
import { useCategories } from '@/lib/hooks/queries';
import { importDocumentType } from '@/lib/api/client';
import { useAuth } from '@/lib/auth-store';
import type { Category } from '@/types';

// Alignée sur spring.servlet.multipart.max-file-size (application.yml) et le
// cahier des charges (25 Mo) — le texte affichait auparavant "10 Mo max" sans
// aucune validation JS réelle (écart 8.2 du rapport d'écarts).
const MAX_FILE_SIZE_MB = 25;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;

const schema = z.object({
  name: z.string().min(3, 'Le nom doit contenir au moins 3 caractères.'),
  description: z.string().optional(),
  categoryId: z.string().min(1, 'Sélectionnez une catégorie.'),
});

type FormValues = z.infer<typeof schema>;

const STEPS = [
  'Analyse du document…',
  'Détection des titres…',
  'Détection des tableaux…',
  'Détection des listes…',
  "Reconstruction de l'arborescence…",
];

export default function ImportDocumentTypePage() {
  const router = useRouter();
  const qc = useQueryClient();
  const hasRole = useAuth((s) => s.hasRole);
  const isAdmin = hasRole('ADMIN');
  const { data: categories } = useCategories();
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const [extracting, setExtracting] = useState(false);
  const [progress, setProgress] = useState(0);
  const [stepIndex, setStepIndex] = useState(0);
  const [done, setDone] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const { register, handleSubmit, formState: { errors }, setValue, watch } = useForm<FormValues>({
    resolver: zodResolver(schema),
    defaultValues: { name: '', description: '', categoryId: '' },
  });

  useEffect(() => {
    if (!isAdmin) router.replace('/access-denied');
  }, [isAdmin, router]);

  const importMut = useMutation({
    mutationFn: async (v: FormValues) =>
      (await importDocumentType({ file: file!, name: v.name, description: v.description, categoryId: v.categoryId })).data,
  });

  function handleFiles(files: FileList | null) {
    if (!files || !files.length) return;
    const f = files[0];
    const allowed = /\.(docx?|pdf|md|txt)$/i;
    if (!allowed.test(f.name)) {
      toast.error('Format non supporté', { description: 'Formats acceptés : .docx, .pdf, .md, .txt' });
      return;
    }
    if (f.size > MAX_FILE_SIZE_BYTES) {
      toast.error('Fichier trop volumineux', { description: `La taille maximale autorisée est de ${MAX_FILE_SIZE_MB} Mo.` });
      return;
    }
    setFile(f);
    if (!watch('name')) setValue('name', f.name.replace(/\.[^.]+$/, ''));
  }

  async function runExtraction(v: FormValues) {
    if (!file) return;
    setExtracting(true);
    setProgress(0);
    setStepIndex(0);

    // Import réel en un seul appel (POST /api/v1/document-types/import) :
    // upload + extraction du texte (Tika) + construction de la structure
    // (POI pour .docx, découpage en paragraphes pour pdf/txt/md/doc) +
    // création du Document Type côté serveur. L'animation des étapes ne
    // reflète plus des sous-appels séparés — elle tourne en parallèle de
    // l'unique requête pour donner un retour visuel pendant son exécution.
    const animateSteps = (async () => {
      for (let i = 0; i < STEPS.length; i++) {
        setStepIndex(i);
        await new Promise((r) => setTimeout(r, 450 + Math.random() * 300));
        setProgress(Math.round(((i + 1) / STEPS.length) * 100));
      }
    })();

    let created: Awaited<ReturnType<typeof importMut.mutateAsync>>;
    try {
      [created] = await Promise.all([importMut.mutateAsync(v), animateSteps]);
    } catch (e) {
      setExtracting(false);
      toast.error("Échec de l'import", { description: e instanceof Error ? e.message : undefined });
      return;
    }

    qc.invalidateQueries({ queryKey: ['document-types'] });
    setDone(true);
    if (created.status === 'ECHEC_EXTRACTION') {
      toast.error('Extraction de structure échouée', {
        description: 'Le fichier a été importé mais sa structure n\'a pas pu être extraite automatiquement. Corrigez-la manuellement ou relancez l\'extraction.',
      });
    } else {
      toast.success('Extraction terminée', { description: 'La structure a été extraite. Vérifiez-la avant activation.' });
    }
    setTimeout(() => router.push(`/documents-types/${created.id}/structure`), 900);
  }

  const onSubmit = handleSubmit(runExtraction);

  if (!isAdmin) return null;

  return (
    <div className="mx-auto max-w-3xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Importer un Document Type"
        description="Déposez un document existant pour en extraire automatiquement la structure."
        icon={FileUp}
        actions={
          <Button asChild variant="ghost" size="sm"><Link href="/documents-types"><ArrowLeft className="mr-2 h-4 w-4" /> Retour</Link></Button>
        }
      />

      <form onSubmit={onSubmit} className="space-y-6">
        {/* Drop zone */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Fichier source</CardTitle>
            <CardDescription>Formats acceptés : .docx, .pdf, .md, .txt — {MAX_FILE_SIZE_MB} Mo max.</CardDescription>
          </CardHeader>
          <CardContent>
            <div
              role="button"
              tabIndex={0}
              aria-label="Zone de dépôt de fichier"
              onClick={() => inputRef.current?.click()}
              onKeyDown={(e) => (e.key === 'Enter' || e.key === ' ') && inputRef.current?.click()}
              onDragOver={(e) => { e.preventDefault(); setDragging(true); }}
              onDragLeave={() => setDragging(false)}
              onDrop={(e) => { e.preventDefault(); setDragging(false); handleFiles(e.dataTransfer.files); }}
              className={`flex flex-col items-center justify-center gap-3 rounded-lg border-2 border-dashed px-6 py-12 text-center transition-colors ${
                dragging ? 'border-accent bg-accent/10' : 'border-border hover:border-accent/60 hover:bg-muted/30'
              }`}
            >
              <div className="flex h-12 w-12 items-center justify-center rounded-full bg-primary/10 text-primary">
                <Upload className="h-6 w-6" />
              </div>
              {file ? (
                <div className="flex items-center gap-2 text-sm">
                  <span className="font-medium">{file.name}</span>
                  <span className="text-muted-foreground">({(file.size / 1024).toFixed(0)} Ko)</span>
                  <button
                    type="button"
                    aria-label="Retirer le fichier"
                    className="ml-2 rounded-full p-1 text-muted-foreground hover:bg-destructive/10 hover:text-destructive"
                    onClick={(e) => { e.stopPropagation(); setFile(null); }}
                  >
                    <X className="h-4 w-4" />
                  </button>
                </div>
              ) : (
                <>
                  <p className="text-sm font-medium">Glissez-déposez votre fichier ici</p>
                  <p className="text-xs text-muted-foreground">ou cliquez pour parcourir</p>
                </>
              )}
              <input ref={inputRef} type="file" className="hidden" accept=".doc,.docx,.pdf,.md,.txt" onChange={(e) => handleFiles(e.target.files)} />
            </div>
          </CardContent>
        </Card>

        {/* Metadata */}
        <Card>
          <CardHeader>
            <CardTitle className="text-lg">Informations</CardTitle>
            <CardDescription>Décrivez ce Document Type pour le retrouver facilement.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">Nom du Document Type</Label>
              <Input id="name" placeholder="Ex. Rapport trimestriel Q3" {...register('name')} aria-invalid={!!errors.name} />
              {errors.name && <p className="text-xs text-destructive">{errors.name.message}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">Description (optionnelle)</Label>
              <Textarea id="description" rows={3} placeholder="À quoi sert ce document ?" {...register('description')} />
            </div>
            <div className="space-y-2">
              <Label htmlFor="categoryId">Catégorie</Label>
              <Select onValueChange={(v) => setValue('categoryId', v, { shouldValidate: true })}>
                <SelectTrigger id="categoryId" aria-label="Catégorie"><SelectValue placeholder="Sélectionnez une catégorie" /></SelectTrigger>
                <SelectContent>
                  {categories?.map((c: Category) => <SelectItem key={c.id} value={c.id}>{c.name}</SelectItem>)}
                </SelectContent>
              </Select>
              {errors.categoryId && <p className="text-xs text-destructive">{errors.categoryId.message}</p>}
            </div>
          </CardContent>
        </Card>

        {/* Extraction progress */}
        {(extracting || done) && (
          <Card>
            <CardHeader>
              <CardTitle className="flex items-center gap-2 text-lg">
                {done ? <><CheckCircle2 className="h-5 w-5 text-success" /> Extraction terminée</> : <><Loader2 className="h-5 w-5 animate-spin" /> Extraction en cours</>}
              </CardTitle>
            </CardHeader>
            <CardContent className="space-y-3">
              <Progress value={progress} aria-label={`Progression : ${progress}%`} />
              <ul className="space-y-1.5 text-sm">
                {STEPS.map((s, i) => (
                  <li key={s} className="flex items-center gap-2">
                    {i < stepIndex || done ? <CheckCircle2 className="h-4 w-4 text-success" /> : i === stepIndex ? <Loader2 className="h-4 w-4 animate-spin text-accent" /> : <div className="h-4 w-4 rounded-full border border-border" />}
                    <span className={i <= stepIndex || done ? 'text-foreground' : 'text-muted-foreground'}>{s}</span>
                  </li>
                ))}
              </ul>
            </CardContent>
          </Card>
        )}

        <div className="flex justify-end gap-3">
          <Button asChild variant="outline" type="button"><Link href="/documents-types">Annuler</Link></Button>
          <Button type="submit" disabled={!file || extracting || done} className="bg-accent text-accent-foreground hover:bg-accent/90">
            {extracting ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Extraction…</> : <><FileUp className="mr-2 h-4 w-4" /> Lancer l'extraction</>}
          </Button>
        </div>
      </form>
    </div>
  );
}
