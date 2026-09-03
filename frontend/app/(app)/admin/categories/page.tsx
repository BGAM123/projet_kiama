'use client';

import { useState, useMemo } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertCircle,
  FolderTree,
  Layers,
  Loader2,
  Pencil,
  Plus,
  Trash2,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { useCategories, useDocumentTypes } from '@/lib/hooks/queries';
import { createCategory, deleteCategory, updateCategory } from '@/lib/api/client';
import type { Category, DocumentType } from '@/types';

const schema = z.object({
  name: z.string().min(1, 'Nom requis.'),
  description: z.string().optional(),
});

type FormValues = z.infer<typeof schema>;

export default function AdminCategoriesPage() {
  const { data: categories, isLoading } = useCategories();
  // Sert uniquement à afficher le nombre de Documents Types rattachés à
  // chaque catégorie (le backend refuse de toute façon la suppression d'une
  // catégorie encore utilisée — cf. CategoryController — mais l'afficher
  // évite à l'admin de découvrir le blocage seulement après coup).
  const { data: documentTypes } = useDocumentTypes();
  const qc = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<Category | null>(null);
  const [toDelete, setToDelete] = useState<Category | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const form = useForm<FormValues>({ resolver: zodResolver(schema), defaultValues: { name: '', description: '' } });

  const createMutation = useMutation({
    mutationFn: async (v: FormValues) => (await createCategory({ name: v.name, description: v.description ?? '' })).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      setDialogOpen(false);
      toast.success('Catégorie créée');
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const editMutation = useMutation({
    mutationFn: async (v: FormValues) => (await updateCategory(editing!.id, { name: v.name, description: v.description ?? '' })).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      setDialogOpen(false);
      toast.success('Catégorie modifiée');
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => (await deleteCategory(id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categories'] });
      setToDelete(null);
      toast.success('Catégorie supprimée');
    },
    onError: (e: Error) => {
      // Ex. CATEGORY_HAS_DOCUMENT_TYPES si un Document Type y est encore
      // rattaché — le backend refuse la suppression (cf. CategoryController).
      toast.error('Suppression impossible', { description: e.message });
      setToDelete(null);
    },
  });

  function openCreate() {
    setEditing(null);
    setServerError(null);
    form.reset({ name: '', description: '' });
    setDialogOpen(true);
  }

  function openEdit(c: Category) {
    setEditing(c);
    setServerError(null);
    form.reset({ name: c.name, description: c.description });
    setDialogOpen(true);
  }

  const onSubmit = form.handleSubmit((v) => {
    setServerError(null);
    if (editing) editMutation.mutate(v);
    else createMutation.mutate(v);
  });

  const documentTypeCountByCategory = useMemo(() => {
    const counts = new Map<string, number>();
    for (const d of documentTypes ?? []) {
      counts.set(d.categoryId, (counts.get(d.categoryId) ?? 0) + 1);
    }
    return counts;
  }, [documentTypes]);
  const countFor = (categoryId: string) => documentTypeCountByCategory.get(categoryId) ?? 0;
  const pending = createMutation.isPending || editMutation.isPending;

  return (
    <div className="mx-auto max-w-5xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Catégories"
        description="Référentiel des catégories utilisées pour classer les Documents Types."
        icon={FolderTree}
        actions={
          <Button onClick={openCreate}>
            <Plus className="mr-2 h-4 w-4" /> Nouvelle catégorie
          </Button>
        }
      />

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Nom</th>
                  <th className="px-4 py-3">Description</th>
                  <th className="px-4 py-3">Documents Types</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i}><td colSpan={4} className="px-4 py-3"><Skeleton className="h-10 w-full" /></td></tr>
                  ))
                ) : !categories?.length ? (
                  <tr><td colSpan={4} className="px-4 py-10 text-center text-muted-foreground">Aucune catégorie.</td></tr>
                ) : (
                  categories.map((c: Category) => (
                    <tr key={c.id} className="hover:bg-muted/30">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <div className="flex h-9 w-9 flex-none items-center justify-center rounded-md bg-primary/10 text-primary">
                            <Layers className="h-4 w-4" />
                          </div>
                          <p className="font-medium">{c.name}</p>
                        </div>
                      </td>
                      <td className="max-w-sm truncate px-4 py-3 text-muted-foreground">{c.description || '—'}</td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className="border-border bg-muted text-muted-foreground">
                          {countFor(c.id)}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="sm" onClick={() => openEdit(c)}>
                            <Pencil className="mr-1 h-3.5 w-3.5" /> Éditer
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className="text-destructive hover:bg-destructive/10"
                            onClick={() => setToDelete(c)}
                          >
                            <Trash2 className="mr-1 h-3.5 w-3.5" /> Supprimer
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Create / Edit dialog */}
      <Dialog open={dialogOpen} onOpenChange={(o) => { setDialogOpen(o); if (!o) setServerError(null); }}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              {editing ? <Pencil className="h-5 w-5 text-primary" /> : <Plus className="h-5 w-5 text-primary" />}
              {editing ? 'Modifier la catégorie' : 'Nouvelle catégorie'}
            </DialogTitle>
            <DialogDescription>
              {editing ? 'Modifiez le nom ou la description de cette catégorie.' : 'Créez une catégorie pour classer les Documents Types.'}
            </DialogDescription>
          </DialogHeader>
          <form onSubmit={onSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label htmlFor="name">Nom</Label>
              <Input id="name" placeholder="Ex. Rapports financiers" {...form.register('name')} aria-invalid={!!form.formState.errors.name} />
              {form.formState.errors.name && <p className="text-xs text-destructive">{form.formState.errors.name.message}</p>}
            </div>
            <div className="space-y-2">
              <Label htmlFor="description">Description (optionnelle)</Label>
              <Textarea id="description" rows={3} placeholder="À quoi sert cette catégorie ?" {...form.register('description')} />
            </div>
            {serverError && (
              <div role="alert" className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                <AlertCircle className="h-4 w-4 flex-none" /> {serverError}
              </div>
            )}
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>Annuler</Button>
              <Button type="submit" disabled={pending}>
                {pending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Enregistrement…</> : 'Enregistrer'}
              </Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <AlertDialog open={!!toDelete} onOpenChange={(o) => { if (!o) setToDelete(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Supprimer cette catégorie ?</AlertDialogTitle>
            <AlertDialogDescription>
              Cette action est définitive. La suppression sera refusée si un Document Type est encore rattaché à
              « {toDelete?.name} ».
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Annuler</AlertDialogCancel>
            <AlertDialogAction
              className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
              disabled={deleteMutation.isPending}
              onClick={() => toDelete && deleteMutation.mutate(toDelete.id)}
            >
              {deleteMutation.isPending ? <Loader2 className="mr-2 h-4 w-4 animate-spin" /> : <Trash2 className="mr-2 h-4 w-4" />}
              Supprimer définitivement
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
