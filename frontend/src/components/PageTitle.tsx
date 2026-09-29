import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';

/** Élément du fil d'Ariane : texte seul, ou lien (breadcrumb-item). */
export type Crumb = string | { label: string; to?: string };

interface Props {
  title: string;
  breadcrumb?: Crumb[];
  actions?: ReactNode;
}

/** En-tête de page Metrica (page-title-box). Le dernier élément du fil d'Ariane (page courante) n'est pas un lien. */
export function PageTitle({ title, breadcrumb = [], actions }: Props) {
  return (
    <div className="row">
      <div className="col-sm-12">
        <div className="page-title-box">
          <div className="float-end">
            {actions}
            {breadcrumb.length > 0 && (
              <ol className="breadcrumb">
                {breadcrumb.map((b, i) => {
                  const { label, to } = typeof b === 'string' ? { label: b, to: undefined } : b;
                  const last = i === breadcrumb.length - 1;
                  return (
                    <li key={label + i} className={'breadcrumb-item' + (last ? ' active' : '')}>
                      {to && !last ? <Link to={to}>{label}</Link> : label}
                    </li>
                  );
                })}
              </ol>
            )}
          </div>
          <h4 className="page-title">{title}</h4>
        </div>
      </div>
    </div>
  );
}
