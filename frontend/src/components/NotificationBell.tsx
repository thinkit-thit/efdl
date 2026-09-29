import { useCallback, useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useLeaveGuard } from './ConfirmDialog';
import {
  NOTIFICATION_ICONS, NOTIFICATIONS_CHANGED, notificationApi, timeAgo, type NotificationItem,
} from '../services/notificationApi';

const POLL_MS = 60_000;

/**
 * Cloche de la barre du haut : nombre de notifications non lues (rafraîchi chaque minute et à chaque page),
 * dernières notifications ; un clic marque comme lu et ouvre la page concernée.
 */
export function NotificationBell() {
  const [unread, setUnread] = useState(0);
  const [open, setOpen] = useState(false);
  const [items, setItems] = useState<NotificationItem[] | null>(null);
  const ref = useRef<HTMLLIElement>(null);
  const navigate = useNavigate();
  const leaveGuard = useLeaveGuard();
  const { pathname } = useLocation();

  const refresh = useCallback(() => {
    notificationApi.unreadCount().then((r) => setUnread(r.unread)).catch(() => {});
  }, []);

  useEffect(() => {
    refresh();
    const timer = setInterval(refresh, POLL_MS);
    window.addEventListener(NOTIFICATIONS_CHANGED, refresh);
    return () => { clearInterval(timer); window.removeEventListener(NOTIFICATIONS_CHANGED, refresh); };
  }, [refresh]);
  useEffect(() => { refresh(); }, [pathname, refresh]);

  useEffect(() => {
    if (!open) return;
    notificationApi.list(false, 0, 8).then((p) => { setItems(p.items); setUnread(p.unread); }).catch(() => setItems([]));
    const onDown = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  const openItem = async (n: NotificationItem) => {
    setOpen(false);
    if (!n.read) await notificationApi.read(n.id).catch(() => {});
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
    // Saisie en cours : même confirmation qu'un lien du menu.
    if (n.link) void leaveGuard(() => navigate(n.link!));
  };
  const readAll = async () => {
    await notificationApi.readAll().catch(() => {});
    setItems((list) => list?.map((n) => ({ ...n, read: true })) ?? null);
    window.dispatchEvent(new Event(NOTIFICATIONS_CHANGED));
  };

  return (
    <li className="dropdown notification-list" ref={ref}>
      <a className="nav-link dropdown-toggle arrow-none nav-icon" href="#" role="button" aria-label="Notifications"
        onClick={(e) => { e.preventDefault(); setOpen((o) => !o); }}>
        <i className="ti ti-bell"></i>
        {unread > 0 && <span className="alert-badge"></span>}
        {unread > 0 && <span className="badge bg-danger rounded-pill position-absolute" style={{ top: 12, right: 2, fontSize: 10 }}>
          {unread > 99 ? '99+' : unread}</span>}
      </a>
      <div className={'dropdown-menu dropdown-menu-end dropdown-lg pt-0' + (open ? ' show' : '')}
        style={open ? { right: 0, width: 360 } : undefined}>
        <h6 className="dropdown-item-text font-15 m-0 py-3 border-bottom d-flex justify-content-between align-items-center">
          Notifications
          {unread > 0 && (
            <a href="#" className="small fw-normal" onClick={(e) => { e.preventDefault(); void readAll(); }}>Tout marquer comme lu</a>
          )}
        </h6>
        <div style={{ maxHeight: 360, overflowY: 'auto' }}>
          {items === null && <div className="p-3 text-muted small">Chargement…</div>}
          {items?.length === 0 && <div className="p-3 text-muted small">Aucune notification.</div>}
          {items?.map((n) => (
            <a key={n.id} href="#" className={'dropdown-item py-2' + (n.read ? '' : ' bg-light')}
              onClick={(e) => { e.preventDefault(); void openItem(n); }}>
              <div className="d-flex align-items-start">
                <i className={'las ' + (NOTIFICATION_ICONS[n.type] ?? 'la-bell text-secondary') + ' font-22 me-2'}></i>
                <div className="text-wrap" style={{ minWidth: 0 }}>
                  <div className={n.read ? '' : 'fw-semibold'}>{n.title}</div>
                  <div className="small text-muted">{n.message}</div>
                  <div className="small text-muted">{timeAgo(n.date)}</div>
                </div>
              </div>
            </a>
          ))}
        </div>
        <Link to="/account/notifications" className="dropdown-item text-center text-primary border-top" onClick={() => setOpen(false)}>
          Voir toutes les notifications <i className="fi-arrow-right"></i>
        </Link>
      </div>
    </li>
  );
}
