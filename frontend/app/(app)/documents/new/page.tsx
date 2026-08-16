'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { FilePlus2, FileUp, Loader2, Upload, X } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useDocumentTypes } from '@/lib/hooks/queries';
import { createDocument, importDocument } from '@/lib/api/client';
import type { DocumentType } from '@/types';

// Alignée sur le contrôle déjà en place pour l'import de Document Type
// (documents-types/import/page.tsx) et sur spring.servlet.multipart.max-file-size.
const MAX_FILE_SIZE_MB = 25;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;
const ALLOWED_FILE_PATTERN = /\.(docx?|pdf|md|txt)$/i;

/**
 * Point d'entrée de la rédaction, à deux vitesses :
 * - « Depuis un Document Type » : squelette d'un gabarit validé, sections
 *   vides à remplir (flux historique) ;
 * - « Importer un fichier » : un document déjà écrit (.docx/.pdf/.md/.txt)
 *   est converti et ouvert directement dans l'éditeur pour être repris — sans
 *   gabarit, pas de Document Type associé.
 * Les deux mènent au même éditeur type Word (`/documents/{id}`).
 */
export default function NewDocumentPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Nouveau document" description="Partez d'un Document Type ou importez un fichier existant." icon={FilePlus2} />

      <Tabs defaultValue="template">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="template">Depuis un Document Type</TabsTrigger>
          <TabsTrigger value="import">Importer un fichier</TabsTrigger>
        </TabsList>
        <TabsContent value="template" className="mt-4">
          <FromTemplateTab />
        </TabsContent>
        <TabsContent value="import" className="mt-4">
          <ImportFileTab />
        </TabsContent>
      </Tabs>
    </div>
  );
}

function FromTemplateTab() {
  const router = useRouter();
  const { data: documentTypes, isLoading } = useDocumentTypes();
  const [documentTypeId, setDocumentTypeId] = useState<string>('');

  const activeTypes = (documentTypes ?? []).filter((d: DocumentType) => d.status === 'ACTIF');

  const create = useMutation({
    mutationFn: async () => (await createDocument(documentTypeId)).data,
    onSuccess: (doc) => router.push(`/documents/${doc.id}`),
    onError: (e: Error) => toast.error('Impossible de créer le document', { description: e.message }),
  });

  return (
    <Card>
      <CardContent className="space-y-6 p-6">
        <div className="space-y-2">
          <Label htmlFor="new-doc-type">Document Type</Label>
          <Select value={documentTypeId} onValueChange={setDocumentTypeId}>
            <SelectTrigger id="new-doc-type" aria-label="Document Type">
              <SelectValue placeholder={isLoading ? 'Chargement…' : 'Sélectionnez un Document Type'} />
            </SelectTrigger>
            <SelectContent>
              {activeTypes.length === 0 && !isLoading ? (
                <div className="px-2 py-1.5 text-sm text-muted-foreground">Aucun Document Type actif.</div>
              ) : (
                activeTypes.map((d: DocumentType) => <SelectItem key={d.id} value={d.id}>{d.name}</SelectItem>)
              )}
            </SelectContent>
          </Select>
          <p className="text-xs text-muted-foreground">
            Seuls les Document Types validés (statut Actif) apparaissent ici.
          </p>
        </div>

        <Button
          className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
          disabled={!documentTypeId || create.isPending}
          onClick={() => create.mutate()}
        >
          {create.isPending ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Création…</> : 'Créer le document'}
        </Button>
      </CardContent>
    </Card>
  );
}

function ImportFileTab() {
  const router = useRouter();
  const [file, setFile] = useState<File | null>(null);
  const [dragging, setDragging] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

  const importMut = useMutation({
    mutationFn: async () => (await importDocument(file!)).data,
    onSuccess: (doc) => {
      toast.success('Document importé — vous pouvez le modifier.');
      router.push(`/documents/${doc.id}`);
    },
    onError: (e: Error) => toast.error("Impossible d'importer ce fichier", { description: e.message }),
  });

  function handleFiles(files: FileList | null) {
    if (!files || !files.length) return;
    const selected = files[0];
    if (!ALLOWED_FILE_PATTERN.test(selected.name)) {
      toast.error('Format non supporté', { description: 'Formats acceptés : .docx, .pdf, .md, .txt' });
      return;
    }
    if (selected.size > MAX_FILE_SIZE_BYTES) {
      toast.error('Fichier trop volumineux', { description: `La taille maximale autorisée est de ${MAX_FILE_SIZE_MB} Mo.` });
      return;
    }
    setFile(selected);
  }

  return (
    <Card>
      <CardContent className="space-y-4 p-6">
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
        <p className="text-xs text-muted-foreground">
          Formats acceptés : .docx, .pdf, .md, .txt — {MAX_FILE_SIZE_MB} Mo max. La mise en forme (styles, tableaux,
          images) n&apos;est conservée que pour les fichiers .docx.
        </p>

        <Button
          className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
          disabled={!file || importMut.isPending}
          onClick={() => importMut.mutate()}
        >
          {importMut.isPending
            ? <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> Import en cours…</>
            : <><FileUp className="mr-2 h-4 w-4" /> Importer et ouvrir dans l&apos;éditeur</>}
        </Button>
      </CardContent>
    </Card>
  );
}
