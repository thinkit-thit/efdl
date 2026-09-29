import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PageTitle } from '../../components/PageTitle';
import { Card, ErrorAlert, Loading, formatDateTime, useApi } from '../../components/common';
import { ApiError } from '../../services/apiClient';
import {
  NOTIFICATION_ICONS, NOTIFICATIONS_CHANGED, notificationApi, type NotificationItem, type NotificationPreference,
} from '../../services/notificationApi';

const SIZE = 20;

/**
 * /account/notifications — « Mes notifications » : toutes ses notifications (non lues en évidence) et ses
 * préférences par type : dans l'application et/ou par e-mail.
 */
export function NotificationsPage() {
  const navigate = useNavigate();
  const [unreadOnly, setUnreadOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [reload, setReload] = useState(0);
  const { data, error, loading } = useApi(() => notificationApi.list(unreadOnly, page, SIZE), [unreadOnly, page, reload]);
  const changed = () => { setReload((n) => n + 1); window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED)); };

  const open = async (n: NotificationItem) => {
    if (!n.read) await notificationApi.read(n.id).catch(() => {});
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
    if (n.link) navigate(n.link); else changed();
  };

  const last = data ? Math.min((page + 1) * SIZE, data.total) : 0;
  return (
    <>
      <PageTitle title="Mes notifications" breadcrumb={['Compte', 'Notifications']} />
      <Card title={`Notifications${data ? ` (${data.unread} non lue${data.unread > 1 ? 's' : ''})` : ''}`}
        actions={(
          <>
            <div className="form-check form-switch d-inline-block me-3">
              <input className="form-check-input" type="checkbox" id="unreadOnly" checked={unreadOnly}
                onChange={(e) => { setUnreadOnly(e.target.checked); setPage(0); }} />
              <label className="form-check-label" htmlFor="unreadOnly">Non lues seulement</label>
            </div>
            {data && data.unread > 0 && (
              <button type="button" className="btn btn-sm btn-outline-secondary"
                onClick={() => void notificationApi.readAll().then(changed)}>Tout marquer comme lu</button>
            )}
          </>
        )}>
        {error && <ErrorAlert message={error} />}
        {loading && !data && <Loading />}
        {data && (
          <>
            {data.items.length === 0 && <p className="text-muted mb-0">Aucune notification.</p>}
            <ul className="list-group list-group-flush">
              {data.items.map((n) => (
                <li key={n.id} className={'list-group-item d-flex align-items-start' + (n.read ? '' : ' bg-light')}>
                  <i className={'las ' + (NOTIFICATION_ICONS[n.type] ?? 'la-bell text-secondary') + ' font-24 me-3'}></i>
                  <div className="flex-grow-1">
                    <a href="#" className={n.read ? 'text-dark' : 'fw-semibold'} onClick={(e) => { e.preventDefault(); void open(n); }}>
                      {n.title}
                    </a>
                    <div className="text-muted small">{n.message}</div>
                  </div>
                  <span className="text-muted small text-nowrap ms-3">{formatDateTime(n.date)}</span>
                </li>
              ))}
            </ul>
            {data.total > SIZE && (
              <div className="clearfix mt-2">
                <span className="float-end">
                  <button type="button" className="btn btn-sm btn-outline-secondary me-2" disabled={page === 0}
                    onClick={() => setPage((p) => p - 1)}>Précédent</button>
                  <button type="button" className="btn btn-sm btn-outline-secondary" disabled={last >= data.total}
                    onClick={() => setPage((p) => p + 1)}>Suivant</button>
                </span>
              </div>
            )}
          </>
        )}
      </Card>
      <PreferencesCard />
    </>
  );
}

/** Préférences : pour chaque type, dans l'application et/ou par e-mail. */
function PreferencesCard() {
  const { data, error, loading } = useApi(() => notificationApi.preferences(), []);
  const [prefs, setPrefs] = useState<NotificationPreference[] | null>(null);
  const [saving, setSaving] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);
  const current = prefs ?? data;
  const set = (type: string, key: 'app' | 'mail', value: boolean) => {
    setMessage(null);
    setPrefs((current ?? []).map((p) => (p.type === type ? { ...p, [key]: value } : p)));
  };
  const save = async () => {
    if (!current) return;
    setSaving(true);
    setSaveError(null);
    try {
      setPrefs(await notificationApi.savePreferences(current));
      setMessage('Préférences enregistrées.');
    } catch (e) {
      setSaveError(e instanceof ApiError ? e.message : 'Enregistrement impossible');
    } finally {
      setSaving(false);
    }
  };
  return (
    <Card title="Mes préférences de notification">
      {error && <ErrorAlert message={error} />}
      {saveError && <ErrorAlert message={saveError} />}
      {loading && !current && <Loading />}
      {current && (
        <>
          <div className="table-responsive">
            <table className="table table-sm align-middle mb-3">
              <thead>
                <tr><th>Notification</th><th className="text-center">Dans l'application</th><th className="text-center">Par e-mail</th></tr>
              </thead>
              <tbody>
                {current.map((p) => (
                  <tr key={p.type}>
                    <td><div className="fw-semibold">{p.label}</div><div className="text-muted small">{p.description}</div></td>
                    {(['app', 'mail'] as const).map((k) => (
                      <td key={k} className="text-center">
                        <input className="form-check-input" type="checkbox" checked={p[k]} aria-label={`${p.label} ${k}`}
                          onChange={(e) => set(p.type, k, e.target.checked)} />
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="button" className="btn btn-primary" disabled={saving} onClick={() => void save()}>
            {saving && <span className="spinner-border spinner-border-sm me-1" role="status" />}Enregistrer
          </button>
          {message && <span className="text-success ms-3">{message}</span>}
          <div className="text-muted small mt-2">Les e-mails partent vers l'adresse de votre fiche utilisateur.</div>
        </>
      )}
    </Card>
  );
}
