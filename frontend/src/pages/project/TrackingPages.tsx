import { useState, type FormEvent } from 'react';
import { Navigate, useParams, useSearchParams } from 'react-router-dom';
import { AttachmentsCard } from '../../components/AttachmentsCard';
import { LeaveLink } from '../../components/ConfirmDialog';
import { MadagascarMap } from '../../components/MadagascarMap';
import { PageTitle } from '../../components/PageTitle';
import { Card, ErrorAlert, Loading, formatDate, toInputDate, useApi, useSubmit } from '../../components/common';
import { ApiError, download } from '../../services/apiClient';
import { FROM_PARAM, PROGRESS_STEP, safeFrom } from '../../services/projectTabs';
import { trackingApi, type ProgressTarget, type Bar, type Dashboard, type Milestone, type Progress, type TimelineRow } from '../../services/trackingApi';

const STATUT_TONE: Record<string, string> = { TERMINE: 'success', TERMINE_EN_RETARD: 'warning', EN_RETARD: 'danger', EN_COURS: 'info' };
const STATUT_LABEL: Record<string, string> = {
  TERMINE: 'Terminé', TERMINE_EN_RETARD: 'Terminé en retard', EN_RETARD: 'En retard', EN_COURS: 'En cours',
};

function Statut({ value }: { value: string | null }) {
  if (!value) return null;
  return <span className={`badge badge-soft-${STATUT_TONE[value] ?? 'secondary'}`}>{STATUT_LABEL[value] ?? value}</span>;
}

// ============================ Suivi d'avancement ============================

/**
 * Suivi d'avancement : jalons du niveau, date prévisionnelle, date réelle, evidence.
 * Programme et projet : page propre. Sous-projet : onglet « Avancement » de sa fiche (redirection vers la fiche
 * d'origine, sinon celle de la liste du sous-programme).
 */
export function ProgressPage() {
  const { programmeId, subProgrammeId, id = '' } = useParams();
  const [search] = useSearchParams();
  if (subProgrammeId && programmeId) {
    const enc = encodeURIComponent;
    const edit = safeFrom(search.get(FROM_PARAM))
      ?? `/admin/project/programme/${enc(programmeId)}/subProgramme/${enc(subProgrammeId)}/edit/${enc(id)}`;
    return <Navigate to={`${edit}?step=${PROGRESS_STEP}`} replace />;
  }
  return <LevelProgressPage programmeId={programmeId} id={id} />;
}

/** Jalons d'un niveau, rechargés après enregistrement (formulaire recréé à partir des valeurs relues). */
function useProgress(target: ProgressTarget) {
  const [reload, setReload] = useState(0);
  const [saved, setSaved] = useState(false);
  const { data, error, loading } = useApi(() => trackingApi.progress(target),
    [target.programmeId, target.subProgrammeId, target.id, reload]);
  // Recréé à partir des valeurs relues (réenregistrer des valeurs périmées effacerait les dates saisies).
  const version = data ? JSON.stringify(data.jalons.map((j) => [j.avancementId, j.prevision, j.reel, j.evidence])) : '';
  const form = data && !loading ? (
    <ProgressForm key={version} target={target} data={data} saved={saved}
      onEdit={() => setSaved(false)} onSaved={() => { setSaved(true); setReload((n) => n + 1); }} />
  ) : null;
  const documents = data ? (
    <AttachmentsCard target={target.subProgrammeId ? 'projects' : target.programmeId ? 'subProgrammes' : 'programmes'}
      id={target.id} title="Documents d'avancement"
      jalons={data.jalons.map((j) => ({ id: j.avancementId, label: `${j.rang}. ${j.title ?? ''}` }))} />
  ) : null;
  return { data, error, loading, form, documents };
}

function LevelProgressPage({ programmeId, id }: { programmeId?: string; id: string }) {
  const { data, error, loading, form, documents } = useProgress({ programmeId, id });
  const level = programmeId ? 'projet' : 'PROGRAMME';
  const back = programmeId ? `/admin/project/programme/${programmeId}/subProgrammes` : '/admin/project/programmes';
  return (
    <>
      <PageTitle title={`Gestion des avancements du ${level} ${data?.title ?? ''}`} breadcrumb={[{ label: 'FDL PROJET', to: '/admin/project/programmes' }, 'Suivi avancement']} />
      <Card title={`${level.charAt(0).toUpperCase() + level.slice(1)} ${data?.title ?? ''}`}
        actions={<LeaveLink className="btn btn-sm btn-outline-secondary" to={back}>Retour</LeaveLink>}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {form}
      </Card>
      {documents}
    </>
  );
}

/** Onglet « Avancement » de la fiche sous-projet : jalons et documents, dans la fiche. */
export function ProjectProgressPanel({ target }: { target: ProgressTarget }) {
  const { error, loading, form, documents } = useProgress(target);
  return (
    <>
      {loading && <Loading />}
      {error && <ErrorAlert message={error} />}
      {form}
      <div className="mt-3">{documents}</div>
    </>
  );
}

function ProgressForm({ target, data, saved, onEdit, onSaved }: {
  target: { programmeId?: string; subProgrammeId?: string; id: string }; data: Progress; saved: boolean;
  onEdit: () => void; onSaved: () => void;
}) {
  const { saving, error, submit } = useSubmit();
  const [rows, setRows] = useState(() => data.jalons.map((j: Milestone) => ({
    avancementId: j.avancementId, prevision: toInputDate(j.prevision), reel: toInputDate(j.reel), evidence: j.evidence ?? '',
  })));
  const set = (i: number, key: 'prevision' | 'reel' | 'evidence', value: string) => {
    onEdit();
    setRows((r) => r.map((row, n) => (n === i ? { ...row, [key]: value } : row)));
  };
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit(() => trackingApi.saveProgress(target, rows), onSaved);
  };
  if (data.jalons.length === 0) {
    return <p className="text-muted mb-0">Aucun jalon défini pour ce niveau (Paramètre › Projets › type avancement).</p>;
  }
  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      {saved && <div className="alert alert-success py-2">Avancement enregistré.</div>}
      <p className="mb-2">Avancement : <strong>{data.pourcentage.toLocaleString('fr-FR')} %</strong></p>
      <div className="table-responsive">
        <table className="table table-sm mb-0 align-middle">
          <thead className="thead-light">
            <tr><th>Ordre</th><th>Libelé</th><th>Date prévisionnelle</th><th>Date réel</th><th>Evidence</th><th>Statut</th></tr>
          </thead>
          <tbody>
            {data.jalons.map((j, i) => (
              <tr key={j.avancementId}>
                <td>{j.rang}</td>
                <td>{j.title} <small className="text-muted">({j.pourcentage} %)</small></td>
                <td><input type="date" className="form-control form-control-sm" value={rows[i].prevision}
                  onChange={(e) => set(i, 'prevision', e.target.value)} /></td>
                <td><input type="date" className="form-control form-control-sm" value={rows[i].reel}
                  onChange={(e) => set(i, 'reel', e.target.value)} /></td>
                <td><input className="form-control form-control-sm" value={rows[i].evidence}
                  onChange={(e) => set(i, 'evidence', e.target.value)} /></td>
                <td><Statut value={j.statut} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <button type="submit" className="btn btn-primary btn-sm px-4 mt-3" disabled={saving}>
        {saving && <span className="spinner-border spinner-border-sm me-1" role="status" />}Enregistrer
      </button>
    </form>
  );
}

// ============================ Tableaux de bord ============================

/** Carte indicateur Metrica (budget, nombre, avancement). */
export function Kpi({ label, value, icon = 'ti-activity', className = 'col-md-4' }: {
  label: string; value: string; icon?: string; className?: string;
}) {
  return (
    <div className={className}>
      <div className="card"><div className="card-body">
        <div className="row d-flex justify-content-center">
          <div className="col">
            <p className="text-dark mb-0 fw-semibold">{label}</p>
            <h3 className="my-1 font-20 fw-bold">{value}</h3>
          </div>
          <div className="col-auto align-self-center"><i className={'ti ' + icon + ' font-24 align-self-center text-muted'}></i></div>
        </div>
      </div></div>
    </div>
  );
}

/** Histogramme horizontal (nombre de sous-projets par jalon). */
function Bars({ bars, tone }: { bars: Bar[]; tone: string }) {
  if (bars.length === 0) return <p className="text-muted mb-0">Aucun sous-projet.</p>;
  const max = Math.max(...bars.map((b) => b.count), 1);
  return (
    <>
      {bars.map((b) => (
        <div key={b.label} className="mb-2">
          <div className="d-flex justify-content-between small"><span>{b.label}</span><strong>{b.count}</strong></div>
          <div className="progress" style={{ height: 8 }}>
            <div className={`progress-bar bg-${tone}`} role="progressbar" style={{ width: `${(b.count / max) * 100}%` }} />
          </div>
        </div>
      ))}
    </>
  );
}

/** Frise des jalons : prévision et réel. */
function Frise({ rows }: { rows: TimelineRow[] }) {
  if (rows.length === 0) return <p className="text-muted mb-0">Aucun jalon renseigné (voir « Suivi Avcmnt »).</p>;
  return (
    <div className="table-responsive">
      <table className="table table-sm mb-0">
        <thead className="thead-light"><tr><th>Ordre</th><th>Jalon</th><th>Prévision</th><th>Réel</th><th>Statut</th></tr></thead>
        <tbody>
          {rows.map((r) => {
            const statut = r.reel ? (r.prevision && new Date(r.prevision) < new Date(r.reel) ? 'TERMINE_EN_RETARD' : 'TERMINE')
              : r.prevision && new Date(r.prevision) < new Date() ? 'EN_RETARD' : 'EN_COURS';
            const range = (a: string | null, b: string | null) => [a, b].filter(Boolean).map((d) => formatDate(d)).join(' → ');
            return (
              <tr key={r.rang + (r.title ?? '')}>
                <td>{r.rang}</td><td>{r.title}</td><td>{range(r.debutPrevision, r.prevision)}</td>
                <td>{range(r.debutReel, r.reel)}</td><td><Statut value={statut} /></td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function DashboardView({ data, what }: { data: Dashboard; what: string }) {
  const pct = (data.avancementMoyen * 100).toLocaleString('fr-FR', { maximumFractionDigits: 2 });
  const shown = Object.keys(data.districtMap).length;
  // Projet : « PROGRAMME - projet » ; programme : son nom.
  const name = what === 'projet' && data.programmeTitle ? `${data.programmeTitle} - ${data.title ?? ''}` : data.title ?? '';
  const kpi = 'col-12';
  // Carte : districts bénéficiaires dans la couleur du programme (non cliquable).
  const districts = Object.fromEntries(Object.keys(data.districtMap).map((code) => [code, data.color]));
  return (
    <>
      <div className="row">
        {/* Gauche : indicateurs ; droite : carte des districts bénéficiaires. */}
        <div className="col-lg-6">
          <div className="row">
            <Kpi className={kpi} label={what === 'projet' ? `Budget total du projet : ${name}` : `Budget total du ${what} ${name}`} value={`${data.budget.toLocaleString('fr-FR')} MGA`} />
            <Kpi className={kpi} label={what === 'programme' ? 'Nombre de Projets Actifs' : 'Nombre de sous-projets Actifs'}
              value={`${data.projets} ${what === 'programme' ? 'projets' : 'projets'}`} />
            <Kpi className={kpi} label="Avancement Physique Moyen" value={`${pct} %`} />
            <Kpi className={kpi} label="Nombre de Districts Bénéficiaires" icon="ti-map-pin"
              value={`${data.districts} district${data.districts > 1 ? 's' : ''}`} />
            <Kpi className={kpi} label="Nombre de Communes Bénéficiaires" icon="ti-building-community"
              value={`${data.communes} commune${data.communes > 1 ? 's' : ''}`} />
          </div>
        </div>
        <div className="col-lg-6">
          <Card title={`Les ${shown} district${shown > 1 ? 's' : ''} bénéficiaire${shown > 1 ? 's' : ''} ${what === 'projet' ? 'du projet' : 'de'} ${name}`}>
            <div className="text-center" id="dashboard-map">
              <MadagascarMap districts={districts} names={data.districtMap} maxHeight={640} />
            </div>
          </Card>
        </div>
      </div>
      {data.indicateurs.length > 0 && (
        <Card title={`INDICATEURS DE PRODUIT - ${name}`}>
          <div className="row">
            {data.indicateurs.map((i) => (
              <div key={i.label} className="col-md-6 mb-2">
                <span className="text-muted">{i.label} : </span>
                <strong>{i.value.toLocaleString('fr-FR', { maximumFractionDigits: 2 })}</strong>
              </div>
            ))}
          </div>
        </Card>
      )}
      <div className="row">
        <div className="col-lg-6">
          <Card title={`SITUATION DES SOUS-PROJETS - ${name} (ACHEVE)`}><Bars bars={data.acheves} tone="success" /></Card>
        </div>
        <div className="col-lg-6">
          <Card title={`SITUATION DES SOUS-PROJETS - ${name} (ECHU)`}><Bars bars={data.echus} tone="danger" /></Card>
        </div>
      </div>
      <Card title={what === 'programme' ? 'SITUATION DU PROGRAMME' : `SITUATION DU PROJET ${name}`}>
        <Frise rows={data.frise} />
      </Card>
    </>
  );
}

/** Image PNG de la carte affichée (fond blanc), pour l'export : identique à l'écran. */
async function mapPng(): Promise<Blob | null> {
  const svg = document.querySelector<SVGSVGElement>('#dashboard-map svg');
  if (!svg) return null;
  const [, , w, h] = (svg.getAttribute('viewBox') ?? '0 0 450 800').split(/\s+/).map(Number);
  const scale = 2;
  const clone = svg.cloneNode(true) as SVGSVGElement;
  clone.setAttribute('xmlns', 'http://www.w3.org/2000/svg');
  clone.setAttribute('width', String(w * scale));
  clone.setAttribute('height', String(h * scale));
  clone.removeAttribute('style');
  const image = new Image();
  image.src = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(new XMLSerializer().serializeToString(clone));
  await image.decode();
  const canvas = document.createElement('canvas');
  canvas.width = w * scale;
  canvas.height = h * scale;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = '#ffffff';
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.drawImage(image, 0, 0, canvas.width, canvas.height);
  return new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
}

/** /admin/project/programme/dashboard/{id} — tableau de bord d'un programme (export PDF / Word). */
/** Boutons « Exporter PDF / Word » d'un tableau de bord ({@code url} : point d'export, carte jointe en image). */
function useExport(url: string) {
  const [exporting, setExporting] = useState<string | null>(null);
  const [exportError, setExportError] = useState<string | null>(null);
  const exportAs = async (format: 'pdf' | 'docx') => {
    setExporting(format);
    setExportError(null);
    try {
      const form = new FormData();
      const png = await mapPng();
      if (png) form.append('map', png, 'carte.png');
      await download(`${url}?format=${format}`, { method: 'POST', body: form });
    } catch (e) {
      setExportError(e instanceof ApiError ? e.message : 'Export impossible');
    } finally {
      setExporting(null);
    }
  };
  const buttons = (
    <span className="me-3">
      {(['pdf', 'docx'] as const).map((f) => (
        <button key={f} type="button" className="btn btn-sm btn-outline-primary me-2" disabled={exporting !== null}
          onClick={() => void exportAs(f)}>
          {exporting === f ? <span className="spinner-border spinner-border-sm me-1" role="status" />
            : <i className={'las ' + (f === 'pdf' ? 'la-file-pdf' : 'la-file-word') + ' me-1'}></i>}
          Exporter {f === 'pdf' ? 'PDF' : 'Word'}
        </button>
      ))}
    </span>
  );
  return { buttons, exportError };
}

export function ProgrammeDashboardPage() {
  const { id = '' } = useParams();
  const { data, error, loading } = useApi(() => trackingApi.programmeDashboard(id), [id]);
  const { buttons, exportError } = useExport(`/api/dashboards/programme/${encodeURIComponent(id)}/export`);
  const exportButtons = data && buttons;
  return (
    <>
      <PageTitle title={`TABLEAU DE BORD - ${data?.title ?? ''}`} breadcrumb={['Programme', 'Tableau de bord']} actions={exportButtons} />
      {exportError && <ErrorAlert message={exportError} />}
      {loading && <Loading />}
      {error && <ErrorAlert message={error} />}
      {data && <DashboardView data={data} what="programme" />}
    </>
  );
}

/** /admin/project/programme/{programmeId}/dashboard/{id} — tableau de bord d'un projet (sous-programme). */
export function SubProgrammeDashboardPage() {
  const { programmeId = '', id = '' } = useParams();
  const { data, error, loading } = useApi(() => trackingApi.subProgrammeDashboard(programmeId, id), [programmeId, id]);
  const { buttons, exportError } = useExport(
    `/api/dashboards/programme/${encodeURIComponent(programmeId)}/subProgramme/${encodeURIComponent(id)}/export`);
  return (
    <>
      <PageTitle title={`Tableau de bord projet : ${data?.programmeTitle ? `${data.programmeTitle} - ` : ''}${data?.title ?? ''}`} breadcrumb={['Projet', 'Tableau de bord']}
        actions={data && buttons} />
      {exportError && <ErrorAlert message={exportError} />}
      {loading && <Loading />}
      {error && <ErrorAlert message={error} />}
      {data && <DashboardView data={data} what="projet" />}
    </>
  );
}
