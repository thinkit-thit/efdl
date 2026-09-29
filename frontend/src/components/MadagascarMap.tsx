import { DISTRICT_PATHS, MAP_VIEWBOX } from '../assets/map/districts';

/**
 * Codes de district sans tracé propre → tracé de la carte (découpage administratif importé le 28/09/2026) :
 * districts renumérotés (MANANARA-NORD, MAROANTSETRA, TOAMASINA I) et arrondissements d'Antananarivo, regroupés sur
 * le tracé de la ville (1107).
 */
const MAP_ALIASES: Record<string, string> = {
  '5401': '5202', '5402': '5203', '5305': '5308',
  '1108': '1107', '1109': '1107', '1110': '1107', '1111': '1107', '1112': '1107', '1114': '1107',
};

/** Tracé de la carte qui représente ce code de district. */
export function mapCode(code: string): string {
  return MAP_ALIASES[code] ?? code;
}

/**
 * Carte des 110 districts de Madagascar : districts donnés colorés (couleur fournie, gris par défaut), les autres en
 * gris clair. Cliquable seulement si {@code onDistrictClick} est fourni (le code rendu est celui du district, pas du tracé).
 */
export function MadagascarMap({ districts, names = {}, onDistrictClick, maxHeight = 560 }: {
  /** code du district → couleur (null = couleur par défaut) */
  districts: Record<string, string | null>;
  names?: Record<string, string>;
  onDistrictClick?: (code: string) => void;
  maxHeight?: number;
}) {
  // Premier district donné pour chaque tracé (codes renumérotés ou regroupés compris).
  const byPath: Record<string, { code: string; color: string | null }> = {};
  Object.entries(districts).forEach(([code, color]) => {
    const path = mapCode(code);
    if (!(path in byPath)) byPath[path] = { code, color };
  });
  return (
    <svg viewBox={MAP_VIEWBOX} style={{ maxHeight, width: '100%' }} role="img" aria-label="Carte des districts">
      {DISTRICT_PATHS.map((d) => {
        const shown = byPath[d.code];
        const clickable = !!shown && !!onDistrictClick;
        return (
          <path key={d.code} d={d.d} fillRule="evenodd" stroke="#f2f2f2" strokeWidth={1}
            fill={shown ? shown.color || '#6c757d' : '#e9ecef'} style={{ cursor: clickable ? 'pointer' : 'default' }}
            onClick={clickable ? () => onDistrictClick!(shown.code) : undefined}>
            <title>{(shown && names[shown.code]) ?? d.title}</title>
          </path>
        );
      })}
    </svg>
  );
}
