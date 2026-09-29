import { useEffect, useMemo, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import logoSm from '../assets/metrica/images/logo-sm.png';
import logoDark from '../assets/metrica/images/logo-dark.png';
import logoLight from '../assets/metrica/images/logo.png';
import type { MenuItem } from '../types/api';

interface Props {
  menu: MenuItem[];
  onTabClick: () => void;
}

/** Vrai si l'élément (ou un descendant) correspond à la page courante. */
function containsPath(item: MenuItem, pathname: string): boolean {
  return item.url === pathname || item.children.some((c) => containsPath(c, pathname));
}

function MenuLink({ item, className }: { item: MenuItem; className: string }) {
  if (item.urlExterne) {
    return <a className={className} href={item.urlExterne} target="_blank" rel="noreferrer">{item.title}</a>;
  }
  return <Link className={className} to={item.url ?? '#'}>{item.title}</Link>;
}

/**
 * Menu de gauche Metrica (leftbar-tab-menu), alimenté par GET /api/menus/me.
 * Niveau 1 = onglets à icônes (ti ti-{icon}), niveau 2 = liens ou sous-menus repliables, niveau 3 = liens.
 * Structure du menu du template Metrica (leftbar-tab-menu).
 */
export function Sidebar({ menu, onTabClick }: Props) {
  const { pathname } = useLocation();
  const activeParent = useMemo(
    () => menu.find((m) => containsPath(m, pathname))?.externalId ?? menu[0]?.externalId,
    [menu, pathname],
  );
  const [activeTab, setActiveTab] = useState<string | undefined>(activeParent);
  const [openItems, setOpenItems] = useState<Set<string>>(new Set());

  useEffect(() => {
    setActiveTab(activeParent);
    const open = new Set<string>();
    menu.forEach((p) => p.children.forEach((c) => { if (containsPath(c, pathname) && c.children.length) open.add(c.externalId); }));
    setOpenItems(open);
  }, [activeParent, menu, pathname]);

  const toggle = (id: string) => setOpenItems((prev) => {
    const next = new Set(prev);
    if (next.has(id)) next.delete(id); else next.add(id);
    return next;
  });

  return (
    <div className="leftbar-tab-menu">
      <div className="main-icon-menu">
        <Link to="/home" className="logo logo-metrica d-block text-center">
          <span><img src={logoSm} alt="logo-small" className="logo-sm" /></span>
        </Link>
        <div className="main-icon-menu-body">
          <div className="position-reletive h-100" style={{ overflowX: 'hidden', overflowY: 'auto' }}>
            <ul className="nav nav-tabs" role="tablist" id="tab-menu">
              {menu.map((parent) => (
                <li key={parent.externalId} className="nav-item" title={parent.title}>
                  <a
                    href={'#' + parent.externalId}
                    className={'nav-link' + (activeTab === parent.externalId ? ' active' : '')}
                    onClick={(e) => { e.preventDefault(); setActiveTab(parent.externalId); onTabClick(); }}
                  >
                    <i className={'ti ti-' + (parent.icon ?? 'point') + ' menu-icon'}></i>
                  </a>
                </li>
              ))}
            </ul>
          </div>
        </div>
      </div>

      <div className="main-menu-inner">
        <div className="topbar-left">
          <Link to="/home" className="logo">
            <span>
              <img src={logoDark} alt="logo-large" className="logo-lg logo-dark" />
              <img src={logoLight} alt="logo-large" className="logo-lg logo-light" />
            </span>
          </Link>
        </div>
        <div className="menu-body navbar-vertical tab-content" style={{ overflowY: 'auto' }}>
          {menu.map((parent) => (
            <div
              key={parent.externalId}
              id={parent.externalId}
              className={'main-icon-menu-pane tab-pane' + (activeTab === parent.externalId ? ' active show' : '')}
              role="tabpanel"
            >
              <div className="title-box">
                <h6 className="menu-title">{parent.title}</h6>
              </div>
              <div className="collapse navbar-collapse show">
                <ul className="navbar-nav">
                  {parent.children.map((item) => {
                    const hasChildren = item.children.length > 0;
                    const isOpen = openItems.has(item.externalId);
                    const isActive = containsPath(item, pathname);
                    return (
                      <li key={item.externalId} className={'nav-item' + (isActive ? ' menuitem-active' : '')}>
                        {hasChildren ? (
                          <>
                            <a
                              href={'#' + item.externalId}
                              className={'nav-link' + (isActive ? ' active' : '')}
                              // L'attribut affiche la flèche « > » de Metrica (tournée si ouvert) ;
                              // le dépliage reste géré ici (Bootstrap JS n'est pas chargé).
                              data-bs-toggle="collapse"
                              role="button"
                              aria-expanded={isOpen}
                              onClick={(e) => { e.preventDefault(); toggle(item.externalId); }}
                            >
                              {item.title}
                            </a>
                            <div className={'collapse' + (isOpen ? ' show' : '')}>
                              <ul className="nav flex-column">
                                {item.children.map((sub) => (
                                  <li key={sub.externalId} className="nav-item">
                                    <MenuLink item={sub} className={'nav-link' + (sub.url === pathname ? ' active' : '')} />
                                  </li>
                                ))}
                              </ul>
                            </div>
                          </>
                        ) : (
                          <MenuLink item={item} className={'nav-link' + (item.url === pathname ? ' active' : '')} />
                        )}
                      </li>
                    );
                  })}
                </ul>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
