'use client';

import Link from 'next/link';
import { Button } from '@/components/ui/button';
import { ShieldAlert } from 'lucide-react';
import { useAuth } from '@/lib/auth-store';

export default function AccessDeniedPage() {
  const session = useAuth((s) => s.session);
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-background p-6 text-center">
      <div className="flex h-14 w-14 items-center justify-center rounded-full bg-destructive/10 text-destructive">
        <ShieldAlert className="h-7 w-7" />
      </div>
      <h1 className="mt-4 text-2xl font-semibold">Accès refusé</h1>
      <p className="mt-2 max-w-md text-muted-foreground">
        Votre compte ({session?.user.email}) ne dispose pas des permissions nécessaires pour accéder à cet espace.
      </p>
      <div className="mt-6 flex gap-3">
        <Button asChild>
          <Link href="/dashboard">Retour au tableau de bord</Link>
        </Button>
      </div>
    </div>
  );
}
