/**
 * Préparation des images insérées dans l'éditeur type Word.
 *
 * Les images sont encodées en `data:` URI directement dans le HTML du
 * document. C'est ce qui permet à l'export serveur (`HtmlContentParser`) de les
 * écrire dans le DOCX/PDF sans aller les rechercher ailleurs, et au document de
 * rester complet à lui seul. En contrepartie chaque image pèse dans la ligne
 * `document.content_html` : elles sont donc redimensionnées et recompressées
 * ici, à la taille au-delà de laquelle une image n'apporte plus rien sur une
 * page A4.
 */

/** Largeur/hauteur maximale conservée — au-delà, l'image est réduite proportionnellement. */
const MAX_DIMENSION = 1400;
/** Refus net au-delà : recompresser 30 Mo dans le navigateur bloquerait l'onglet pour rien. */
const MAX_SOURCE_BYTES = 15 * 1024 * 1024;
const JPEG_QUALITY = 0.85;

export const ACCEPTED_IMAGE_TYPES = 'image/png,image/jpeg,image/gif,image/webp,image/bmp';

export class ImageTooLargeError extends Error {
  constructor() {
    super('Image trop lourde (15 Mo maximum). Réduisez-la avant de l\'insérer.');
  }
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result));
    reader.onerror = () => reject(new Error("Lecture du fichier impossible."));
    reader.readAsDataURL(file);
  });
}

function loadImage(dataUrl: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const image = new Image();
    image.onload = () => resolve(image);
    image.onerror = () => reject(new Error("Ce fichier n'est pas une image lisible."));
    image.src = dataUrl;
  });
}

/**
 * Fichier -> `data:` URI prêt à être inséré, avec la largeur d'affichage
 * conseillée (bornée à la largeur utile d'une page A4).
 *
 * Les GIF sont repassés tels quels : les redessiner sur un canvas ne
 * conserverait que la première image de l'animation.
 */
export async function fileToEditorImage(file: File): Promise<{ src: string; width: number }> {
  if (file.size > MAX_SOURCE_BYTES) {
    throw new ImageTooLargeError();
  }
  const original = await readAsDataUrl(file);
  const image = await loadImage(original);
  const displayWidth = Math.min(image.naturalWidth || MAX_DIMENSION, 620);

  if (file.type === 'image/gif') {
    return { src: original, width: displayWidth };
  }

  const scale = Math.min(1, MAX_DIMENSION / Math.max(image.naturalWidth, image.naturalHeight));
  if (scale === 1 && file.size < 400 * 1024) {
    // Déjà légère et à taille raisonnable : la recompresser ne ferait que
    // dégrader l'image sans gain de poids notable.
    return { src: original, width: displayWidth };
  }

  const canvas = window.document.createElement('canvas');
  canvas.width = Math.max(1, Math.round(image.naturalWidth * scale));
  canvas.height = Math.max(1, Math.round(image.naturalHeight * scale));
  const context = canvas.getContext('2d');
  if (!context) {
    return { src: original, width: displayWidth };
  }
  context.drawImage(image, 0, 0, canvas.width, canvas.height);

  // PNG conservé en PNG : le JPEG ferait perdre la transparence (logos,
  // captures avec fond détouré).
  const keepsAlpha = file.type === 'image/png' || file.type === 'image/webp';
  const encoded = keepsAlpha ? canvas.toDataURL('image/png') : canvas.toDataURL('image/jpeg', JPEG_QUALITY);
  return { src: encoded.length < original.length ? encoded : original, width: displayWidth };
}
