import { useEffect, useState, type ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ApiError } from '../services/apiClient';
import { deleteOptions, LeaveLink, markSaved, useConfirm } from './ConfirmDialog';

/** Chargement d'une ressource de l'API avec états chargement / erreur. */
export function useApi<T>(load: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    load()
      .then((d) => { if (!cancelled) setData(d); })
      .catch((e) => { if (!cancelled) setError(e instanceof ApiError ? e.message : 'Une erreur est survenue'); })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);
  return { data, error, loading };
}

/** Carte Metrica (card / card-header / card-body). */
export function Card({ title, actions, children }: { title: string; actions?: ReactNode; children: ReactNode }) {
  return (
    <div className="card">
      <div className="card-header">
        <div className="row align-items-center">
          <div className="col"><h4 className="card-title">{title}</h4></div>
          {actions && <div className="col-auto">{actions}</div>}
        </div>
      </div>
      <div className="card-body">{children}</div>
    </div>
  );
}

export function Loading() {
  return <div className="text-center py-4"><div className="spinner-border spinner-border-sm text-primary" role="status" /></div>;
}

export function ErrorAlert({ message }: { message: string }) {
  return <div className="alert alert-danger mb-0">{message}</div>;
}

/** Ligne de formulaire (libellé à gauche, champ à droite). */
export function FormRow({ label, htmlFor, required, children }: { label: string; htmlFor?: string; required?: boolean; children: ReactNode }) {
  return (
    <div className="mb-3 row">
      <label className="col-sm-4 col-form-label text-end" htmlFor={htmlFor}>
        {label}{required && <span className="text-danger"> *</span>}
      </label>
      <div className="col-sm-8">{children}</div>
    </div>
  );
}

/** Champ texte (ou date, e-mail, nombre) lié à une valeur contrôlée. */
export function TextField({ label, name, value, onChange, type = 'text', required, maxLength }: {
  label: string; name: string; value: string | number | null | undefined; onChange: (value: string) => void;
  type?: 'text' | 'date' | 'email' | 'number'; required?: boolean; maxLength?: number;
}) {
  return (
    <FormRow label={label} htmlFor={name} required={required}>
      <input id={name} name={name} className="form-control" type={type} required={required} maxLength={maxLength}
        value={value ?? ''} onChange={(e) => onChange(e.target.value)} />
    </FormRow>
  );
}

/**
 * Enregistrement d'un formulaire : état « en cours », message d'erreur de l'API, puis retour à la liste
 * (retour à la liste après enregistrement).
 */
export function useSubmit() {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const submit = async (action: () => Promise<unknown>, onDone: () => void) => {
    setSaving(true);
    setError(null);
    try {
      await action();
      onDone();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Une erreur est survenue');
    } finally {
      setSaving(false);
    }
  };
  return { saving, error, submit };
}

/**
 * Boutons Enregistrer / Annuler, et Supprimer (avec confirmation) si {@code onDelete} est fourni.
 * La suppression est logique côté serveur (status=DELETED) : le document reste en base.
 */
export function FormActions({ saving, cancelTo, onDelete, deleteLabel }: {
  saving: boolean; cancelTo: string; onDelete?: () => void; deleteLabel?: string;
}) {
  const confirm = useConfirm();
  const confirmDelete = async () => {
    if (onDelete && await confirm(deleteOptions(deleteLabel))) {
      markSaved(); // la fiche disparaît : pas d'alerte « modifications non enregistrées »
      onDelete();
    }
  };
  return (
    <div className="row">
      {/* Pas de d-flex : le thème Metrica étire les .btn enfants d'un .d-flex (flex:1). */}
      <div className="col-sm-8 offset-sm-4 clearfix">
        <button type="submit" className="btn btn-primary me-2" disabled={saving}>
          {saving && <span className="spinner-border spinner-border-sm me-1" role="status" />}Enregistrer
        </button>
        <LeaveLink to={cancelTo} className="btn btn-outline-secondary">Annuler</LeaveLink>
        {onDelete && (
          <button type="button" className="btn btn-outline-danger float-end" disabled={saving} onClick={confirmDelete}>
            <i className="las la-trash-alt me-1"></i>Supprimer
          </button>
        )}
      </div>
    </div>
  );
}

/** Bouton d'ajout d'en-tête de liste (btn-outline-light btn-sm px-4, « + Ajouter »). */
export function AddButton({ to, label = 'Ajouter' }: { to: string; label?: string }) {
  return <Link to={to} className="btn btn-outline-light btn-sm px-4">+ {label}</Link>;
}

/** Date ISO de l'API → valeur d'un champ date (yyyy-MM-dd, heure locale). */
export function toInputDate(value: string | null | undefined): string {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Date au format jj/mm/aaaa. */
/** Date et heure (jj/mm/aaaa hh:mm), ou vide. */
export function formatDateTime(value: string | null | undefined): string {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return formatDate(value) + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
}

export function formatDate(value: string | null | undefined): string {
  if (!value) return '';
  const d = new Date(value);
  if (Number.isNaN(d.getTime())) return '';
  return d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}
