#!/usr/bin/env node
/**
 * Génère une nouvelle paire de clés RSA (2048 bits) pour la signature JWT
 * RS256 du backend DocuAI, et remplace DOCUAI_JWT_PRIVATE_KEY /
 * DOCUAI_JWT_PUBLIC_KEY dans backend/.env — même rôle que la paire actuelle
 * (lue par PemKeyReader / JwtKeyConfig, docuai-security).
 *
 * Pourquoi Node plutôt que openssl : Node est déjà requis pour le frontend de
 * ce projet (donc disponible partout où le dépôt est cloné, y compris sous
 * Windows sans Git Bash/WSL), et son module `crypto` intégré génère
 * directement le bon format sans dépendance externe :
 *   - clé privée en PKCS#8 ("-----BEGIN PRIVATE KEY-----")
 *   - clé publique en X.509/SPKI ("-----BEGIN PUBLIC KEY-----")
 * … exactement ce que PemKeyReader attend (voir sa Javadoc : le format
 * PKCS#1 produit par `openssl genrsa` n'est PAS supporté nativement par
 * java.security.KeyFactory).
 *
 * Usage :
 *   node backend/scripts/generate-jwt-keys.js
 *   node backend/scripts/generate-jwt-keys.js --target ../.env --bits 4096
 *   node backend/scripts/generate-jwt-keys.js --no-pem-files
 *
 * Note : l'option s'appelle --target (pas --env-file) volontairement — Node.js
 * définit lui-même un flag natif --env-file (chargement d'un .env au
 * démarrage) qui intercepte silencieusement tout argument de ce nom, même
 * placé après le script, et empêche le script de le recevoir.
 *
 * Effet de bord important : toute nouvelle paire de clés invalide
 * immédiatement tous les JWT (access + refresh) déjà émis avec l'ancienne
 * paire — tous les utilisateurs connectés devront se reconnecter.
 */

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

function parseArgs(argv) {
  const args = { bits: 2048, envFile: path.join(__dirname, '..', '.env'), pemFiles: true };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--bits') args.bits = parseInt(argv[++i], 10);
    else if (a === '--target' || a === '-t') args.envFile = path.resolve(argv[++i]);
    else if (a === '--no-pem-files') args.pemFiles = false;
    else if (a === '--help' || a === '-h') args.help = true;
  }
  return args;
}

function printHelp() {
  console.log(`Génère une nouvelle paire de clés RSA pour le JWT RS256 du backend DocuAI.

Options :
  --bits <n>         Taille de la clé en bits (défaut : 2048)
  --target, -t <path> Fichier .env à mettre à jour (défaut : backend/.env)
  --no-pem-files      Ne pas écrire jwt_private.pem / jwt_public.pem à côté du script
  -h, --help          Affiche cette aide
`);
}

// Convertit un PEM multi-lignes en une seule ligne avec des "\n" littéraux,
// le format attendu dans .env par PemKeyReader (qui fait pem.replace('\\n', '\n')
// avant de décoder le base64).
function pemToEnvLine(pem) {
  return pem.trim().split(/\r?\n/).join('\\n');
}

function generateKeyPair(bits) {
  return crypto.generateKeyPairSync('rsa', {
    modulusLength: bits,
    publicKeyEncoding: { type: 'spki', format: 'pem' },
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  });
}

function upsertEnvVar(envContent, key, value) {
  const line = `${key}=${value}`;
  const pattern = new RegExp(`^${key}=.*$`, 'm');
  if (pattern.test(envContent)) {
    return envContent.replace(pattern, line);
  }
  const separator = envContent.length > 0 && !envContent.endsWith('\n') ? '\n' : '';
  return `${envContent}${separator}${line}\n`;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  if (args.help) {
    printHelp();
    return;
  }

  console.log(`Génération d'une paire de clés RSA-${args.bits} (RS256)...`);
  const { publicKey, privateKey } = generateKeyPair(args.bits);

  const privateEnvValue = pemToEnvLine(privateKey);
  const publicEnvValue = pemToEnvLine(publicKey);

  // 1. Fichier .env : on part de l'existant s'il existe, sinon de
  //    .env.example (pour garder toutes les autres variables déjà
  //    documentées), sinon d'un fichier vide.
  let envContent = '';
  if (fs.existsSync(args.envFile)) {
    envContent = fs.readFileSync(args.envFile, 'utf8');
  } else {
    const exampleFile = path.join(path.dirname(args.envFile), '.env.example');
    if (fs.existsSync(exampleFile)) {
      console.log(`${args.envFile} n'existe pas encore — initialisé depuis ${exampleFile}.`);
      envContent = fs.readFileSync(exampleFile, 'utf8');
    }
  }

  // Sauvegarde avant écrasement, pour pouvoir revenir en arrière si besoin
  // (ex. tokens de prod encore en circulation qu'on ne voulait pas invalider).
  if (fs.existsSync(args.envFile)) {
    const backupPath = `${args.envFile}.bak.${Date.now()}`;
    fs.copyFileSync(args.envFile, backupPath);
    console.log(`Sauvegarde de l'ancien .env -> ${backupPath}`);
  }

  envContent = upsertEnvVar(envContent, 'DOCUAI_JWT_PRIVATE_KEY', privateEnvValue);
  envContent = upsertEnvVar(envContent, 'DOCUAI_JWT_PUBLIC_KEY', publicEnvValue);

  fs.writeFileSync(args.envFile, envContent, 'utf8');
  console.log(`Clés écrites dans ${args.envFile} (DOCUAI_JWT_PRIVATE_KEY / DOCUAI_JWT_PUBLIC_KEY).`);

  // 2. Fichiers .pem à côté, pratique pour une inspection manuelle
  //    (openssl rsa -in jwt_private.pem -text -noout, etc.) — ignorés par
  //    .gitignore (*.pem) donc jamais commités par erreur.
  if (args.pemFiles) {
    const dir = path.dirname(args.envFile);
    fs.writeFileSync(path.join(dir, 'jwt_private.pem'), privateKey, { mode: 0o600 });
    fs.writeFileSync(path.join(dir, 'jwt_public.pem'), publicKey);
    console.log(`Clés également écrites en clair dans ${path.join(dir, 'jwt_private.pem')} / jwt_public.pem.`);
  }

  console.log('\n⚠️  Tous les JWT (access + refresh) émis avec l\'ancienne paire sont désormais invalides.');
  console.log('    Chaque utilisateur devra se reconnecter (POST /api/v1/auth/login).');
  console.log('\nRedémarrer le backend pour appliquer :');
  console.log('    docker compose up -d --build backend');
}

main();
