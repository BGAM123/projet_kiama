'use client';

import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import {
  AlertCircle,
  Eye,
  EyeOff,
  KeyRound,
  Loader2,
  Mail,
  Pencil,
  Power,
  Trash2,
  UserPlus,
  Users as UsersIcon,
} from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Badge } from '@/components/ui/badge';
import { Skeleton } from '@/components/ui/skeleton';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { Separator } from '@/components/ui/separator';
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
import { useUsers, useRoles } from '@/lib/hooks/queries';
import { createUser, deleteUser, updateUser } from '@/lib/api/client';
import { formatDate } from '@/lib/format';
import type { Role, User } from '@/types';

const createSchema = z.object({
  firstName: z.string().min(1, 'Prénom requis.'),
  lastName: z.string().min(1, 'Nom requis.'),
  email: z.string().email('E-mail invalide.'),
  password: z.string().min(8, 'Le mot de passe doit contenir au moins 8 caractères.'),
  role: z.enum(['ADMIN', 'UTILISATEUR']),
  active: z.boolean(),
});

const editSchema = z.object({
  firstName: z.string().min(1, 'Prénom requis.'),
  lastName: z.string().min(1, 'Nom requis.'),
  email: z.string().email('E-mail invalide.'),
  role: z.enum(['ADMIN', 'UTILISATEUR']),
  active: z.boolean(),
});

type CreateValues = z.infer<typeof createSchema>;
type EditValues = z.infer<typeof editSchema>;

export default function AdminUsersPage() {
  const { data: users, isLoading } = useUsers();
  // Rôles réels (UUID backend), pas la fixture 'r1'/'r2' — nécessaire pour
  // que roleIds envoyé à createUser/updateUser corresponde à des rôles
  // existants côté backend.
  const { data: allRoles = [] } = useRoles();
  const qc = useQueryClient();

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<User | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const [toDelete, setToDelete] = useState<User | null>(null);
  const [serverError, setServerError] = useState<string | null>(null);

  const createForm = useForm<CreateValues>({ resolver: zodResolver(createSchema) });
  const editForm = useForm<EditValues>({ resolver: zodResolver(editSchema) });

  const createMutation = useMutation({
    mutationFn: async (v: CreateValues) =>
      (
        await createUser({
          email: v.email,
          firstName: v.firstName,
          lastName: v.lastName,
          active: v.active,
          password: v.password,
          roles: allRoles.filter((r: Role) => r.name === v.role),
        })
      ).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setDialogOpen(false);
      toast.success('Utilisateur créé', {
        description: `Un e-mail avec les identifiants a été envoyé à ${createForm.getValues('email')}.`,
      });
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const editMutation = useMutation({
    mutationFn: async (v: EditValues) =>
      (
        await updateUser(editing!.id, {
          firstName: v.firstName,
          lastName: v.lastName,
          email: v.email,
          active: v.active,
          roles: allRoles.filter((r: Role) => r.name === v.role),
        })
      ).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setDialogOpen(false);
      toast.success('Utilisateur modifié');
    },
    onError: (e: Error) => setServerError(e.message),
  });

  const toggleMutation = useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => (await updateUser(id, { active })).data,
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['users'] }); toast.success('Statut mis à jour.'); },
  });

  const deleteMutation = useMutation({
    mutationFn: async (id: string) => (await deleteUser(id)).data,
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['users'] });
      setToDelete(null);
      toast.success('Utilisateur supprimé');
    },
  });

  function openCreate() {
    setEditing(null);
    setShowPassword(false);
    setServerError(null);
    createForm.reset({ firstName: '', lastName: '', email: '', password: '', role: 'UTILISATEUR', active: true });
    setDialogOpen(true);
  }

  function openEdit(u: User) {
    setEditing(u);
    setServerError(null);
    editForm.reset({
      firstName: u.firstName,
      lastName: u.lastName,
      email: u.email,
      role: u.roles.some((r) => r.name === 'ADMIN') ? 'ADMIN' : 'UTILISATEUR',
      active: u.active,
    });
    setDialogOpen(true);
  }

  const onCreateSubmit = createForm.handleSubmit((v) => { setServerError(null); createMutation.mutate(v); });
  const onEditSubmit = editForm.handleSubmit((v) => { setServerError(null); editMutation.mutate(v); });

  return (
    <div className="mx-auto max-w-7xl space-y-6 p-6 lg:p-8">
      <PageHeader
        title="Utilisateurs"
        description="Gérez les comptes et leur accès."
        icon={UsersIcon}
        actions={
          <Button onClick={openCreate}>
            <UserPlus className="mr-2 h-4 w-4" /> Nouvel utilisateur
          </Button>
        }
      />

      <Card>
        <CardContent className="p-0">
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="border-b border-border bg-muted/40 text-left text-xs uppercase tracking-wider text-muted-foreground">
                <tr>
                  <th className="px-4 py-3">Utilisateur</th>
                  <th className="px-4 py-3">Rôle</th>
                  <th className="px-4 py-3">Statut</th>
                  <th className="px-4 py-3">Créé le</th>
                  <th className="px-4 py-3 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border">
                {isLoading ? (
                  Array.from({ length: 4 }).map((_, i) => (
                    <tr key={i}><td colSpan={5} className="px-4 py-3"><Skeleton className="h-10 w-full" /></td></tr>
                  ))
                ) : !users?.length ? (
                  <tr><td colSpan={5} className="px-4 py-10 text-center text-muted-foreground">Aucun utilisateur.</td></tr>
                ) : (
                  users.map((u: User) => (
                    <tr key={u.id} className="hover:bg-muted/30">
                      <td className="px-4 py-3">
                        <div className="flex items-center gap-3">
                          <Avatar className="h-9 w-9">
                            <AvatarFallback className="bg-primary text-xs font-semibold text-primary-foreground">
                              {u.firstName[0]}{u.lastName[0]}
                            </AvatarFallback>
                          </Avatar>
                          <div>
                            <p className="font-medium">{u.firstName} {u.lastName}</p>
                            <p className="text-xs text-muted-foreground">{u.email}</p>
                          </div>
                        </div>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className={u.roles.some((r) => r.name === 'ADMIN') ? 'border-primary/30 bg-primary/10 text-primary' : 'border-border bg-muted text-muted-foreground'}>
                          {u.roles.some((r) => r.name === 'ADMIN') ? 'Administrateur' : 'Utilisateur'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3">
                        <Badge variant="outline" className={u.active ? 'border-success/30 bg-success/15 text-success' : 'border-border bg-muted text-muted-foreground'}>
                          {u.active ? 'Actif' : 'Désactivé'}
                        </Badge>
                      </td>
                      <td className="px-4 py-3 text-muted-foreground">{formatDate(u.createdAt)}</td>
                      <td className="px-4 py-3">
                        <div className="flex justify-end gap-1">
                          <Button variant="ghost" size="sm" onClick={() => openEdit(u)}>
                            <Pencil className="mr-1 h-3.5 w-3.5" /> Éditer
                          </Button>
                          <Button
                            variant="ghost"
                            size="sm"
                            className={u.active ? 'text-warning hover:bg-warning/10' : 'text-success hover:bg-success/10'}
                            disabled={toggleMutation.isPending}
                            onClick={() => toggleMutation.mutate({ id: u.id, active: !u.active })}
                          >
                            <Power className="mr-1 h-3.5 w-3.5" /> {u.active ? 'Désactiver' : 'Activer'}
                          </Button>
                          <Button variant="ghost" size="sm" className="text-destructive hover:bg-destructive/10" onClick={() => setToDelete(u)}>
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
              {editing ? <Pencil className="h-5 w-5 text-primary" /> : <UserPlus className="h-5 w-5 text-primary" />}
              {editing ? "Modifier l'utilisateur" : 'Nouvel utilisateur'}
            </DialogTitle>
            <DialogDescription>
              {editing
                ? 'Modifiez les informations du compte.'
                : "Renseignez les informations du compte. Le mot de passe sera envoyé à l'utilisateur par e-mail."}
            </DialogDescription>
          </DialogHeader>

          {editing ? (
            <form onSubmit={onEditSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="e-firstName">Prénom</Label>
                  <Input id="e-firstName" {...editForm.register('firstName')} aria-invalid={!!editForm.formState.errors.firstName} />
                  {editForm.formState.errors.firstName && <p className="text-xs text-destructive">{editForm.formState.errors.firstName.message}</p>}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="e-lastName">Nom</Label>
                  <Input id="e-lastName" {...editForm.register('lastName')} aria-invalid={!!editForm.formState.errors.lastName} />
                  {editForm.formState.errors.lastName && <p className="text-xs text-destructive">{editForm.formState.errors.lastName.message}</p>}
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="e-email">E-mail</Label>
                <Input id="e-email" type="email" {...editForm.register('email')} aria-invalid={!!editForm.formState.errors.email} />
                {editForm.formState.errors.email && <p className="text-xs text-destructive">{editForm.formState.errors.email.message}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="e-role">Rôle</Label>
                <select
                  id="e-role"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  {...editForm.register('role')}
                >
                  <option value="UTILISATEUR">Utilisateur</option>
                  <option value="ADMIN">Administrateur</option>
                </select>
              </div>
              <label className="flex items-center gap-3 rounded-md border border-border p-3 cursor-pointer hover:bg-muted/30">
                <input type="checkbox" {...editForm.register('active')} className="mt-0.5 h-4 w-4 rounded border-border" />
                <div>
                  <p className="text-sm font-medium">Compte actif</p>
                  <p className="text-xs text-muted-foreground">Un compte désactivé ne peut plus se connecter.</p>
                </div>
              </label>
              {serverError && (
                <div role="alert" className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  <AlertCircle className="h-4 w-4 flex-none" /> {serverError}
                </div>
              )}
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>Annuler</Button>
                <Button type="submit" disabled={editMutation.isPending}>
                  {editMutation.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Enregistrement…</> : 'Enregistrer'}
                </Button>
              </DialogFooter>
            </form>
          ) : (
            <form onSubmit={onCreateSubmit} className="space-y-4">
              <div className="grid grid-cols-2 gap-3">
                <div className="space-y-2">
                  <Label htmlFor="c-firstName">Prénom</Label>
                  <Input id="c-firstName" {...createForm.register('firstName')} aria-invalid={!!createForm.formState.errors.firstName} />
                  {createForm.formState.errors.firstName && <p className="text-xs text-destructive">{createForm.formState.errors.firstName.message}</p>}
                </div>
                <div className="space-y-2">
                  <Label htmlFor="c-lastName">Nom</Label>
                  <Input id="c-lastName" {...createForm.register('lastName')} aria-invalid={!!createForm.formState.errors.lastName} />
                  {createForm.formState.errors.lastName && <p className="text-xs text-destructive">{createForm.formState.errors.lastName.message}</p>}
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="c-email">E-mail</Label>
                <Input id="c-email" type="email" {...createForm.register('email')} aria-invalid={!!createForm.formState.errors.email} />
                {createForm.formState.errors.email && <p className="text-xs text-destructive">{createForm.formState.errors.email.message}</p>}
              </div>
              <div className="space-y-2">
                <Label htmlFor="c-password">Mot de passe initial</Label>
                <div className="relative">
                  <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
                  <Input
                    id="c-password"
                    type={showPassword ? 'text' : 'password'}
                    autoComplete="new-password"
                    placeholder="••••••••"
                    className="pl-9 pr-10"
                    {...createForm.register('password')}
                    aria-invalid={!!createForm.formState.errors.password}
                  />
                  <button
                    type="button"
                    aria-label={showPassword ? 'Masquer' : 'Afficher'}
                    onClick={() => setShowPassword((s) => !s)}
                    className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
                  >
                    {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
                  </button>
                </div>
                {createForm.formState.errors.password && <p className="text-xs text-destructive">{createForm.formState.errors.password.message}</p>}
                <div className="flex items-start gap-2 rounded-md border border-info/30 bg-info/5 px-3 py-2">
                  <Mail className="mt-0.5 h-3.5 w-3.5 flex-none text-info" />
                  <p className="text-xs text-muted-foreground">Ce mot de passe sera envoyé par e-mail à l'utilisateur. Il pourra le modifier depuis son compte.</p>
                </div>
              </div>
              <div className="space-y-2">
                <Label htmlFor="c-role">Rôle</Label>
                <select
                  id="c-role"
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                  {...createForm.register('role')}
                >
                  <option value="UTILISATEUR">Utilisateur</option>
                  <option value="ADMIN">Administrateur</option>
                </select>
              </div>
              {serverError && (
                <div role="alert" className="flex items-center gap-2 rounded-md border border-destructive/30 bg-destructive/10 px-3 py-2 text-sm text-destructive">
                  <AlertCircle className="h-4 w-4 flex-none" /> {serverError}
                </div>
              )}
              <DialogFooter>
                <Button type="button" variant="outline" onClick={() => setDialogOpen(false)}>Annuler</Button>
                <Button type="submit" disabled={createMutation.isPending}>
                  {createMutation.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Création…</> : 'Créer et envoyer'}
                </Button>
              </DialogFooter>
            </form>
          )}
        </DialogContent>
      </Dialog>

      {/* Delete confirmation */}
      <AlertDialog open={!!toDelete} onOpenChange={(o) => { if (!o) setToDelete(null); }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Supprimer cet utilisateur ?</AlertDialogTitle>
            <AlertDialogDescription>
              Cette action est définitive. {toDelete?.firstName} {toDelete?.lastName} ({toDelete?.email}) perdra l'accès à DocuAI et son compte sera retiré de la liste.
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
