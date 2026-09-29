import { api } from './apiClient';

export interface NotificationItem {
  id: string; type: string; title: string | null; message: string | null; link: string | null; read: boolean; date: string;
}
export interface NotificationPage { items: NotificationItem[]; total: number; unread: number }
export interface NotificationPreference { type: string; label: string; description: string; app: boolean; mail: boolean }

/** Notifications de l'utilisateur connecté (les siennes seulement) et ses préférences. */
export const notificationApi = {
  list: (unread = false, page = 0, size = 20) => api<NotificationPage>(`/api/notifications?unread=${unread}&page=${page}&size=${size}`),
  unreadCount: () => api<{ unread: number }>('/api/notifications/unread-count'),
  read: (id: string) => api<void>(`/api/notifications/${encodeURIComponent(id)}/read`, { method: 'POST' }),
  readAll: () => api<void>('/api/notifications/read-all', { method: 'POST' }),
  preferences: () => api<NotificationPreference[]>('/api/notifications/preferences'),
  savePreferences: (prefs: NotificationPreference[]) =>
    api<NotificationPreference[]>('/api/notifications/preferences', { method: 'PUT', body: JSON.stringify(prefs) }),
};

/** Signale un changement (lecture…) : la cloche recompte les non lues. */
export const NOTIFICATIONS_CHANGED = 'efdl-notifications-changed';

/** « à l'instant », « il y a 5 min », « il y a 3 h », « il y a 2 j », sinon la date. */
export function timeAgo(value: string): string {
  const d = new Date(value);
  const s = Math.max(0, (Date.now() - d.getTime()) / 1000);
  if (s < 60) return "à l'instant";
  if (s < 3600) return `il y a ${Math.floor(s / 60)} min`;
  if (s < 86400) return `il y a ${Math.floor(s / 3600)} h`;
  if (s < 7 * 86400) return `il y a ${Math.floor(s / 86400)} j`;
  return d.toLocaleDateString('fr-FR');
}

export const NOTIFICATION_ICONS: Record<string, string> = {
  VALIDATION_ATTENTE: 'la-file-invoice-dollar text-primary', VALIDATION_RETARD: 'la-exclamation-triangle text-danger',
  FACTURE_VALIDEE: 'la-check-circle text-success', FACTURE_REJETEE: 'la-times-circle text-danger',
};
