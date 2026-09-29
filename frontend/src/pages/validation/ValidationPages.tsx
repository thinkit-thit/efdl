import { Link, useNavigate, useParams } from 'react-router-dom';
import { AttachmentsCard } from '../../components/AttachmentsCard';
import { useConfirm } from '../../components/ConfirmDialog';
import { DataTable } from '../../components/DataTable';
import { PaymentStatusBadge, WaitingValidator } from '../../components/PaymentStatus';
import { PageTitle } from '../../components/PageTitle';
import { Card, ErrorAlert, Loading, formatDate, useApi, useSubmit } from '../../components/common';
import { validationApi, type ValidationRow } from '../../services/validationApi';

const LIST = '/admin/project/validationPaiements';
const money = (value: number | null | undefined) => (value ?? 0).toLocaleString('fr-FR');

/** Badge de statut (VALIDATE vert, REJECTED rouge, sinon bleu). */
function StatusBadge({ status }: { status: string }) {
  const tone = status === 'VALIDATE' ? 'success' : status === 'REJECTED' ? 'danger' : 'info';
  return <span className={`badge badge-soft-${tone}`}>{status}</span>;
}

/** /admin/project/validationPaiements — factures qui attendent la validation de l'utilisateur connecté. */
export function ValidationListPage() {
  const { data, error, loading } = useApi(validationApi.list);
  return (
    <>
      <PageTitle title="Validation Paiement" breadcrumb={[{ label: 'FDL PROJET', to: '/admin/project/programmes' }, 'Validation Paiement']} />
      <Card title="Factures en attente de votre validation">
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && (
          <DataTable<ValidationRow>
            rows={data} rowKey={(r) => r.externalId} pageSize={25} emptyText="Aucune facture n'attend votre validation"
            columns={[
              { key: 'date', header: 'Date', value: (r) => r.creationDate ?? '', render: (r) => formatDate(r.creationDate) },
              { key: 'numero', header: 'N° facture', value: (r) => r.numeroFacture },
              { key: 'intitule', header: 'Intitulé', value: (r) => r.intituleFacture },
              { key: 'prestataire', header: 'Prestataire', value: (r) => r.prestataire },
              { key: 'contrat', header: 'Contrat', value: (r) => r.contrat },
              { key: 'projet', header: 'Projet', value: (r) => r.projet },
              { key: 'montant', header: 'Montant MGA', value: (r) => r.montant, render: (r) => money(r.montant) },
              { key: 'validateur', header: 'Prochain validateur', value: (r) => r.prochainValidateur,
                render: (r) => <WaitingValidator name={r.prochainValidateur} attenteJours={r.attenteJours} delaiMax={r.delaiMax} enRetard={r.enRetard} /> },
              { key: 'status', header: 'Statut', value: (r) => r.status,
                render: (r) => <PaymentStatusBadge status={r.status} enRetard={r.enRetard} delaiMax={r.delaiMax} /> },
              { key: 'voir', header: 'Voir', className: 'text-end', render: (r) => (
                <Link to={`/admin/project/validationPaiement/edit/${r.externalId}`}><i className="las la-eye text-secondary font-16"></i></Link>) },
            ]}
          />
        )}
      </Card>
    </>
  );
}

function Info({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="mb-2 row">
      <label className="col-lg-3 col-form-label text-end">{label}</label>
      <div className="col-lg-6"><input className="form-control" readOnly value={value === null || value === undefined ? '' : String(value)} /></div>
    </div>
  );
}

/**
 * /admin/project/validationPaiement/edit/{id} — « Info sur le paiement » : facture en lecture seule, note et
 * check-list du validateur, VALIDER / REJETER (le serveur vérifie que c'est bien votre tour).
 */
export function ValidationDetailPage() {
  const { id = '' } = useParams();
  const navigate = useNavigate();
  const { data, error, loading } = useApi(() => validationApi.get(id), [id]);
  const { saving, error: actionError, submit } = useSubmit();

  const confirm = useConfirm();
  const decide = async (approve: boolean) => {
    const ok = await confirm(approve
      ? { title: 'Confirmation de validation', tone: 'primary', icon: 'las la-check-circle',
          message: 'Êtes-vous sûr de vouloir valider cette facture ?',
          detail: 'Elle passera au validateur suivant, ou sera validée définitivement si vous êtes le dernier.',
          confirmLabel: 'Oui, valider' }
      : { title: 'Confirmation de rejet', tone: 'danger', icon: 'las la-times-circle',
          message: 'Êtes-vous sûr de vouloir rejeter cette facture ?', detail: 'Elle ne pourra plus être validée.',
          confirmLabel: 'Oui, rejeter' });
    if (!ok) return;
    submit(() => (approve ? validationApi.validate(id) : validationApi.reject(id)), () => navigate(LIST));
  };

  const f = data?.facture;
  return (
    <>
      <PageTitle title="Gestion des paiements" breadcrumb={[{ label: 'FDL PROJET', to: '/admin/project/programmes' }, { label: 'Validation Paiement', to: LIST }, 'Facture']} />
      <Card title="Info sur le paiement" actions={f && <PaymentStatusBadge status={f.status} enRetard={f.enRetard} delaiMax={f.delaiMax} />}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {actionError && <div className="mb-3"><ErrorAlert message={actionError} /></div>}
        {data && f && (
          <>
            <Info label="Prestataire(BE/Ese/Frns)" value={f.prestataire} />
            <Info label="Numéro facture" value={f.numeroFacture} />
            <Info label="Intitulé facture" value={f.intituleFacture} />
            <Info label="Numéro Attachement" value={data.numeroAttachement} />
            <Info label="Intitulé Attachement" value={data.intituleAttachement} />
            <Info label="N° CONTRAT / SOUS-PROJET / PRESTATAIRE" value={[f.contrat, f.projet, f.prestataire].filter(Boolean).join(' / ')} />
            <Info label="Montant" value={money(f.montant)} />
            <Info label="Montant du contrat" value={money(data.montantContrat)} />
            <Info label="Montant payé" value={money(data.montantPaye)} />
            {data.etape && (
              <>
                <Info label="Étape de validation" value={`${data.niveau + 1} / ${data.etapes} — ${data.etape.title ?? ''} (${data.etape.userName ?? ''})`} />
                <div className="mb-2 row">
                  <label className="col-lg-3 col-form-label text-end">En attente</label>
                  <div className="col-lg-6 pt-2">
                    <WaitingValidator name={data.etape.userName ?? ' '} attenteJours={f.attenteJours} delaiMax={f.delaiMax} enRetard={f.enRetard} />
                  </div>
                </div>
                <Info label="NOTE A PRENDRE EN COMPTE AVANT DE VALIDER CE PAIEMENT" value={data.etape.description} />
                <div className="mb-2 row">
                  <label className="col-lg-3 col-form-label text-end">QUESTION A PRENDRE EN COMPTE AVANT DE VALIDER CE PAIEMENT</label>
                  <div className="col-lg-6 pt-2">
                    {data.questions.length === 0 && <span className="text-muted">Aucune</span>}
                    {data.questions.map((q, i) => <p key={i} className="mb-1">{i + 1}-{q}</p>)}
                  </div>
                </div>
              </>
            )}
            {data.historique.length > 0 && (
              <div className="mb-2 row">
                <label className="col-lg-3 col-form-label text-end">Historique</label>
                <div className="col-lg-6 pt-2">
                  {data.historique.map((h, i) => (
                    <p key={i} className="mb-1"><StatusBadge status={h.status} /> {h.userName} — {formatDate(h.date)}</p>
                  ))}
                </div>
              </div>
            )}
            {!data.canValidate && f.status === 'PENDING' && (
              <div className="alert alert-info py-2 mt-3">Cette facture n'attend pas votre validation.</div>
            )}
            <div className="mt-3">
              {data.canValidate && (
                <>
                  <button type="button" className="btn btn-primary btn-sm px-4 me-2" disabled={saving} onClick={() => decide(true)}>VALIDER</button>
                  <button type="button" className="btn btn-danger btn-sm px-4 me-2" disabled={saving} onClick={() => decide(false)}>REJETER</button>
                </>
              )}
              <Link to={LIST} className="btn btn-info btn-sm px-4 text-light">Revenir sur la liste</Link>
            </div>
          </>
        )}
      </Card>
      {data && <AttachmentsCard target="paiements" id={id} title="Documents de la facture" />}
    </>
  );
}
