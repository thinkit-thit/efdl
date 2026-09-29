import { useState } from 'react';
import { ApiError } from '../services/apiClient';
import { notifyPhotoChanged, uploadPhoto, UserAvatar } from './UserAvatar';

/**
 * Photo + bouton « Ajouter une image » / « Modifier l'image » ;
 * {@code self} : sa propre photo (page Mon profil).
 */
export function PhotoEditor({ userId, gender, self = false }: { userId: string; gender?: string | null; self?: boolean }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [hasPhoto, setHasPhoto] = useState(true);

  const onFile = async (file: File | undefined) => {
    if (!file) return;
    setBusy(true);
    setError(null);
    try {
      await uploadPhoto(file, self ? undefined : userId);
      setHasPhoto(true);
      notifyPhotoChanged(userId);
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Envoi impossible');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="d-flex align-items-center gap-3">
      <UserAvatar userId={userId} size={180} gender={gender} className="rounded" style={{ width: 120, height: 120 }}
        onMissing={() => setHasPhoto(false)} />
      <div>
        <label className={'btn btn-de-primary btn-sm mb-1' + (busy ? ' disabled' : '')}>
          {busy && <span className="spinner-border spinner-border-sm me-1" role="status" />}
          {hasPhoto ? "Modifier l'image" : 'Ajouter une image'}
          <input type="file" accept="image/png,image/jpeg,image/gif" hidden disabled={busy}
            onChange={(e) => { void onFile(e.target.files?.[0]); e.target.value = ''; }} />
        </label>
        <div><small className="text-muted">JPG, PNG ou GIF, 5 Mo au plus.</small></div>
        {error && <div className="text-danger small mt-1">{error}</div>}
      </div>
    </div>
  );
}
