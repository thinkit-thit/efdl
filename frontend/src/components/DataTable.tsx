import { useMemo, useState, type ReactNode } from 'react';

export interface Column<T> {
  key: string;
  header: string;
  /** Valeur utilisée pour la recherche et le tri. */
  value?: (row: T) => string | number | null | undefined;
  render?: (row: T) => ReactNode;
  className?: string;
  sortable?: boolean;
}

interface Props<T> {
  rows: T[];
  columns: Column<T>[];
  rowKey: (row: T) => string;
  pageSize?: number;
  emptyText?: string;
}

/**
 * Tableau Metrica avec recherche, tri et pagination côté navigateur — même usage que simple-datatables
 * côté navigateur (toutes les lignes sont chargées, la pagination est locale).
 */
export function DataTable<T>({ rows, columns, rowKey, pageSize = 10, emptyText = 'Aucun élément' }: Props<T>) {
  const [query, setQuery] = useState('');
  const [sort, setSort] = useState<{ key: string; asc: boolean } | null>(null);
  const [page, setPage] = useState(0);

  const text = (col: Column<T>, row: T) => {
    const v = col.value?.(row);
    return v === null || v === undefined ? '' : String(v);
  };

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    let result = q ? rows.filter((r) => columns.some((c) => c.value && text(c, r).toLowerCase().includes(q))) : rows;
    if (sort) {
      const col = columns.find((c) => c.key === sort.key);
      if (col?.value) {
        result = [...result].sort((a, b) => {
          const va = col.value!(a) ?? '';
          const vb = col.value!(b) ?? '';
          const cmp = typeof va === 'number' && typeof vb === 'number'
            ? va - vb : String(va).localeCompare(String(vb), 'fr', { numeric: true, sensitivity: 'base' });
          return sort.asc ? cmp : -cmp;
        });
      }
    }
    return result;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, columns, query, sort]);

  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const current = Math.min(page, pages - 1);
  const visible = filtered.slice(current * pageSize, current * pageSize + pageSize);

  const toggleSort = (col: Column<T>) => {
    if (!col.value || col.sortable === false) return;
    setSort((s) => (s?.key === col.key ? { key: col.key, asc: !s.asc } : { key: col.key, asc: true }));
  };

  return (
    <>
      <div className="d-flex justify-content-end mb-2">
        <input
          type="search" className="form-control form-control-sm" style={{ maxWidth: 260 }} placeholder="Rechercher…"
          value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }}
        />
      </div>
      <div className="table-responsive">
        <table className="table mb-0">
          <thead className="thead-light">
            <tr>
              {columns.map((c) => (
                <th
                  key={c.key} className={c.className}
                  style={c.value && c.sortable !== false ? { cursor: 'pointer', whiteSpace: 'nowrap' } : undefined}
                  onClick={() => toggleSort(c)}
                >
                  {c.header}
                  {sort?.key === c.key && <i className={'ms-1 ti ti-chevron-' + (sort.asc ? 'up' : 'down')}></i>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {visible.length === 0 && (
              <tr><td colSpan={columns.length} className="text-center text-muted">{emptyText}</td></tr>
            )}
            {visible.map((row) => (
              <tr key={rowKey(row)}>
                {columns.map((c) => <td key={c.key} className={c.className}>{c.render ? c.render(row) : text(c, row)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="d-flex justify-content-between align-items-center mt-3">
        <small className="text-muted">
          {filtered.length === 0 ? '0 élément' : `${current * pageSize + 1} à ${Math.min((current + 1) * pageSize, filtered.length)} sur ${filtered.length}`}
        </small>
        {pages > 1 && (
          <ul className="pagination pagination-sm mb-0">
            <li className={'page-item' + (current === 0 ? ' disabled' : '')}>
              <button className="page-link" onClick={() => setPage(current - 1)}>‹</button>
            </li>
            {Array.from({ length: pages }, (_, i) => (
              <li key={i} className={'page-item' + (i === current ? ' active' : '')}>
                <button className="page-link" onClick={() => setPage(i)}>{i + 1}</button>
              </li>
            ))}
            <li className={'page-item' + (current === pages - 1 ? ' disabled' : '')}>
              <button className="page-link" onClick={() => setPage(current + 1)}>›</button>
            </li>
          </ul>
        )}
      </div>
    </>
  );
}
