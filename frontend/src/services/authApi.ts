import { api } from './apiClient';
import type { CurrentUser, MenuItem, School } from '../types/api';

export const authApi = {
  login: (identifier: string, password: string) =>
    api<CurrentUser>('/api/auth/login', { method: 'POST', body: JSON.stringify({ identifier, password }) }),
  logout: () => api<void>('/api/auth/logout', { method: 'POST' }),
  me: () => api<CurrentUser>('/api/auth/me'),
  changePassword: (currentPassword: string, newPassword: string) =>
    api<void>('/api/auth/password', { method: 'POST', body: JSON.stringify({ currentPassword, newPassword }) }),
  tenant: () => api<School | undefined>('/api/tenant'),
};

export const menuApi = {
  mine: () => api<MenuItem[]>('/api/menus/me'),
};
