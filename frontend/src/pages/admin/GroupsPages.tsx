import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { LeaveLink } from '../../components/ConfirmDialog';
import { DataTable } from '../../components/DataTable';
import { PageTitle } from '../../components/PageTitle';
import { AddButton, Card, ErrorAlert, FormActions, FormRow, Loading, TextField, useApi, useSubmit } from '../../components/common';
import { adminApi, type Group } from '../../services/adminApi';

const LIST = '/admin/user/group/list';

/** /admin/user/group/list — liste des groupes (colonnes : nom, chemins, actions). */
export function GroupsPage() {
  const { data, error, loading } = useApi(adminApi.groups);
  return (
    <>
      <PageTitle title="Groupes d'accès" breadcrumb={['Administration', 'Groupes']} />
      <Card title="Liste des groupes" actions={<AddButton to="/admin/user/group/edit/new" />}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && (
          <DataTable<Group>
            rows={data} rowKey={(g) => g.externalId} pageSize={20}
            columns={[
              { key: 'name', header: 'Nom', value: (g) => g.name },
              { key: 'paths', header: 'Chemins autorisés', value: (g) => g.allowedPaths.join(' ; '),
                render: (g) => g.allowedPaths.map((p) => <code key={p} className="me-2">{p}</code>) },
              { key: 'actions', header: 'Actions', className: 'text-end',
                render: (g) => <Link to={`/admin/user/group/edit/${g.externalId}`}><i className="las la-pen text-secondary font-16"></i></Link> },
            ]}
          />
        )}
      </Card>
    </>
  );
}

/** /admin/user/group/edit/{id|new} — fiche du groupe (Nom, Url autorisés séparés par « ; »). */
export function GroupDetailPage() {
  const { id = '' } = useParams();
  const isNew = id === 'new';
  const { data, error, loading } = useApi(() => (isNew ? Promise.resolve(null) : adminApi.group(id)), [id]);
  return (
    <>
      <PageTitle title="Groupe d'accès" breadcrumb={['Administration', { label: 'Groupes', to: LIST }, isNew ? 'Nouveau' : 'Modification']} />
      <Card title="Informations du groupe" actions={<LeaveLink className="btn btn-sm btn-outline-secondary" to={LIST}>Retour</LeaveLink>}>
        {loading && !isNew && <Loading />}
        {error && <ErrorAlert message={error} />}
        {(isNew || data) && <GroupForm key={id} id={isNew ? null : id} initial={data} />}
      </Card>
    </>
  );
}

function GroupForm({ id, initial }: { id: string | null; initial: Group | null }) {
  const navigate = useNavigate();
  const { saving, error, submit } = useSubmit();
  const [name, setName] = useState(initial?.name ?? '');
  const [paths, setPaths] = useState((initial?.allowedPaths ?? []).join(';\n'));

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    // Chemins séparés par « ; » (les retours à la ligne sont aussi acceptés).
    const form = { name, allowedPaths: paths.split(/[;\n]/).map((p) => p.trim()).filter(Boolean) };
    submit(() => (id ? adminApi.updateGroup(id, form) : adminApi.createGroup(form)), () => navigate(LIST));
  };

  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      <TextField label="Nom" name="name" value={name} onChange={setName} required maxLength={200} />
      <FormRow label="Url autorisés (séparés par « ; »)" htmlFor="paths">
        <textarea id="paths" className="form-control font-monospace" rows={Math.max(4, paths.split('\n').length + 1)}
          value={paths} onChange={(e) => setPaths(e.target.value)} placeholder={'/admin/menus;\n/admin/menu/.*'} />
        <small className="text-muted">Expressions régulières comparées à l'URL des écrans.</small>
      </FormRow>
      <FormActions saving={saving} cancelTo={LIST} />
    </form>
  );
}
