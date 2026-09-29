import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { LeaveLink } from '../../components/ConfirmDialog';
import { DataTable } from '../../components/DataTable';
import { PageTitle } from '../../components/PageTitle';
import { AddButton, Card, ErrorAlert, FormActions, FormRow, Loading, TextField, useApi, useSubmit } from '../../components/common';
import { SearchSelect } from '../../components/SearchSelect';
import { adminApi, type SchoolRow } from '../../services/adminApi';

const LIST = '/admin/school/list';

/** /admin/school/list — applications (ADMIN uniquement) : ID, Nom du compte, Adresse, Nom de domaine. */
export function SchoolsPage() {
  const { data, error, loading } = useApi(adminApi.schools);
  return (
    <>
      <PageTitle title="Applications" breadcrumb={['Administration', 'Applications']} />
      <Card title="Liste des applications" actions={<AddButton to="/admin/school/edit/new" />}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && (
          <DataTable<SchoolRow>
            rows={data} rowKey={(s) => s.externalId} pageSize={20}
            columns={[
              { key: 'id', header: 'ID', value: (s) => s.externalId },
              { key: 'name', header: 'Nom du compte', value: (s) => s.name },
              { key: 'address', header: 'Adresse', value: (s) => s.address },
              { key: 'domain', header: 'Nom de domaine', value: (s) => s.domainName },
              { key: 'actions', header: 'Action', className: 'text-end',
                render: (s) => <Link to={`/admin/school/edit/${s.externalId}`}><i className="las la-pen text-secondary font-16"></i></Link> },
            ]}
          />
        )}
      </Card>
    </>
  );
}

/** /admin/school/edit/{id|new} — fiche de l'application (Nom du compte, Nom de domaine, Année, Adresse, Description). */
export function SchoolDetailPage() {
  const { id = '' } = useParams();
  const isNew = id === 'new';
  const { data, error, loading } = useApi(() => (isNew ? Promise.resolve(null) : adminApi.school(id)), [id]);
  return (
    <>
      <PageTitle title="Application" breadcrumb={['Administration', { label: 'Applications', to: LIST }, isNew ? 'Nouvelle' : 'Modification']} />
      <Card title="Informations de l'application" actions={<LeaveLink className="btn btn-sm btn-outline-secondary" to={LIST}>Retour</LeaveLink>}>
        {loading && !isNew && <Loading />}
        {error && <ErrorAlert message={error} />}
        {(isNew || data) && <SchoolForm key={id} id={isNew ? null : id} initial={data} />}
      </Card>
    </>
  );
}

function SchoolForm({ id, initial }: { id: string | null; initial: SchoolRow | null }) {
  const navigate = useNavigate();
  const { saving, error, submit } = useSubmit();
  const [form, setForm] = useState({
    name: initial?.name ?? '', domainName: initial?.domainName ?? '', years: initial?.years ?? '',
    address: initial?.address ?? '', description: initial?.description ?? '', validatorId: initial?.validatorId ?? '',
  });
  // Validateur info : utilisateurs ADMINISTRATION de l'application (aucun tant qu'elle n'est pas créée).
  const { data: validators } = useApi(() => (id ? adminApi.schoolValidators(id) : Promise.resolve([])), [id]);
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit(() => (id ? adminApi.updateSchool(id, form) : adminApi.createSchool(form)), () => navigate(LIST));
  };

  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      {id
        ? <FormRow label="ID"><input className="form-control" readOnly value={id} /></FormRow>
        : <div className="alert alert-info py-2">L'identifiant de l'application est dérivé du nom du compte (minuscules, sans accents, espaces remplacés par « - »). Il ne pourra plus être changé.</div>}
      <TextField label="Nom du compte" name="name" value={form.name} onChange={set('name')} required maxLength={200} />
      <TextField label="Nom de domaine" name="domainName" value={form.domainName} onChange={set('domainName')} maxLength={200} />
      <TextField label="Année" name="years" value={form.years} onChange={set('years')} maxLength={50} />
      <TextField label="Adresse" name="address" value={form.address} onChange={set('address')} maxLength={500} />
      <FormRow label="Description" htmlFor="description">
        <textarea id="description" className="form-control" rows={3} maxLength={2000}
          value={form.description} onChange={(e) => set('description')(e.target.value)} />
      </FormRow>
      <FormRow label="Validateur info" htmlFor="validatorId">
        <SearchSelect id="validatorId" value={form.validatorId} onChange={set('validatorId')}
          placeholder={id ? '—' : 'Disponible après la création de l\'application'} disabled={!id}
          options={(validators ?? []).map((v) => ({ id: v.externalId, label: v.label }))} />
        <small className="text-muted">Seuls le validateur info et l'administrateur peuvent importer des fichiers Excel.</small>
      </FormRow>
      <FormActions saving={saving} cancelTo={LIST} deleteLabel={initial?.name}
        onDelete={id ? () => submit(() => adminApi.deleteSchool(id), () => navigate(LIST)) : undefined} />
    </form>
  );
}
