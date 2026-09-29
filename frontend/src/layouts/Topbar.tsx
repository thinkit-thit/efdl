import { useEffect, useRef, useState, type MouseEvent as ReactMouseEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { NotificationBell } from '../components/NotificationBell';
import { UserAvatar } from '../components/UserAvatar';
import { useAuth } from '../contexts/AuthContext';

interface Props {
  onToggleMenu: () => void;
}

/**
 * Barre du haut Metrica (topbar / navbar-custom) :
 * bouton de repli du menu et menu utilisateur (Mot de passe, Paramettre, Deconnexion).
 * La recherche du template (exemple sans action) n’est pas affichée.
 */
export function Topbar({ onToggleMenu }: Props) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [sticky, setSticky] = useState(false);
  const ref = useRef<HTMLLIElement>(null);

  useEffect(() => {
    const onScroll = () => setSticky(window.scrollY >= 50);
    const onClick = (e: MouseEvent) => { if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false); };
    window.addEventListener('scroll', onScroll);
    document.addEventListener('click', onClick);
    return () => { window.removeEventListener('scroll', onScroll); document.removeEventListener('click', onClick); };
  }, []);

  const onLogout = async (e: ReactMouseEvent) => {
    e.preventDefault();
    await logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="topbar">
      <nav className={'navbar-custom' + (sticky ? ' nav-sticky' : '')} id="navbar-custom">
        <ul className="list-unstyled topbar-nav float-end mb-0">
          <NotificationBell />
          <li className="dropdown" ref={ref}>
            <a
              className="nav-link dropdown-toggle nav-user"
              href="#"
              role="button"
              aria-haspopup="false"
              aria-expanded={open}
              onClick={(e) => { e.preventDefault(); setOpen((o) => !o); }}
            >
              <div className="d-flex align-items-center">
                <UserAvatar userId={user?.externalId} size={65} className="rounded-circle me-2 thumb-sm" />
                <div>
                  <small className="d-none d-md-block font-11">{user?.school?.name}</small>
                  <span className="d-none d-md-block fw-semibold font-12">
                    {user?.displayName} <i className="mdi mdi-chevron-down"></i>
                  </span>
                </div>
              </div>
            </a>
            <div className={'dropdown-menu dropdown-menu-end' + (open ? ' show' : '')} style={open ? { right: 0 } : undefined}>
              <Link className="dropdown-item" to="/account/notifications">
                <i className="ti ti-bell font-16 me-1 align-text-bottom"></i> Mes notifications
              </Link>
              <Link className="dropdown-item" to="/account/profile">
                <i className="ti ti-user font-16 me-1 align-text-bottom"></i> Mon profil
              </Link>
              {user?.canViewAudit && (
                <Link className="dropdown-item" to="/admin/historique">
                  <i className="ti ti-history font-16 me-1 align-text-bottom"></i> Historique
                </Link>
              )}
              <Link className="dropdown-item" to="/account/password">
                <i className="ti ti-key font-16 me-1 align-text-bottom"></i> Mot de passe
              </Link>
              <a className="dropdown-item" href="#" onClick={(e) => e.preventDefault()}>
                <i className="ti ti-settings font-16 me-1 align-text-bottom"></i> Paramettre
              </a>
              <div className="dropdown-divider mb-0"></div>
              <a className="dropdown-item" href="/logout" onClick={onLogout}>
                <i className="ti ti-power font-16 me-1 align-text-bottom"></i> Deconnexion
              </a>
            </div>
          </li>
        </ul>
        <ul className="list-unstyled topbar-nav mb-0">
          <li>
            <button className="nav-link button-menu-mobile nav-icon" id="togglemenu" onClick={onToggleMenu}>
              <i className="ti ti-menu-2"></i>
            </button>
          </li>
        </ul>
      </nav>
    </div>
  );
}
