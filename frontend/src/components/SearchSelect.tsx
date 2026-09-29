import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from 'react';

export interface SearchOption {
  id: string;
  label: string;
  /** Texte grisé devant le libellé (ex. « Analamanga | Antananarivo Renivohitra | »). */
  path?: string;
  disabled?: boolean;
}

const MAX_SHOWN = 200;

/** Minuscules sans accents : « é » trouve « e », comme on tape sur un clavier sans accents. */
function normalize(text: string): string {
  return text.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase();
}

/**
 * Liste déroulante avec recherche : un clic ouvre la liste, la zone de
 * recherche est dans la liste et filtre pendant la frappe (tous les mots, sans tenir compte des accents) ;
 * flèches + Entrée pour choisir, Échap pour fermer.
 */
export function SearchSelect({ id, value, options, onChange, placeholder = '—', disabled = false, size }: {
  id?: string; value: string; options: SearchOption[]; onChange: (value: string) => void;
  placeholder?: string; disabled?: boolean; size?: 'sm';
}) {
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const root = useRef<HTMLDivElement>(null);
  const search = useRef<HTMLInputElement>(null);
  const list = useRef<HTMLDivElement>(null);

  const current = options.find((o) => o.id === value);
  const matches = useMemo(() => {
    const words = normalize(query).split(/\s+/).filter(Boolean);
    if (!words.length) return options;
    return options.filter((o) => {
      const text = normalize((o.path ?? '') + ' ' + o.label);
      return words.every((w) => text.includes(w));
    });
  }, [options, query]);
  const shown = matches.slice(0, MAX_SHOWN);

  // Fermeture au clic en dehors.
  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => { if (root.current && !root.current.contains(e.target as Node)) setOpen(false); };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  // Élément actif visible pendant la navigation au clavier.
  useEffect(() => {
    list.current?.querySelector<HTMLElement>(`[data-index="${active}"]`)?.scrollIntoView({ block: 'nearest' });
  }, [active, open]);

  const openList = () => {
    if (disabled) return;
    setQuery('');
    setActive(Math.max(0, options.findIndex((o) => o.id === value)));
    setOpen(true);
    setTimeout(() => search.current?.focus(), 0);
  };
  const choose = (option: SearchOption | undefined) => {
    if (!option || option.disabled) return;
    select(option.id);
  };
  const select = (next: string) => {
    if (next !== value) {
      onChange(next);
      // Comme un vrai <select> : la saisie est signalée « modifiée » (alerte d'annulation).
      root.current?.dispatchEvent(new Event('change', { bubbles: true }));
    }
    setOpen(false);
  };
  const onKey = (e: KeyboardEvent) => {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((i) => Math.min(i + 1, shown.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((i) => Math.max(i - 1, 0)); }
    else if (e.key === 'Enter') { e.preventDefault(); choose(shown[active]); }
    else if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); setOpen(false); }
  };

  return (
    <div ref={root} className="position-relative">
      <button type="button" id={id} disabled={disabled} aria-haspopup="listbox" aria-expanded={open}
        className={'form-select text-start' + (size ? ' form-select-sm' : '')}
        style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
        onClick={() => (open ? setOpen(false) : openList())}
        onKeyDown={(e) => { if (!open && (e.key === 'ArrowDown' || e.key === 'Enter' || e.key === ' ')) { e.preventDefault(); openList(); } }}>
        {current
          ? <>{current.path && <span className="text-muted">{current.path} </span>}{current.label}</>
          : <span className="text-muted">{placeholder}</span>}
      </button>
      {open && (
        <div className="dropdown-menu show w-100 p-2 shadow" style={{ zIndex: 1050 }}>
          <input ref={search} className="form-control form-control-sm mb-2" placeholder="Rechercher…" value={query}
            aria-label="Rechercher" onKeyDown={onKey}
            onChange={(e) => { setQuery(e.target.value); setActive(0); }} />
          <div ref={list} role="listbox" style={{ maxHeight: 280, overflowY: 'auto' }}>
            {value && (
              <button type="button" className="dropdown-item text-muted small" onClick={() => select('')}>
                — Aucun —
              </button>
            )}
            {shown.map((o, i) => (
              <button type="button" key={o.id} data-index={i} role="option" aria-selected={o.id === value}
                disabled={o.disabled}
                className={'dropdown-item text-wrap' + (i === active ? ' active' : '') + (o.id === value ? ' fw-semibold' : '')}
                onMouseEnter={() => setActive(i)} onClick={() => choose(o)}>
                {o.path && <span className={i === active ? '' : 'text-muted'}>{o.path} </span>}{o.label}
              </button>
            ))}
            {matches.length === 0 && <div className="text-muted small px-2 py-1">Aucun résultat</div>}
            {matches.length > MAX_SHOWN && (
              <div className="text-muted small px-2 py-1">
                {matches.length - MAX_SHOWN} autres résultats : précisez la recherche.
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
