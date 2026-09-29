import { useCallback, useEffect, useState } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { ErrorBoundary } from '../components/ErrorBoundary';
import { ApiError } from '../services/apiClient';
import { menuApi } from '../services/authApi';
import type { MenuItem } from '../types/api';
import { Footer } from './Footer';
import { Sidebar } from './Sidebar';
import { Topbar } from './Topbar';

/**
 * Layout principal (template Metrica) : menu de gauche, barre du haut, contenu, pied de page.
 * Reproduit le comportement de assets/js/app.js du template : classe body "enlarge-menu" pour replier le menu,
 * repli automatique sous 1025 px.
 */
export function AppLayout() {
  const [menu, setMenu] = useState<MenuItem[]>([]);
  const [menuError, setMenuError] = useState<string | null>(null);
  const location = useLocation();

  useEffect(() => {
    menuApi.mine()
      .then(setMenu)
      .catch((e) => setMenuError(e instanceof ApiError ? e.message : 'Menu indisponible'));
  }, []);

  useEffect(() => {
    const body = document.body;
    if (window.screen.width < 1025) body.classList.add('enlarge-menu', 'enlarge-menu-all');
    return () => body.classList.remove('enlarge-menu', 'enlarge-menu-all');
  }, []);

  const toggleMenu = useCallback(() => document.body.classList.toggle('enlarge-menu'), []);
  const expandMenu = useCallback(() => document.body.classList.remove('enlarge-menu'), []);

  return (
    <>
      <Sidebar menu={menu} onTabClick={expandMenu} />
      <Topbar onToggleMenu={toggleMenu} />
      <div className="page-wrapper">
        <div className="page-content-tab">
          <div className="container-fluid">
            {menuError && <div className="alert alert-warning mt-3 mb-0">{menuError}</div>}
            <ErrorBoundary resetKey={location.pathname}>
              <Outlet />
            </ErrorBoundary>
          </div>
          <Footer />
        </div>
      </div>
    </>
  );
}
