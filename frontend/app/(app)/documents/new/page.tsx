'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { FilePlus2, FileUp, Loader2, Sparkles, Upload, X } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useDocumentTypes } from '@/lib/hooks/queries';
import { importDocument, generateDocument } from '@/lib/api/client';
import type { DocumentType } from '@/types';

// Alignée sur le contrôle déjà en place pour l'import de Document Type
// (documents-types/import/page.tsx) et sur spring.servlet.multipart.max-file-size.
const MAX_FILE_SIZE_MB = 25;
const MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024;
const ALLOWED_FILE_PATTERN = /\.(docx?|pdf|md|txt)$/i;
// Même seuil que côté serveur quand une description est fournie
// (GenerateDocumentRequest.description, @Size(min = 10)) — la description
// elle-même reste optionnelle.
const MIN_DESCRIPTION_LENGTH = 10;

/**
 * Point d'entrée de la rédaction, à deux vitesses :
 * - « Générer à partir d'un Document Type » : le plan d'un gabarit validé,
 *   optionnellement rempli par l'IA à partir d'une description — sans
 *   description, un squelette vide est créé (a remplacé l'ancien flux
 *   « Depuis un Document Type », qui ne faisait que ça) ;
 * - « Importer un fichier » : un document déjà écrit (.docx/.pdf/.md/.txt)
 *   est converti et ouvert directement dans l'éditeur pour être repris — sans
 *   gabarit, pas de Document Type associé.
 * Les deux mènent au même éditeur type Word (`/documents/{id}`).
 */
export default function NewDocumentPage() {
  return (
    <div className="mx-auto max-w-2xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Nouveau document" description="Générez un document à partir d'un Document Type — avec ou sans l'aide de l'IA — ou importez un fichier existant." icon={FilePlus2} />

      <Tabs defaultValue="generate">
        <TabsList className="grid w-full grid-cols-2">
          <TabsTrigger value="generate">Générer à partir d&apos;un document type</TabsTrigger>
          <TabsTrigger value="import">Importer un fichier</TabsTrigger>
        </TabsList>
        <TabsContent value="generate" className="mt-4">
          <GenerateFromTypeTab />
        </TabsContent>
        <TabsContent value="import" className="mt-4">
          <ImportFileTab />
        </TabsContent>
      </Tabs>
    </div>
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

/**
 * Le plan (titres, tableaux) vient du Document Type choisi — l'IA ne rédige
 * que le contenu de chaque section, jamais la structure. La description est
 * optionnelle : vide, le document est créé avec un squelette vierge (comme
 * l'ancien flux « Depuis un Document Type ») ; renseignée (10 caractères
 * minimum), un appel bloquant unique (JSON couvrant tout le document) génère
 * le contenu — plus lent qu'une simple création, mais plus cohérent qu'une
 * suite d'appels indépendants section par section. Le document est créé même
 * si l'IA échoue (repli sur le squelette vide — jamais d'erreur bloquante).
 */
function GenerateFromTypeTab() {
  const router = useRouter();
  const { data: documentTypes, isLoading } = useDocumentTypes();
  const [documentTypeId, setDocumentTypeId] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');

  const activeTypes = (documentTypes ?? []).filter((d: DocumentType) => d.status === 'ACTIF');
  const trimmedDescription = description.trim();
  const hasDescription = trimmedDescription.length > 0;
  const descriptionTooShort = hasDescription && trimmedDescription.length < MIN_DESCRIPTION_LENGTH;
  const canSubmit = !!documentTypeId && name.trim().length > 0 && !descriptionTooShort;

  const generateMut = useMutation({
    mutationFn: async () =>
      (await generateDocument({ documentTypeId, name: name.trim(), description: trimmedDescription || undefined })).data,
    onSuccess: (doc) => {
      toast.success(hasDescription ? 'Contenu généré — relisez-le avant de le finaliser.' : 'Document créé.');
      router.push(`/documents/${doc.id}`);
    },
    onError: (e: Error) => toast.error(hasDescription ? 'La génération a échoué' : 'Impossible de créer le document', { description: e.message }),
  });

  return (
    <Card>
      <CardContent className="space-y-4 p-6">
        <div className="space-y-2">
          <Label htmlFor="gen-doc-name">Nom du document</Label>
          <Input
            id="gen-doc-name"
            placeholder="Ex. Audit sécurité — siège social"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
        </div>

        <div className="space-y-2">
          <Label htmlFor="gen-doc-type">Document Type</Label>
          <Select value={documentTypeId} onValueChange={setDocumentTypeId}>
            <SelectTrigger id="gen-doc-type" aria-label="Document Type">
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
            Seuls les Document Types validés (statut Actif) apparaissent ici. L&apos;IA respecte leur plan — elle n&apos;en rédige que le contenu.
          </p>
        </div>

        <div className="space-y-2">
          <Label htmlFor="gen-doc-description">Ce que vous souhaitez obtenir (optionnel)</Label>
          <Textarea
            id="gen-doc-description"
            rows={5}
            placeholder="Ex. Un audit de la sécurité informatique du siège social, réalisé en mars 2026, avec trois constats critiques sur la gestion des accès et des recommandations priorisées."
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            aria-invalid={descriptionTooShort}
          />
          <p className={`text-xs ${descriptionTooShort ? 'text-destructive' : 'text-muted-foreground'}`}>
            {hasDescription
              ? `${trimmedDescription.length}/${MIN_DESCRIPTION_LENGTH} caractères minimum. Plus la description est précise, plus le contenu généré par l'IA sera pertinent.`
              : "Laissez vide pour créer un squelette vierge à partir du plan du Document Type, sans appel à l'IA."}
          </p>
        </div>

        <Button
          className="w-full bg-accent text-accent-foreground hover:bg-accent/90"
          disabled={!canSubmit || generateMut.isPending}
          onClick={() => generateMut.mutate()}
        >
          {generateMut.isPending ? (
            <><Loader2 className="mr-2 h-4 w-4 animate-spin" /> {hasDescription ? 'Génération en cours (jusqu’à une minute)…' : 'Création…'}</>
          ) : hasDescription ? (
            <><Sparkles className="mr-2 h-4 w-4" /> Générer le contenu</>
          ) : (
            'Créer le document'
          )}
        </Button>
      </CardContent>
    </Card>
  );
}
