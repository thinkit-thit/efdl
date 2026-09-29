import { useState, type ReactNode } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { api, ApiError } from '../services/apiClient';
import { Card, ErrorAlert } from './common';
import { useConfirm } from './ConfirmDialog';

/** Le thème Metrica étire les .btn enfants d'un .d-flex (flex:1) : on garde la largeur du texte. */
const NO_STRETCH = { flex: 'none' } as const;

interface ImportLine { line: number; action: 'CREATION' | 'MISE_A_JOUR' | 'IGNOREE' | 'ERREUR'; label: string | null; messages: string[] }
interface ImportReport {
  applied: boolean; lignes: number; creations: number; misesAJour: number; ignorees: number; erreurs: number; details: ImportLine[];
}

const ACTIONS: Record<ImportLine['action'], [string, string]> = {
  CREATION: ['Création', 'success'], MISE_A_JOUR: ['Mise à jour', 'info'], IGNOREE: ['Ignorée', 'secondary'], ERREUR: ['Erreur', 'danger'],
};

/**
 * Import Excel en deux temps : « Analyser » (rien n'est écrit, compte rendu ligne par ligne) puis « Importer ».
 * @param url     point d'entrée de l'API (/api/imports/…)
 * @param options paramètres supplémentaires (ex. historique=true) et leur saisie
 */
export function ImportCard({ url, columns, options, extra, onImported }: {
  url: string; columns: ReactNode; options?: ReactNode; extra?: Record<string, string>; onImported: () => void;
}) {
  const [file, setFile] = useState<File | null>(null);
  const [report, setReport] = useState<ImportReport | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const confirm = useConfirm();
  const { user } = useAuth();
  const run = async (apply: boolean) => {
    if (!file) return;
    if (apply && !(await confirm({
      title: "Confirmation d'import", tone: 'primary', icon: 'las la-file-import',
      message: 'Êtes-vous sûr de vouloir importer ce fichier ?',
      detail: "Les créations et mises à jour annoncées par l'analyse seront enregistrées.", confirmLabel: 'Oui, importer',
    }))) return;
    setBusy(true);
    setError(null);
    try {
      const form = new FormData();
      form.append('file', file);
      const params = new URLSearchParams({ apply: String(apply), ...(extra ?? {}) });
      const result = await api<ImportReport>(`${url}?${params}`, { method: 'POST', body: form });
      setReport(result);
      if (apply) onImported();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Import impossible');
    } finally {
      setBusy(false);
    }
  };

  // Imports réservés au « Validateur info » de l'application et à l'administrateur.
  if (!user?.canImport) return null;

  return (
    <Card title="Importer un fichier Excel">
      <p className="text-muted mb-2">{columns}</p>
      <div className="d-flex flex-wrap align-items-center gap-2 mb-2">
        <input type="file" className="form-control" style={{ maxWidth: 420 }} accept=".xlsx,.xls"
          onChange={(e) => { setFile(e.target.files?.[0] ?? null); setReport(null); }} />
        {options}
        <button type="button" className="btn btn-outline-primary" style={NO_STRETCH} disabled={!file || busy} onClick={() => run(false)}>
          {busy && <span className="spinner-border spinner-border-sm me-1" role="status" />}Analyser
        </button>
        <button type="button" className="btn btn-primary" style={NO_STRETCH} disabled={!file || busy || !report || report.applied} onClick={() => run(true)}>
          Importer
        </button>
      </div>
      {error && <ErrorAlert message={error} />}
      {report && (
        <>
          <div className={`alert ${report.applied ? 'alert-success' : 'alert-info'} py-2 mb-2`}>
            {report.applied ? 'Import terminé' : 'Analyse (rien n\'a été enregistré)'} : {report.lignes} ligne(s) —{' '}
            {report.creations} création(s), {report.misesAJour} mise(s) à jour, {report.ignorees} ignorée(s), {report.erreurs} erreur(s).
          </div>
          <div className="table-responsive" style={{ maxHeight: 360, overflowY: 'auto' }}>
            <table className="table table-sm mb-0">
              <thead className="thead-light"><tr><th>Ligne</th><th>Résultat</th><th>Élément</th><th>Détails</th></tr></thead>
              <tbody>
                {report.details.map((l) => (
                  <tr key={l.line}>
                    <td>{l.line}</td>
                    <td><span className={`badge badge-soft-${ACTIONS[l.action][1]}`}>{ACTIONS[l.action][0]}</span></td>
                    <td>{l.label}</td>
                    <td className="small">{l.messages.join(' · ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </Card>
  );
}
