import { useEffect, useState } from 'react';
import { PageTitle } from '../../components/PageTitle';
import { SearchSelect } from '../../components/SearchSelect';
import { Card, ErrorAlert, Loading, formatDateTime, useApi } from '../../components/common';
import { useConfirm } from '../../components/ConfirmDialog';
import { useAuth } from '../../contexts/AuthContext';
import { api, ApiError, download } from '../../services/apiClient';

interface Change { field: string; label: string | null; before: string | null; after: string | null }
interface Entry {
  date: string; userExternalId: string | null; userName: string | null; action: string; collection: string;
  entityLabel: string | null; targetExternalId: string | null; targetLabel: string | null; changes: Change[];
}
interface Option { id: string; label: string }
interface AuditPageData {
  items: Entry[]; total: number; purgeable: number; page: number; size: number; users: Option[]; types: Option[];
}

const ACTIONS: Record<string, [string, string]> = {
  CREATION: ['Création', 'success'], MODIFICATION: ['Modification', 'primary'], SUPPRESSION: ['Suppression', 'danger'],
  VALIDATION: ['Validation', 'success'], REJET: ['Rejet', 'danger'], MOT_DE_PASSE: ['Mot de passe', 'warning'],
  PURGE: ["Suppression d'historique", 'dark'],
};
const SIZE = 50;

/** Détail d'une trace : « Champ : avant → après » (création : valeurs saisies). */
function Changes({ entry }: { entry: Entry }) {
  const [all, setAll] = useState(false);
  if (entry.changes.length === 0) return null;
  const shown = all ? entry.changes : entry.changes.slice(0, 4);
  return (
    <ul className="list-unstyled mb-0 small">
      {shown.map((c, i) => (
        <li key={i}>
          <span className="text-muted">{c.label ?? c.field} : </span>
          {entry.action === 'CREATION'
            ? <span>{c.after}</span>
            : <><del className="text-danger">{c.before ?? '—'}</del> → <span className="text-success">{c.after ?? '—'}</span></>}
        </li>
      ))}
      {entry.changes.length > 4 && (
        <li><a href="#" onClick={(e) => { e.preventDefault(); setAll(!all); }}>{all ? 'Réduire' : `+ ${entry.changes.length - 4} autres`}</a></li>
      )}
    </ul>
  );
}

/**
 * /admin/historique — historique des modifications de l'application (qui a ajouté, modifié, supprimé, validé quoi
 * et quand) ; réservé à l'administrateur et au « Validateur info » (contrôlé par le serveur).
 */
export function AuditPage() {
  const [filters, setFilters] = useState({ userId: '', collection: '', action: '', from: '', to: '', q: '' });
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const [purging, setPurging] = useState(false);
  const [purgeError, setPurgeError] = useState<string | null>(null);
  const confirm = useConfirm();
  const [archivesReload, setArchivesReload] = useState(0);
  const { user } = useAuth();
  const isAdmin = user?.type === 'ADMIN';
  const set =(key: keyof typeof filters) => (value: string) => { setFilters((f) => ({ ...f, [key]: value })); setPage(0); };

  // Recherche texte appliquée après une courte pause de frappe.
  useEffect(() => {
    const t = setTimeout(() => { setFilters((f) => ({ ...f, q: query })); setPage(0); }, 400);
    return () => clearTimeout(t);
  }, [query]);

  const params = new URLSearchParams({ page: String(page), size: String(SIZE) });
  Object.entries(filters).forEach(([k, v]) => { if (v) params.set(k, v); });
  const { data, error, loading } = useApi(() => api<AuditPageData>(`/api/audit?${params}`), [params.toString(), reload]);

  /** Suppression en masse des entrées filtrées (administrateur) : archive Excel (serveur + téléchargement), puis suppression. */
  const purge = async () => {
    if (!data || data.purgeable === 0) return;
    const ok = await confirm({
      title: "Suppression de l'historique", tone: 'danger', icon: 'las la-exclamation-triangle',
      message: <>Supprimer définitivement {data.purgeable} entrée(s) filtrée(s) de l'historique ?</>,
      detail: "Une archive Excel est d'abord enregistrée sur le serveur et téléchargée ; la suppression est ensuite irréversible. "
        + "Aucune ligne ne reste dans l'historique ; l'archive reste disponible dans « Archives ».",
      confirmLabel: 'Oui, archiver et supprimer',
    });
    if (!ok) return;
    setPurging(true);
    setPurgeError(null);
    try {
      const p = new URLSearchParams({ expected: String(data.purgeable) });
      Object.entries(filters).forEach(([k, v]) => { if (v) p.set(k, v); });
      await download(`/api/audit/purge?${p}`, { method: 'POST' });
      setPage(0);
      setReload((n) => n + 1);
      setArchivesReload((n) => n + 1);
    } catch (e) {
      setPurgeError(e instanceof ApiError ? e.message : 'Suppression impossible');
    } finally {
      setPurging(false);
    }
  };

  const { data: archives } = useApi(
    () => (isAdmin ? api<{ name: string; date: string; size: number }[]>('/api/audit/archives') : Promise.resolve([])),
    [isAdmin, archivesReload]);

  /** Archive d'une suppression d'historique (administrateur). */
  const openArchive = async (name: string) => {
    setPurgeError(null);
    try {
      await download(`/api/audit/archives/${encodeURIComponent(name)}`);
    } catch (e) {
      setPurgeError(e instanceof ApiError ? e.message : 'Archive introuvable');
    }
  };

  const first = data && data.total > 0 ? data.page * data.size + 1 : 0;
  const last = data ? Math.min((data.page + 1) * data.size, data.total) : 0;
  return (
    <>
      <PageTitle title="Historique des modifications" breadcrumb={['Administration', 'Historique']} />
      <Card title="Qui a ajouté, modifié ou supprimé quoi, et quand">
        <div className="row g-2 mb-3">
          <div className="col-md-3">
            <SearchSelect size="sm" value={filters.userId} onChange={set('userId')} placeholder="Utilisateur : tous"
              options={(data?.users ?? []).map((u) => ({ id: u.id, label: u.label }))} />
          </div>
          <div className="col-md-2">
            <SearchSelect size="sm" value={filters.collection} onChange={set('collection')} placeholder="Élément : tous"
              options={(data?.types ?? []).map((t) => ({ id: t.id, label: t.label }))} />
          </div>
          <div className="col-md-2">
            <select className="form-select form-select-sm" value={filters.action} onChange={(e) => set('action')(e.target.value)}
              aria-label="Action">
              <option value="">Action : toutes</option>
              {Object.entries(ACTIONS).map(([k, [label]]) => <option key={k} value={k}>{label}</option>)}
            </select>
          </div>
          <div className="col-md-2">
            <input type="date" className="form-control form-control-sm" value={filters.from} title="Du"
              onChange={(e) => set('from')(e.target.value)} />
          </div>
          <div className="col-md-2">
            <input type="date" className="form-control form-control-sm" value={filters.to} title="Au"
              onChange={(e) => set('to')(e.target.value)} />
          </div>
          <div className="col-md-1">
            <input className="form-control form-control-sm" placeholder="Rechercher…" value={query}
              onChange={(e) => setQuery(e.target.value)} aria-label="Rechercher" />
          </div>
        </div>
        {error && <ErrorAlert message={error} />}
        {purgeError && <div className="mb-2"><ErrorAlert message={purgeError} /></div>}
        {isAdmin && data && data.purgeable > 0 && (
          <div className="clearfix mb-2">
            <button type="button" className="btn btn-sm btn-outline-danger float-end" disabled={purging || loading}
              onClick={() => void purge()}>
              {purging ? <span className="spinner-border spinner-border-sm me-1" role="status" /> : <i className="las la-trash-alt me-1"></i>}
              Supprimer les {data.purgeable} entrée(s) filtrée(s)
            </button>
          </div>
        )}
        {loading && !data && <Loading />}
        {data && (
          <>
            <div className="table-responsive">
              <table className="table table-sm align-middle mb-2">
                <thead>
                  <tr><th style={{ width: 140 }}>Date</th><th>Utilisateur</th><th>Action</th><th>Élément</th><th>Détail</th></tr>
                </thead>
                <tbody>
                  {data.items.length === 0 && <tr><td colSpan={5} className="text-muted">Aucune modification.</td></tr>}
                  {data.items.map((e, i) => {
                    const [label, tone] = ACTIONS[e.action] ?? [e.action, 'secondary'];
                    return (
                      <tr key={i}>
                        <td className="text-nowrap">{formatDateTime(e.date)}</td>
                        <td className="text-nowrap">
                          {e.userName}
                        </td>
                        <td><span className={`badge badge-soft-${tone}`}>{label}</span></td>
                        <td>
                          <span className="text-muted">{e.entityLabel}</span>{e.targetLabel ? <> : {e.targetLabel}</> : null}
                          {e.action === 'PURGE' && isAdmin && e.targetExternalId && (
                            <div><a href="#" onClick={(ev) => { ev.preventDefault(); void openArchive(e.targetExternalId!); }}>
                              <i className="las la-file-excel text-success me-1"></i>Archive
                            </a></div>
                          )}
                        </td>
                        <td><Changes entry={e} /></td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
            <div className="clearfix">
              <span className="text-muted small float-start pt-2">{data.total > 0 ? `${first}–${last} sur ${data.total}` : ''}</span>
              <span className="float-end">
                <button type="button" className="btn btn-sm btn-outline-secondary me-2" disabled={page === 0 || loading}
                  onClick={() => setPage((p) => p - 1)}>Précédent</button>
                <button type="button" className="btn btn-sm btn-outline-secondary" disabled={last >= data.total || loading}
                  onClick={() => setPage((p) => p + 1)}>Suivant</button>
              </span>
            </div>
          </>
        )}
      </Card>
      {isAdmin && archives && archives.length > 0 && (
        <Card title="Archives des suppressions d'historique">
          <ul className="list-unstyled mb-0">
            {archives.map((a) => (
              <li key={a.name} className="mb-1">
                <a href="#" onClick={(ev) => { ev.preventDefault(); void openArchive(a.name); }}>
                  <i className="las la-file-excel text-success me-1"></i>{a.name}
                </a>
                <span className="text-muted small ms-2">{formatDateTime(a.date)} — {Math.max(1, Math.round(a.size / 1024))} Ko</span>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </>
  );
}
