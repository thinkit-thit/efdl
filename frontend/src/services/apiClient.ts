import type { ApiErrorBody } from '../types/api';

/** Erreur HTTP renvoyée par l'API (message déjà adapté à l'affichage, sans détail technique). */
export class ApiError extends Error {
  constructor(public readonly status: number, message: string) {
    super(message);
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';

function readCookie(name: string): string | null {
  const match = document.cookie.split('; ').find((c) => c.startsWith(name + '='));
  return match ? decodeURIComponent(match.split('=')[1]) : null;
}

/** Garantit la présence du cookie CSRF (émis par GET /api/auth/csrf). */
export async function ensureCsrf(): Promise<void> {
  if (!readCookie(CSRF_COOKIE)) {
    await fetch('/api/auth/csrf', { credentials: 'same-origin' });
  }
}

/** Client HTTP centralisé : cookies de session, jeton CSRF, erreurs JSON homogènes. */
export async function api<T>(path: string, options: RequestInit = {}): Promise<T> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  // Fichier (FormData) : le navigateur fixe lui-même le type multipart et sa délimitation.
  if (options.body && !(options.body instanceof FormData) && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrf();
    const token = readCookie(CSRF_COOKIE);
    if (token) headers.set(CSRF_HEADER, token);
  }
  const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
  if (response.status === 204) {
    return undefined as T;
  }
  const isJson = response.headers.get('Content-Type')?.includes('application/json');
  const body = isJson ? await response.json() : null;
  if (!response.ok) {
    const message = (body as ApiErrorBody | null)?.message ?? defaultMessage(response.status);
    throw new ApiError(response.status, message);
  }
  return body as T;
}

/**
 * Appel qui renvoie un fichier (ex. archive Excel) : même session et même jeton CSRF que {@link api}, puis
 * téléchargement dans le navigateur sous le nom donné par le serveur.
 */
export async function download(path: string, options: RequestInit = {}): Promise<void> {
  const method = (options.method ?? 'GET').toUpperCase();
  const headers = new Headers(options.headers);
  if (method !== 'GET' && method !== 'HEAD') {
    await ensureCsrf();
    const token = readCookie(CSRF_COOKIE);
    if (token) headers.set(CSRF_HEADER, token);
  }
  const response = await fetch(path, { ...options, method, headers, credentials: 'same-origin' });
  if (!response.ok) {
    const isJson = response.headers.get('Content-Type')?.includes('application/json');
    const body = isJson ? await response.json() : null;
    throw new ApiError(response.status, (body as ApiErrorBody | null)?.message ?? defaultMessage(response.status));
  }
  const disposition = response.headers.get('Content-Disposition') ?? '';
  const encoded = /filename\*=UTF-8''([^;]+)/i.exec(disposition)?.[1];
  const plain = /filename="?([^";]+)"?/i.exec(disposition)?.[1];
  const name = encoded ? decodeURIComponent(encoded) : plain ?? 'fichier';
  const url = URL.createObjectURL(await response.blob());
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

function defaultMessage(status: number): string {
  if (status === 401) return 'Session expirée, veuillez vous reconnecter';
  if (status === 403) return 'Vous ne pouvez pas accéder à cette page';
  return 'Une erreur est survenue';
}
