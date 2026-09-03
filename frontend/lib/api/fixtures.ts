import type {
  AiModelConfig,
  Category,
  Conversation,
  DocumentStructure,
  DocumentType,
  Message,
  Notification,
  Permission,
  ReferenceDocument,
  Role,
  User,
} from '@/types';

export const permissions: Permission[] = [
  { id: 'p1', code: 'document_type:read', description: 'Consulter les Documents Types' },
  { id: 'p2', code: 'document_type:write', description: 'Créer et modifier les Documents Types' },
  { id: 'p3', code: 'generation:write', description: 'Lancer une génération documentaire' },
  { id: 'p4', code: 'user:manage', description: 'Gérer les utilisateurs' },
  { id: 'p5', code: 'role:manage', description: 'Gérer les rôles et permissions' },
  { id: 'p6', code: 'ai_config:manage', description: 'Configurer les modèles IA' },
  { id: 'p8', code: 'category:manage', description: 'Gérer les catégories' },
];

export const roles: Role[] = [
  {
    id: 'r1',
    name: 'ADMIN',
    description: 'Administrateur — accès complet à la configuration et la supervision.',
    permissions,
  },
  {
    id: 'r2',
    name: 'UTILISATEUR',
    description: 'Utilisateur — génère des documents à partir des Documents Types actifs.',
    permissions: [permissions[0], permissions[2]],
  },
];

export const users: User[] = [
  {
    id: 'u1',
    email: 'admin@docuai.io',
    firstName: 'Sophie',
    lastName: 'Lambert',
    active: true,
    roles: [roles[0]],
    createdAt: '2026-01-12T09:00:00Z',
  },
  {
    id: 'u2',
    email: 'user@docuai.io',
    firstName: 'Thomas',
    lastName: 'Moreau',
    active: true,
    roles: [roles[1]],
    createdAt: '2026-02-03T14:20:00Z',
  },
  {
    id: 'u3',
    email: 'claire.dubois@docuai.io',
    firstName: 'Claire',
    lastName: 'Dubois',
    active: true,
    roles: [roles[1]],
    createdAt: '2026-03-21T11:05:00Z',
  },
  {
    id: 'u4',
    email: 'marc.fournier@docuai.io',
    firstName: 'Marc',
    lastName: 'Fournier',
    active: false,
    roles: [roles[1]],
    createdAt: '2026-04-18T08:45:00Z',
  },
];

export const categories: Category[] = [
  { id: 'c1', name: 'Reporting', description: 'Rapports internes et indicateurs' },
  { id: 'c2', name: 'Commercial', description: 'Propositions et devis clients' },
  { id: 'c3', name: 'Juridique', description: 'Contrats et documents légaux' },
];

export const documentTypes: DocumentType[] = [
  {
    id: 'dt1',
    name: 'Rapport mensuel d\'activité',
    description: 'Synthèse mensuelle des résultats et actions pour la direction.',
    categoryId: 'c1',
    status: 'ACTIF',
    version: 3,
    createdAt: '2026-01-15T10:00:00Z',
  },
  {
    id: 'dt2',
    name: 'Proposition commerciale',
    description: 'Proposition commerciale standard adressée aux prospects.',
    categoryId: 'c2',
    status: 'ACTIF',
    version: 2,
    createdAt: '2026-02-10T10:00:00Z',
  },
  {
    id: 'dt3',
    name: 'Compte rendu de réunion',
    description: 'Compte rendu structuré avec décisions et actions à venir.',
    categoryId: 'c1',
    status: 'ACTIF',
    version: 1,
    createdAt: '2026-03-02T10:00:00Z',
  },
  {
    id: 'dt4',
    name: 'Contrat type de prestation',
    description: 'Contrat de prestation de services avec clauses standards.',
    categoryId: 'c3',
    status: 'EN_VALIDATION',
    version: 1,
    createdAt: '2026-07-20T10:00:00Z',
  },
];

export const structures: DocumentStructure[] = [
  {
    id: 's1',
    documentTypeId: 'dt1',
    hasToc: true,
    tree: [
      { id: 'n1', type: 'cover', label: 'Page de garde' },
      { id: 'n2', type: 'heading', level: 1, label: '1. Introduction', children: [
        { id: 'n2a', type: 'heading', level: 2, label: '1.1 Contexte' },
        { id: 'n2b', type: 'heading', level: 2, label: '1.2 Objectifs du mois' },
      ] },
      { id: 'n3', type: 'heading', level: 1, label: '2. Résultats du mois', children: [
        { id: 'n3a', type: 'table', label: 'Tableau : Indicateurs clés', columns: ['Indicateur', 'Objectif', 'Réalisé', 'Écart'] },
        { id: 'n3b', type: 'heading', level: 2, label: '2.1 Analyse des écarts' },
      ] },
      { id: 'n4', type: 'heading', level: 1, label: '3. Actions à venir', children: [
        { id: 'n4a', type: 'list', label: 'Liste des actions priorisées' },
      ] },
      { id: 'n5', type: 'heading', level: 1, label: '4. Conclusion' },
    ],
  },
  {
    id: 's2',
    documentTypeId: 'dt2',
    hasToc: false,
    tree: [
      { id: 'n10', type: 'cover', label: 'Page de garde' },
      { id: 'n11', type: 'heading', level: 1, label: '1. Présentation de l\'offre' },
      { id: 'n12', type: 'heading', level: 1, label: '2. Périmètre et livrables', children: [
        { id: 'n12a', type: 'table', label: 'Tableau : Livrables & planning', columns: ['Livrable', 'Délai', 'Responsable'] },
      ] },
      { id: 'n13', type: 'heading', level: 1, label: '3. Conditions financières', children: [
        { id: 'n13a', type: 'table', label: 'Tableau : Tarification', columns: ['Prestation', 'Quantité', 'Prix unitaire', 'Total'] },
      ] },
      { id: 'n14', type: 'heading', level: 1, label: '4. Conditions générales' },
    ],
  },
  {
    id: 's3',
    documentTypeId: 'dt3',
    hasToc: true,
    tree: [
      { id: 'n20', type: 'heading', level: 1, label: 'Informations générales' },
      { id: 'n21', type: 'heading', level: 1, label: 'Ordre du jour', children: [
        { id: 'n21a', type: 'list', label: 'Points abordés' },
      ] },
      { id: 'n22', type: 'heading', level: 1, label: 'Échanges et décisions' },
      { id: 'n23', type: 'heading', level: 1, label: 'Actions à venir', children: [
        { id: 'n23a', type: 'table', label: 'Tableau : Suivi des actions', columns: ['Action', 'Responsable', 'Échéance'] },
      ] },
    ],
  },
  {
    id: 's4',
    documentTypeId: 'dt4',
    hasToc: true,
    tree: [
      { id: 'n30', type: 'cover', label: 'Page de garde' },
      { id: 'n31', type: 'heading', level: 1, label: 'Article 1 — Objet du contrat' },
      { id: 'n32', type: 'heading', level: 1, label: 'Article 2 — Durée' },
      { id: 'n33', type: 'heading', level: 1, label: 'Article 3 — Rémunération', children: [
        { id: 'n33a', type: 'table', label: 'Tableau : Barème', columns: ['Phase', 'Montant HT', 'TVA', 'TTC'] },
      ] },
      { id: 'n34', type: 'heading', level: 1, label: 'Article 4 — Responsabilités' },
      { id: 'n35', type: 'heading', level: 1, label: 'Article 5 — Résiliation' },
    ],
  },
];

export const conversations: Conversation[] = [
  { id: 'cv1', userId: 'u2', documentTypeId: 'dt1', title: 'Rapport mensuel — Juin 2026', createdAt: '2026-07-02T09:15:00Z' },
  { id: 'cv2', userId: 'u2', documentTypeId: 'dt2', title: 'Proposition — Acme Corp', createdAt: '2026-07-08T13:40:00Z' },
  { id: 'cv3', userId: 'u2', documentTypeId: 'dt3', title: 'CR réunion comité pilotage', createdAt: '2026-07-12T16:20:00Z' },
  { id: 'cv4', userId: 'u1', documentTypeId: 'dt1', title: 'Rapport mensuel — Mai 2026', createdAt: '2026-06-03T09:00:00Z' },
  { id: 'cv5', userId: 'u3', documentTypeId: 'dt2', title: 'Proposition — Globex', createdAt: '2026-07-15T11:30:00Z' },
  { id: 'cv6', userId: 'u2', documentTypeId: 'dt3', title: 'CR réunion équipe produit', createdAt: '2026-07-19T15:00:00Z' },
];

export const messages: Message[] = [
  { id: 'm1', conversationId: 'cv1', role: 'assistant', content: 'Bonjour Thomas. Je vais vous aider à générer un Rapport mensuel d\'activité. Quels sont les chiffres clés de ce mois ?', createdAt: '2026-07-02T09:15:00Z' },
  { id: 'm2', conversationId: 'cv1', role: 'user', content: 'CA de 420k€, objectif 400k€. 12 nouveaux clients. NPS à 47.', createdAt: '2026-07-02T09:16:00Z' },
  { id: 'm3', conversationId: 'cv1', role: 'assistant', content: 'Parfait. Je prépare un rapport formel en français sur 8-10 pages. Cliquez sur « Générer le document » quand vous êtes prêt.', createdAt: '2026-07-02T09:16:30Z' },
  { id: 'm4', conversationId: 'cv2', role: 'assistant', content: 'Bonjour ! Pour la proposition à Acme Corp, quel périmètre souhaitez-vous proposer ?', createdAt: '2026-07-08T13:40:00Z' },
  { id: 'm5', conversationId: 'cv2', role: 'user', content: 'Mission d\'audit sur 6 semaines, 3 livrables.', createdAt: '2026-07-08T13:41:00Z' },
];

export const referenceDocuments: ReferenceDocument[] = [
  { id: 'rd1', conversationId: 'cv1', fileName: 'chiffres_juin_2026.xlsx', storagePath: '/refs/cv1/chiffres_juin_2026.xlsx', importedAt: '2026-07-02T09:14:00Z' },
  { id: 'rd2', conversationId: 'cv2', fileName: 'besoins_acme.pdf', storagePath: '/refs/cv2/besoins_acme.pdf', importedAt: '2026-07-08T13:39:00Z' },
];


export const aiModelConfigs: AiModelConfig[] = [
  { id: 'ai1', provider: 'OPENAI', modelName: 'gpt-4o', apiKeyRef: 'OPENAI_API_KEY', hasStoredApiKey: true, apiKeyPreview: '•••• a1f4', isDefault: true, active: true },
  { id: 'ai2', provider: 'CLAUDE', modelName: 'claude-sonnet-4', apiKeyRef: 'ANTHROPIC_API_KEY', hasStoredApiKey: true, apiKeyPreview: '•••• 88c2', isDefault: false, active: true },
  { id: 'ai3', provider: 'GEMINI', modelName: 'gemini-1.5-pro', apiKeyRef: 'GEMINI_API_KEY', hasStoredApiKey: false, apiKeyPreview: null, isDefault: false, active: true },
  { id: 'ai4', provider: 'MISTRAL', modelName: 'mistral-large', apiKeyRef: 'MISTRAL_API_KEY', hasStoredApiKey: false, apiKeyPreview: null, isDefault: false, active: false },
  { id: 'ai5', provider: 'OLLAMA', modelName: 'llama3.1:70b', apiKeyRef: '—', hasStoredApiKey: false, apiKeyPreview: null, isDefault: false, active: false },
  { id: 'ai6', provider: 'DEEPSEEK', modelName: 'deepseek-chat', apiKeyRef: 'DEEPSEEK_API_KEY', hasStoredApiKey: false, apiKeyPreview: null, isDefault: false, active: false },
];

export const notifications: Notification[] = [
  { id: 'n1', userId: 'u2', type: 'SUCCESS', content: 'Le document « Rapport mensuel — Juin 2026 » a été exporté en PDF.', read: true, createdAt: '2026-07-02T09:25:00Z' },
  { id: 'n2', userId: 'u2', type: 'INFO', content: 'Le Document Type « Contrat type de prestation » est en attente de validation.', read: false, createdAt: '2026-07-21T10:00:00Z' },
  { id: 'n3', userId: 'u2', type: 'WARNING', content: 'La génération « CR équipe produit » a échoué. Vous pouvez relancer.', read: false, createdAt: '2026-07-19T15:05:00Z' },
  { id: 'n4', userId: 'u1', type: 'INFO', content: 'Claire Dubois a créé une nouvelle conversation.', read: false, createdAt: '2026-07-15T11:31:00Z' },
];

export const demoCredentials = [
  { email: 'admin@docuai.io', password: 'demo1234', role: 'Administrateur' },
  { email: 'user@docuai.io', password: 'demo1234', role: 'Utilisateur' },
];

// Per-user password store (mock). Seeded with the demo password for existing accounts.
export const userPasswords: Record<string, string> = {
  u1: 'demo1234',
  u2: 'demo1234',
  u3: 'demo1234',
  u4: 'demo1234',
};
