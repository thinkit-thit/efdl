import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { LeaveLink } from '../../components/ConfirmDialog';
import { DataTable } from '../../components/DataTable';
import { PageTitle } from '../../components/PageTitle';
import { AddButton, Card, ErrorAlert, FormActions, FormRow, Loading, TextField, useApi, useSubmit } from '../../components/common';
import { adminApi, type MenuRow } from '../../services/adminApi';

const LIST = '/admin/menus';

/** /admin/menus — liste des menus (Ordre, Nom ou libellé, Lien, Élément parent). */
export function MenusPage() {
  const { data, error, loading } = useApi(adminApi.menus);
  return (
    <>
      <PageTitle title="Gestion des menus" breadcrumb={['Administration', 'Menus']} />
      <Card title="Liste des menus" actions={<AddButton to="/admin/menu/edit/new" />}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && (
          <DataTable<MenuRow>
            rows={data} rowKey={(m) => m.externalId} pageSize={20}
            columns={[
              { key: 'order', header: 'Ordre', value: (m) => m.order },
              { key: 'title', header: 'Nom ou libellé', value: (m) => m.title,
                render: (m) => <>{m.icon && <i className={'ti ti-' + m.icon + ' me-1'}></i>}{m.title}</> },
              { key: 'url', header: 'Lien', value: (m) => m.url ?? m.urlExterne },
              { key: 'parent', header: 'Élément parent', value: (m) => m.parentTitle },
              { key: 'actions', header: 'Modifier', className: 'text-end',
                render: (m) => <Link to={`/admin/menu/edit/${m.externalId}`}><i className="las la-pen text-secondary font-16"></i></Link> },
            ]}
          />
        )}
      </Card>
    </>
  );
}

/** /admin/menu/edit/{id|new} — fiche du menu (Libellé, Parent, Icon, Ordre, Lien, Path). */
export function MenuDetailPage() {
  const { id = '' } = useParams();
  const isNew = id === 'new';
  const menu = useApi(() => (isNew ? Promise.resolve(null) : adminApi.menu(id)), [id]);
  const parents = useApi(() => adminApi.menuParents(id), [id]);
  const error = menu.error ?? parents.error;
  return (
    <>
      <PageTitle title="Gestion des menus" breadcrumb={['Administration', { label: 'Menus', to: LIST }, isNew ? 'Nouveau' : 'Modification']} />
      <Card title="Info sur le menu" actions={<LeaveLink className="btn btn-sm btn-outline-secondary" to={LIST}>Retour</LeaveLink>}>
        {(menu.loading || parents.loading) && <Loading />}
        {error && <ErrorAlert message={error} />}
        {parents.data && (isNew || menu.data) && (
          <MenuForm key={id} id={isNew ? null : id} initial={menu.data} parents={parents.data} />
        )}
      </Card>
    </>
  );
}

function MenuForm({ id, initial, parents }: { id: string | null; initial: MenuRow | null; parents: MenuRow[] }) {
  const navigate = useNavigate();
  const { saving, error, submit } = useSubmit();
  const [form, setForm] = useState({
    title: initial?.title ?? '', icon: initial?.icon ?? '', parentId: initial?.parentId ?? '',
    order: String(initial?.order ?? 0), url: initial?.url ?? '', path: initial?.path ?? '',
  });
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }));

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const body = { ...form, order: Number(form.order) || 0 };
    submit(() => (id ? adminApi.updateMenu(id, body) : adminApi.createMenu(body)), () => navigate(LIST));
  };

  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      <TextField label="Libellé" name="title" value={form.title} onChange={set('title')} required maxLength={200} />
      <FormRow label="Parent" htmlFor="parentId">
        <select id="parentId" className="form-select" value={form.parentId} onChange={(e) => set('parentId')(e.target.value)}>
          <option value="">— Aucun (menu principal) —</option>
          {parents.map((p) => <option key={p.externalId} value={p.externalId}>{p.title}</option>)}
        </select>
      </FormRow>
      <FormRow label="Icon" htmlFor="icon">
        <div className="input-group">
          {form.icon && <span className="input-group-text"><i className={'ti ti-' + form.icon}></i></span>}
          <input id="icon" className="form-control" value={form.icon} maxLength={100} onChange={(e) => set('icon')(e.target.value)} />
        </div>
      </FormRow>
      <TextField label="Ordre" name="order" type="number" value={form.order} onChange={set('order')} />
      <TextField label="Lien" name="url" value={form.url} onChange={set('url')} maxLength={500} />
      <TextField label="Path" name="path" value={form.path} onChange={set('path')} maxLength={200} />
      <FormActions saving={saving} cancelTo={LIST} deleteLabel={initial?.title}
        onDelete={id ? () => submit(() => adminApi.deleteMenu(id), () => navigate(LIST)) : undefined} />
    </form>
  );
}
