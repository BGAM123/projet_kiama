'use client';

import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { useMutation } from '@tanstack/react-query';
import { toast } from 'sonner';
import { FilePlus2, Loader2 } from 'lucide-react';
import { PageHeader } from '@/components/page-header';
import { Card, CardContent } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { useDocumentTypes } from '@/lib/hooks/queries';
import { createDocument } from '@/lib/api/client';
import type { DocumentType } from '@/types';

/**
 * Point d'entrée du flux de rédaction manuelle assistée (remplace l'ancien
 * "Nouvelle conversation") : choix d'un Document Type actif, puis création
 * du document (sections vides initialisées depuis son squelette côté
 * serveur) avant redirection vers l'éditeur par section.
 */
export default function NewDocumentPage() {
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
    <div className="mx-auto max-w-2xl space-y-6 p-6 lg:p-8">
      <PageHeader title="Nouveau document" description="Choisissez un Document Type pour commencer la rédaction." icon={FilePlus2} />

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
    </div>
  );
}
