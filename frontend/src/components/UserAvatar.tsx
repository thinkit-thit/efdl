import { useEffect, useState } from 'react';
import avatarDefault from '../assets/images/avatar.jpg';
import avatarFemale from '../assets/images/avatarF.jpg';
import { api } from '../services/apiClient';

const PHOTO_CHANGED = 'efdl-photo-changed';

/** Signale une nouvelle photo : les avatars de cet utilisateur se rechargent. */
export function notifyPhotoChanged(userId: string): void {
  window.dispatchEvent(new CustomEvent(PHOTO_CHANGED, { detail: userId }));
}

/** Envoi d'une photo ({@code userId} absent = sa propre photo). */
export async function uploadPhoto(file: File, userId?: string): Promise<void> {
  const form = new FormData();
  form.append('file', file);
  await api<void>(userId ? `/api/admin/users/${encodeURIComponent(userId)}/photo` : '/api/auth/me/photo', { method: 'POST', body: form });
}

/**
 * Photo de profil (/api/users/{id}/photo?size=65 ou 180), ou avatar par défaut selon le genre
 * (avatar.jpg / avatarF.jpg) si l'utilisateur n'en a pas.
 */
export function UserAvatar({ userId, size, gender, className, style, onMissing }: {
  userId: string | null | undefined; size: number; gender?: string | null; className?: string; style?: React.CSSProperties;
  /** Appelé quand l'utilisateur n'a pas de photo (avatar par défaut affiché). */
  onMissing?: () => void;
}) {
  const fallback = gender === 'FEMALE' ? avatarFemale : avatarDefault;
  const [version, setVersion] = useState(0);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const onChange = (e: Event) => {
      if ((e as CustomEvent<string>).detail === userId) { setFailed(false); setVersion((v) => v + 1); }
    };
    window.addEventListener(PHOTO_CHANGED, onChange);
    return () => window.removeEventListener(PHOTO_CHANGED, onChange);
  }, [userId]);
  useEffect(() => setFailed(false), [userId]);

  const src = !userId || failed ? fallback
    : `/api/users/${encodeURIComponent(userId)}/photo?size=${size}${version ? `&v=${version}` : ''}`;
  return <img src={src} alt="photo" className={className} style={{ objectFit: 'cover', ...style }} onError={() => { if (!failed) { setFailed(true); onMissing?.(); } }} />;
}
