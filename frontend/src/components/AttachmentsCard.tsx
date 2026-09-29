import { useState } from 'react';
import { api, ApiError } from '../services/apiClient';
import { Card, ErrorAlert, Loading, formatDate, useApi } from './common';
import { deleteOptions, useConfirm } from './ConfirmDialog';
import { SearchSelect } from './SearchSelect';

interface AttachmentItem {
  externalId: string; title: string | null; originalFilename: string | null; contentType: string | null; size: number;
  avancementExternalId: string | null; avancementTitle: string | null; creationDate: string | null; createdBy: string | null;
}
interface Listing { items: AttachmentItem[]; canAdd: boolean; canDelete: boolean }

/** Le thème Metrica étire les .btn enfants d'un .d-flex (flex:1) : largeur du texte. */
const NO_STRETCH = { flex: 'none' } as const;

function sizeLabel(bytes: number): string {
  if (bytes >= 1024 * 1024) return (bytes / 1024 / 1024).toLocaleString('fr-FR', { maximumFractionDigits: 1 }) + ' Mo';
  return Math.max(1, Math.round(bytes / 1024)).toLocaleString('fr-FR') + ' Ko';
}

function icon(contentType: string | null): string {
  if (contentType === 'application/pdf') return 'las la-file-pdf text-danger';
  if (contentType?.startsWith('image/')) return 'las la-file-image text-info';
  if (contentType?.includes('sheet') || contentType?.includes('excel') || contentType === 'text/csv') return 'las la-file-excel text-success';
  if (contentType?.includes('word') || contentType?.includes('opendocument.text')) return 'las la-file-word text-primary';
  return 'las la-file text-secondary';
}

/**
 * Documents joints : facture ({@code paiements}) ou suivi d'avancement d'un programme,
 * projet ou sous-projet ({@code programmes}, {@code subProgrammes}, {@code projects} ; jalon facultatif). Ajout et suppression selon les droits renvoyés par le serveur.
 */
export function AttachmentsCard({ target, id, jalons = [], title = 'Documents' }: {
  target: 'paiements' | 'programmes' | 'subProgrammes' | 'projects'; id: string; jalons?: { id: string; label: string }[]; title?: string;
}) {
  const confirm = useConfirm();
  const [reload, setReload] = useState(0);
  const { data, error, loading } = useApi(() => api<Listing>(`/api/attachments/${target}/${encodeURIComponent(id)}`), [target, id, reload]);
  const [file, setFile] = useState<File | null>(null);
  const [docTitle, setDocTitle] = useState('');
  const [jalon, setJalon] = useState('');
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [inputKey, setInputKey] = useState(0);

  const add = async () => {
    if (!file) return;
    setBusy(true);
    setActionError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      if (docTitle.trim()) form.append('title', docTitle.trim());
      if (jalon) form.append('avancementId', jalon);
      await api(`/api/attachments/${target}/${encodeURIComponent(id)}`, { method: 'POST', body: form });
      setFile(null);
      setDocTitle('');
      setJalon('');
      setInputKey((k) => k + 1);
      setReload((n) => n + 1);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Envoi impossible');
    } finally {
      setBusy(false);
    }
  };

  const remove = async (item: AttachmentItem) => {
    if (!(await confirm(deleteOptions(item.title ?? item.originalFilename)))) return;
    setActionError(null);
    try {
      await api<void>(`/api/attachments/${encodeURIComponent(item.externalId)}`, { method: 'DELETE' });
      setReload((n) => n + 1);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Suppression impossible');
    }
  };

  // Pas de droit de consultation : la carte n'est pas affichée.
  if (error && !data) return null;
  const withJalon = target !== 'paiements' && jalons.length > 0;
  return (
    <Card title={title}>
      {loading && !data && <Loading />}
      {actionError && <div className="mb-2"><ErrorAlert message={actionError} /></div>}
      {data && (
        <>
          {data.items.length === 0
            ? <p className="text-muted mb-3">Aucun document.</p>
            : (
              <div className="table-responsive mb-3">
                <table className="table table-sm mb-0 align-middle">
                  <thead>
                    <tr>
                      <th>Document</th>
                      {withJalon && <th>Jalon</th>}
                      <th>Fichier</th><th>Ajouté le</th><th>Par</th><th></th>
                    </tr>
                  </thead>
                  <tbody>
                    {data.items.map((d) => (
                      <tr key={d.externalId}>
                        <td>
                          <a href={`/api/attachments/${encodeURIComponent(d.externalId)}/file`} target="_blank" rel="noreferrer">
                            <i className={icon(d.contentType) + ' font-18 me-1'}></i>{d.title ?? d.originalFilename}
                          </a>
                        </td>
                        {withJalon && <td>{d.avancementTitle ?? ''}</td>}
                        <td className="text-muted small">{d.originalFilename} ({sizeLabel(d.size)})</td>
                        <td>{formatDate(d.creationDate)}</td>
                        <td>{d.createdBy ?? ''}</td>
                        <td className="text-end">
                          {data.canDelete && (
                            <a href="#" title="Supprimer" onClick={(e) => { e.preventDefault(); void remove(d); }}>
                              <i className="las la-trash-alt text-danger font-16"></i>
                            </a>
                          )}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          {data.canAdd && (
            <div className="row g-2 align-items-center">
              <div className="col-md-4">
                <input key={inputKey} type="file" className="form-control"
                  accept=".pdf,.jpg,.jpeg,.png,.gif,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.odt,.ods,.txt,.csv,.zip"
                  onChange={(e) => setFile(e.target.files?.[0] ?? null)} />
              </div>
              <div className={withJalon ? 'col-md-3' : 'col-md-5'}>
                <input className="form-control" placeholder="Titre (facultatif : nom du fichier)" maxLength={200}
                  value={docTitle} onChange={(e) => setDocTitle(e.target.value)} />
              </div>
              {withJalon && (
                <div className="col-md-3">
                  <SearchSelect value={jalon} onChange={setJalon} placeholder="Jalon (facultatif)"
                    options={jalons.map((j) => ({ id: j.id, label: j.label }))} />
                </div>
              )}
              <div className="col-md-2">
                <button type="button" className="btn btn-primary" style={NO_STRETCH} disabled={!file || busy} onClick={() => void add()}>
                  {busy && <span className="spinner-border spinner-border-sm me-1" role="status" />}Ajouter
                </button>
              </div>
              <div className="col-12">
                <small className="text-muted">PDF, images, Word, Excel, PowerPoint, texte, CSV ou ZIP ; 10 Mo au plus.</small>
              </div>
            </div>
          )}
        </>
      )}
    </Card>
  );
}
