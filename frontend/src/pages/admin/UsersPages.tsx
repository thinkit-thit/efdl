import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { LeaveLink, markSaved, useConfirm } from '../../components/ConfirmDialog';
import { PhotoEditor } from '../../components/PhotoEditor';
import { UserAvatar } from '../../components/UserAvatar';
import { DataTable } from '../../components/DataTable';
import { PageTitle } from '../../components/PageTitle';
import {
  AddButton, Card, ErrorAlert, FormActions, FormRow, Loading, TextField, formatDateTime, toInputDate, useApi, useSubmit,
} from '../../components/common';
import { adminApi, type UserDetail, type UserForm, type UserRow } from '../../services/adminApi';

const LIST = '/admin/user/administration/list';

/**
 * /admin/user/administration/list — liste des utilisateurs :
 * Nom & prénoms, Matricule, Contact, Adresse, Dernière connexion (à la place de la date d'embauche, 2026-09-26), Statut (« Approved »),
 * actions visibles seulement si l'utilisateur a le droit sur /admin/user/administration/edit/.
 */
export function UsersPage() {
  const { data, error, loading } = useApi(adminApi.users);
  return (
    <>
      <PageTitle title="Gestion des utilisateurs" breadcrumb={['Administration', 'Utilisateur']} />
      <Card title="Liste des utilisateurs" actions={data?.canEdit && <AddButton to="/admin/user/administration/edit/new" />}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && (
          <DataTable<UserRow>
            rows={data.users} rowKey={(u) => u.externalId}
            columns={[
              { key: 'name', header: 'Nom & prénoms', value: (u) => u.displayName,
                render: (u) => (
                  <Link to={`/admin/user/administration/edit/${u.externalId}`}>
                    <UserAvatar userId={u.hasPhoto ? u.externalId : null} size={65} gender={u.gender} className="rounded-circle thumb-xs me-2" />{u.displayName}
                  </Link>
                ) },
              { key: 'username', header: 'Matricule', value: (u) => u.username },
              { key: 'phone', header: 'Contact', value: (u) => u.phone },
              { key: 'address', header: 'Adresse', value: (u) => u.address },
              { key: 'lastLogin', header: 'Dernière connexion', value: (u) => u.lastLogin ?? '', render: (u) => formatDateTime(u.lastLogin) },
              { key: 'status', header: 'Statut', render: () => <span className="badge badge-soft-success">Approved</span> },
              ...(data.canEdit ? [{
                key: 'actions', header: 'Action', className: 'text-end',
                render: (u: UserRow) => <Link to={`/admin/user/administration/edit/${u.externalId}`}><i className="las la-pen text-secondary font-16"></i></Link>,
              }] : []),
            ]}
          />
        )}
      </Card>
    </>
  );
}

/**
 * /admin/user/administration/edit/{id|new} — « Fiche de renseignements ».
 * Le mot de passe n'est jamais affiché ; il peut être réinitialisé (provisoire, à changer à la connexion).
 */
export function UserDetailPage() {
  const { id = '' } = useParams();
  const isNew = id === 'new';
  const { data, error, loading } = useApi(() => adminApi.user(id), [id]);
  return (
    <>
      <PageTitle title="Gestion des utilisateurs" breadcrumb={['Administration', { label: 'Utilisateur', to: LIST }, isNew ? 'Nouveau' : 'Modification']} />
      <Card title="Fiche de renseignements" actions={<LeaveLink className="btn btn-sm btn-outline-secondary" to={LIST}>Retour</LeaveLink>}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && <UserEditForm key={id} id={isNew ? null : id} initial={data} />}
      </Card>
      {data && !isNew && <Card title="Photo"><PhotoEditor userId={id} gender={data.gender} /></Card>}
      {data && !isNew && <PasswordResetCard key={id} id={id} />}
    </>
  );
}

function UserEditForm({ id, initial }: { id: string | null; initial: UserDetail }) {
  const navigate = useNavigate();
  const { saving, error, submit } = useSubmit();
  const [form, setForm] = useState({
    username: initial.username ?? '', lastName: initial.lastName ?? '', firstName: initial.firstName ?? '',
    gender: initial.gender ?? '', phone: initial.phone ?? '', email: initial.email ?? '', address: initial.address ?? '',
    birthDate: toInputDate(initial.birthDate), birthPlace: initial.birthPlace ?? '', cinNumber: initial.cinNumber ?? '',
    cinDate: toInputDate(initial.cinDate), cinPlace: initial.cinPlace ?? '', numeroCnaps: initial.numeroCnaps ?? '',
    inDate: toInputDate(initial.inDate),
  });
  const [groups, setGroups] = useState(() => new Set(initial.groups.filter((g) => g.member).map((g) => g.externalId)));
  const set = (key: keyof typeof form) => (value: string) => setForm((f) => ({ ...f, [key]: value }));
  const toggle = (groupId: string) => setGroups((current) => {
    const next = new Set(current);
    if (next.has(groupId)) next.delete(groupId); else next.add(groupId);
    return next;
  });

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    const body: UserForm = {
      ...form,
      birthDate: form.birthDate || null, cinDate: form.cinDate || null, inDate: form.inDate || null,
      userGroupIds: [...groups],
    };
    submit(() => (id ? adminApi.updateUser(id, body) : adminApi.createUser(body)), () => navigate(LIST));
  };

  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      <div className="row">
        <div className="col-lg-6">
          <TextField label="Matricule" name="username" value={form.username} onChange={set('username')} required maxLength={100} />
          <TextField label="Nom(s)" name="lastName" value={form.lastName} onChange={set('lastName')} maxLength={200} />
          <TextField label="Prénom(s)" name="firstName" value={form.firstName} onChange={set('firstName')} maxLength={200} />
          <FormRow label="Genre" htmlFor="gender">
            <select id="gender" className="form-select" value={form.gender} onChange={(e) => set('gender')(e.target.value)}>
              <option value="">—</option>
              <option value="MALE">Masculin</option>
              <option value="FEMALE">Féminin</option>
            </select>
          </FormRow>
          <TextField label="Téléphone" name="phone" value={form.phone} onChange={set('phone')} maxLength={50} />
          <TextField label="E-mail" name="email" type="email" value={form.email} onChange={set('email')} maxLength={200} />
          <TextField label="Adresse" name="address" value={form.address} onChange={set('address')} maxLength={500} />
        </div>
        <div className="col-lg-6">
          <TextField label="Date de naissance" name="birthDate" type="date" value={form.birthDate} onChange={set('birthDate')} />
          <TextField label="Lieu de naissance" name="birthPlace" value={form.birthPlace} onChange={set('birthPlace')} maxLength={200} />
          <TextField label="Numéro CIN" name="cinNumber" value={form.cinNumber} onChange={set('cinNumber')} maxLength={50} />
          <TextField label="Fait le" name="cinDate" type="date" value={form.cinDate} onChange={set('cinDate')} />
          <TextField label="À" name="cinPlace" value={form.cinPlace} onChange={set('cinPlace')} maxLength={200} />
          <TextField label="Numéro CNAPS" name="numeroCnaps" value={form.numeroCnaps} onChange={set('numeroCnaps')} maxLength={50} />
          <TextField label="Date d'intégration" name="inDate" type="date" value={form.inDate} onChange={set('inDate')} />
        </div>
        <div className="col-12">
          <h5 className="mt-2 mb-3">Groupe d'accès (Rôles)</h5>
          <div className="row mb-3">
            {initial.groups.map((g) => (
              <div key={g.externalId} className="col-md-4">
                <div className="form-check">
                  <input className="form-check-input" type="checkbox" id={'g-' + g.externalId}
                    checked={groups.has(g.externalId)} onChange={() => toggle(g.externalId)} />
                  <label className="form-check-label" htmlFor={'g-' + g.externalId}>{g.name}</label>
                </div>
              </div>
            ))}
          </div>
        </div>
      </div>
      <FormActions saving={saving} cancelTo={LIST} deleteLabel={[initial.lastName, initial.firstName].filter(Boolean).join(' ') || initial.username || undefined}
        onDelete={id ? () => submit(() => adminApi.deleteUser(id), () => navigate(LIST)) : undefined} />
    </form>
  );
}

/** Réinitialisation du mot de passe : provisoire, l'utilisateur devra le changer à sa prochaine connexion. */
function PasswordResetCard({ id }: { id: string }) {
  const { saving, error, submit } = useSubmit();
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [mismatch, setMismatch] = useState(false);
  const [done, setDone] = useState(false);
  const ask = useConfirm();

  const onSubmit = async (e: FormEvent) => {
    e.preventDefault();
    setDone(false);
    setMismatch(password !== confirm);
    if (password !== confirm) return;
    if (!(await ask({
      title: 'Confirmation de réinitialisation', tone: 'warning', icon: 'las la-key',
      message: 'Êtes-vous sûr de vouloir réinitialiser le mot de passe de cet utilisateur ?',
      detail: 'Son mot de passe actuel ne fonctionnera plus ; il devra changer le mot de passe provisoire à sa prochaine connexion.',
      confirmLabel: 'Oui, réinitialiser',
    }))) return;
    submit(() => adminApi.resetUserPassword(id, password), () => {
      markSaved();
      setDone(true);
      setPassword('');
      setConfirm('');
    });
  };

  return (
    <Card title="Mot de passe">
      <form onSubmit={onSubmit} data-unsaved-guard>
        {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
        {mismatch && <div className="alert alert-danger py-2">La confirmation ne correspond pas.</div>}
        {done && <div className="alert alert-success py-2">Mot de passe provisoire enregistré : l'utilisateur devra le changer à sa prochaine connexion.</div>}
        <FormRow label="Mot de passe provisoire" htmlFor="newPassword">
          <input id="newPassword" type="password" className="form-control" autoComplete="new-password"
            value={password} onChange={(e) => setPassword(e.target.value)} required />
          <small className="text-muted">8 caractères minimum, avec au moins une lettre et un chiffre. Communiquez-le à l'utilisateur.</small>
        </FormRow>
        <FormRow label="Confirmation" htmlFor="confirmPassword">
          <input id="confirmPassword" type="password" className="form-control" autoComplete="new-password"
            value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
        </FormRow>
        <div className="row">
          <div className="col-sm-8 offset-sm-4">
            <button type="submit" className="btn btn-warning" disabled={saving || !password || !confirm}>
              <i className="las la-key me-1"></i>Réinitialiser le mot de passe
            </button>
          </div>
        </div>
      </form>
    </Card>
  );
}
