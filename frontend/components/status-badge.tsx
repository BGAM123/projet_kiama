import { Badge } from '@/components/ui/badge';
import {
  documentTypeStatusLabel,
  documentTypeStatusTone,
  documentStatusLabel,
  documentStatusTone,
} from '@/lib/format';
import type { DocumentTypeStatus, DocumentStatus } from '@/types';
import { cn } from '@/lib/utils';

export function DocumentTypeStatusBadge({ status }: { status: DocumentTypeStatus }) {
  return (
    <Badge variant="outline" className={cn('font-medium', documentTypeStatusTone[status])}>
      {documentTypeStatusLabel[status]}
    </Badge>
  );
}

export function DocumentStatusBadge({ status }: { status: DocumentStatus }) {
  return (
    <Badge variant="outline" className={cn('font-medium', documentStatusTone[status])}>
      {documentStatusLabel[status]}
    </Badge>
  );
}
