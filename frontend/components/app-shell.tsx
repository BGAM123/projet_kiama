'use client';

import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';
import { useMemo, useState } from 'react';
import {
  Bell,
  Bot,
  FileText,
  FolderTree,
  History,
  KeyRound,
  LayoutDashboard,
  ListChecks,
  LogOut,
  Menu,
  Settings,
  Sparkles,
  Users,
  X,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import { useAuth } from '@/lib/auth-store';
import { useNotifications } from '@/lib/hooks/queries';
import type { Notification } from '@/types';
import { Button } from '@/components/ui/button';
import { NotificationsBell } from '@/components/notifications-bell';
import { ThemeToggle } from '@/components/theme-toggle';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { toast } from 'sonner';

interface NavItem {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
  requireAdmin?: boolean;
}

const navItems: NavItem[] = [
  { href: '/dashboard', label: 'Tableau de bord', icon: LayoutDashboard },
  { href: '/documents-types', label: 'Documents Types', icon: FileText },
  { href: '/documents/new', label: 'Nouveau document', icon: Bot },
  { href: '/history', label: 'Historique', icon: History },
  { href: '/notifications', label: 'Notifications', icon: Bell },
  { href: '/admin/categories', label: 'Catégories', icon: FolderTree, requireAdmin: true },
  { href: '/admin/users', label: 'Utilisateurs', icon: Users, requireAdmin: true },
  { href: '/admin/ai-models', label: 'Modèles IA', icon: Sparkles, requireAdmin: true },
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const session = useAuth((s) => s.session);
  const logout = useAuth((s) => s.logout);
  const hasRole = useAuth((s) => s.hasRole);
  const [mobileOpen, setMobileOpen] = useState(false);

  const items = useMemo(
    () => navItems.filter((i) => !i.requireAdmin || hasRole('ADMIN')),
    [hasRole],
  );

  const { data: notifications } = useNotifications();
  const unreadCount = useMemo(
    () => (notifications ?? []).filter((n: Notification) => !n.read).length,
    [notifications],
  );

  if (!session) return null;
  const user = session.user;
  const initials = `${user.firstName[0] ?? ''}${user.lastName[0] ?? ''}`.toUpperCase();
  const isAdmin = hasRole('ADMIN');

  function handleLogout() {
    logout();
    toast.success('Déconnexion réussie');
    router.replace('/login');
  }

  const Sidebar = (
    <aside className="flex h-full w-64 flex-col border-r border-border bg-card">
      <div className="flex h-16 items-center gap-2.5 border-b border-border px-5">
        <div className="flex h-9 w-9 flex-none items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary/80 text-primary-foreground shadow-sm">
          <FileText className="h-5 w-5" />
        </div>
        <div className="leading-tight">
          <p className="text-base font-semibold tracking-tight text-foreground">DocuAI</p>
          <p className="text-[11px] text-muted-foreground">Génération documentaire</p>
        </div>
      </div>
      <nav className="flex-1 space-y-0.5 overflow-y-auto p-3 scrollbar-thin">
        {items.map((item) => {
          const active = pathname === item.href || (item.href !== '/dashboard' && pathname.startsWith(item.href));
          const Icon = item.icon;
          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={() => setMobileOpen(false)}
              className={cn(
                'group flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all',
                active
                  ? 'bg-primary text-primary-foreground shadow-sm'
                  : 'text-muted-foreground hover:bg-accent hover:text-accent-foreground',
              )}
            >
              <Icon
                className={cn(
                  'h-4 w-4 flex-none transition-colors',
                  active ? 'text-primary-foreground' : 'text-muted-foreground group-hover:text-accent-foreground',
                )}
              />
              <span className="flex-1">{item.label}</span>
              {item.href === '/notifications' && unreadCount > 0 && (
                <span
                  className={cn(
                    'flex h-5 min-w-5 flex-none items-center justify-center rounded-full px-1 text-[11px] font-bold',
                    active ? 'bg-primary-foreground/20 text-primary-foreground' : 'bg-destructive text-destructive-foreground',
                  )}
                >
                  {unreadCount}
                </span>
              )}
            </Link>
          );
        })}
      </nav>
      <div className="border-t border-border p-3">
        <div className="flex items-center gap-2 rounded-lg bg-accent/60 px-3 py-2.5">
          <ListChecks className="h-4 w-4 flex-none text-primary" />
          <span className="text-xs font-medium text-accent-foreground">{isAdmin ? 'Espace administrateur' : 'Espace utilisateur'}</span>
        </div>
      </div>
    </aside>
  );

  return (
    <div className="flex h-screen overflow-hidden bg-background">
      {/* Desktop sidebar */}
      <div className="hidden lg:block">{Sidebar}</div>

      {/* Mobile sidebar */}
      {mobileOpen && (
        <div className="fixed inset-0 z-50 lg:hidden">
          <div className="absolute inset-0 bg-foreground/40 backdrop-blur-[2px]" onClick={() => setMobileOpen(false)} />
          <div className="absolute left-0 top-0 h-full animate-slide-in-right shadow-2xl">{Sidebar}</div>
        </div>
      )}

      {/* Main */}
      <div className="flex flex-1 flex-col overflow-hidden">
        <header className="sticky top-0 z-10 flex h-16 flex-none items-center justify-between gap-3 border-b border-border bg-card/95 px-4 backdrop-blur supports-[backdrop-filter]:bg-card/80 lg:px-6">
          <div className="flex items-center gap-3">
            <Button variant="ghost" size="icon" className="lg:hidden" aria-label="Ouvrir le menu" onClick={() => setMobileOpen(true)}>
              <Menu className="h-5 w-5" />
            </Button>
            <div className="hidden items-center gap-2 sm:flex">
              <span className="text-sm text-muted-foreground">
                Bonjour, <span className="font-medium text-foreground">{user.firstName}</span>
              </span>
            </div>
          </div>
          <div className="flex items-center gap-1">
            <NotificationsBell />
            <ThemeToggle />
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="ml-1 flex items-center gap-2 rounded-full outline-none ring-offset-background transition-shadow focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2" aria-label="Menu du profil">
                  <Avatar className="h-9 w-9 border-2 border-primary/15">
                    <AvatarFallback className="bg-primary text-xs font-semibold text-primary-foreground">
                      {initials}
                    </AvatarFallback>
                  </Avatar>
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-56">
                <DropdownMenuLabel>
                  <div className="flex flex-col">
                    <span className="text-sm font-medium">{user.firstName} {user.lastName}</span>
                    <span className="text-xs font-normal text-muted-foreground">{user.email}</span>
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="cursor-pointer" onClick={() => router.push('/mon-compte/mot-de-passe')}>
                  <KeyRound className="mr-2 h-4 w-4" /> Modifier mon mot de passe
                </DropdownMenuItem>
                <DropdownMenuItem className="cursor-pointer" onClick={handleLogout}>
                  <LogOut className="mr-2 h-4 w-4" /> Se déconnecter
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </header>
        <main className="flex-1 overflow-y-auto scrollbar-thin">{children}</main>
      </div>
    </div>
  );
}
