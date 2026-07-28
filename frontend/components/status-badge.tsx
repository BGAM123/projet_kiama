import { Badge } from '@/components/ui/badge';
import {
  documentTypeStatusLabel,
  documentTypeStatusTone,
  generatedStatuslabel,
  generatedStatusTone,
} from '@/lib/format';
import type { DocumentTypeStatus, GeneratedDocumentStatus } from '@/types';
import { cn } from '@/lib/utils';

export function DocumentTypeStatusBadge({ status }: { status: DocumentTypeStatus }) {
  return (
    <Badge variant="outline" className={cn('font-medium', documentTypeStatusTone[status])}>
      {documentTypeStatusLabel[status]}
    </Badge>
  );
}

export function GeneratedStatusBadge({ status }: { status: GeneratedDocumentStatus }) {
  return (
    <Badge variant="outline" className={cn('font-medium', generatedStatusTone[status])}>
      {generatedStatuslabel[status]}
    </Badge>
  );
}
