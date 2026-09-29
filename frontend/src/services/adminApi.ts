import { api } from './apiClient';

export interface Group { externalId: string; name: string; allowedPaths: string[] }
export interface MenuRow {
  externalId: string; title: string; url: string | null; urlExterne: string | null; icon: string | null;
  path: string | null; order: number; parentId: string | null; parentTitle: string | null;
}
export interface SchoolRow {
  externalId: string; name: string; domainName: string | null; years: string | null; address: string | null; description: string | null;
  validatorId: string | null;
}
export interface ValidatorChoice { externalId: string; label: string }
export interface UserRow {
  externalId: string; displayName: string; username: string; phone: string | null; address: string | null; inDate: string | null;
  gender: string | null; hasPhoto: boolean; lastLogin: string | null;
}
export interface UserList { users: UserRow[]; canEdit: boolean }
export interface GroupChoice { externalId: string; name: string; member: boolean }
export interface UserDetail {
  externalId: string | null; username: string | null; lastName: string | null; firstName: string | null; gender: string | null;
  phone: string | null; email: string | null; address: string | null; birthDate: string | null; birthPlace: string | null;
  cinNumber: string | null; cinDate: string | null; cinPlace: string | null; numeroCnaps: string | null; inDate: string | null;
  groups: GroupChoice[];
}

export interface GroupForm { name: string; allowedPaths: string[] }
export interface MenuForm { title: string; icon: string; parentId: string; order: number; url: string; path: string }
export interface SchoolForm { name: string; domainName: string; years: string; address: string; description: string; validatorId: string }
export interface UserForm {
  username: string; lastName: string; firstName: string; gender: string; phone: string; email: string; address: string;
  birthDate: string | null; birthPlace: string; cinNumber: string; cinDate: string | null; cinPlace: string;
  numeroCnaps: string; inDate: string | null; userGroupIds: string[];
}
export interface Created { externalId: string }

const json = (method: 'POST' | 'PUT', body: unknown): RequestInit => ({ method, body: JSON.stringify(body) });
const enc = encodeURIComponent;

/** API du socle administration. Les suppressions sont logiques (status=DELETED) côté serveur. */
export const adminApi = {
  groups: () => api<Group[]>('/api/admin/groups'),
  group: (id: string) => api<Group>(`/api/admin/groups/${encodeURIComponent(id)}`),
  menus: () => api<MenuRow[]>('/api/admin/menus'),
  menu: (id: string) => api<MenuRow>(`/api/admin/menus/${encodeURIComponent(id)}`),
  schools: () => api<SchoolRow[]>('/api/admin/schools'),
  school: (id: string) => api<SchoolRow>(`/api/admin/schools/${encodeURIComponent(id)}`),
  schoolValidators: (id: string) => api<ValidatorChoice[]>(`/api/admin/schools/${encodeURIComponent(id)}/validators`),
  users: () => api<UserList>('/api/admin/users'),
  user: (id: string) => api<UserDetail>(`/api/admin/users/${encodeURIComponent(id)}`),
  menuParents: (id: string) => api<MenuRow[]>(`/api/admin/menus/${enc(id)}/parents`),

  createGroup: (f: GroupForm) => api<Created>('/api/admin/groups', json('POST', f)),
  updateGroup: (id: string, f: GroupForm) => api<Group>(`/api/admin/groups/${enc(id)}`, json('PUT', f)),
  createMenu: (f: MenuForm) => api<Created>('/api/admin/menus', json('POST', f)),
  updateMenu: (id: string, f: MenuForm) => api<MenuRow>(`/api/admin/menus/${enc(id)}`, json('PUT', f)),
  createSchool: (f: SchoolForm) => api<Created>('/api/admin/schools', json('POST', f)),
  updateSchool: (id: string, f: SchoolForm) => api<SchoolRow>(`/api/admin/schools/${enc(id)}`, json('PUT', f)),
  createUser: (f: UserForm) => api<Created>('/api/admin/users', json('POST', f)),
  updateUser: (id: string, f: UserForm) => api<UserDetail>(`/api/admin/users/${enc(id)}`, json('PUT', f)),
  resetUserPassword: (id: string, password: string) =>
    api<void>(`/api/admin/users/${enc(id)}/password`, json('POST', { password })),

  deleteMenu: (id: string) => api<void>(`/api/admin/menus/${enc(id)}`, { method: 'DELETE' }),
  deleteSchool: (id: string) => api<void>(`/api/admin/schools/${enc(id)}`, { method: 'DELETE' }),
  deleteUser: (id: string) => api<void>(`/api/admin/users/${enc(id)}`, { method: 'DELETE' }),
};
