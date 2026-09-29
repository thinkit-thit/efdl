import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ImportCard } from '../../components/ImportCard';
import { MadagascarMap, mapCode } from '../../components/MadagascarMap';
import { PageTitle } from '../../components/PageTitle';
import { Card, ErrorAlert, Loading, useApi } from '../../components/common';
import { trackingApi, type DistrictStats, type LocalisationCounts } from '../../services/trackingApi';

const BASE = '/admin/dashboard/election';

/** Niveaux de localisation : libellé, clé de la réponse, icône, liste correspondante. */
const LEVELS: { label: string; key: keyof LocalisationCounts; icon: string; list: string }[] = [
  { label: 'Régions', key: 'regions', icon: 'ti-map-2', list: 'regions' },
  { label: 'Districts', key: 'districts', icon: 'ti-map-pin', list: 'districts' },
  { label: 'Communes', key: 'communes', icon: 'ti-building-community', list: 'communes' },
  { label: 'Fokontany', key: 'fokontany', icon: 'ti-home', list: 'fokontanies' },
];

/** Deux lectures de la carte : 5 nuances chacune, de la plus claire (peu) à la plus foncée (beaucoup). */
const MODES = {
  beneficiaires: {
    label: 'Bénéficiaire projet FDL', value: (s: Totals) => s.communes, unit: 'commune(s) bénéficiaire(s)',
    colors: ['#d8f3dc', '#95d5b2', '#52b788', '#2d6a4f', '#1b4332'],
  },
  population: {
    label: 'Population', value: (s: Totals) => s.population, unit: 'habitants',
    colors: ['#deebf7', '#9ecae1', '#6baed6', '#3182bd', '#08519c'],
  },
} as const;
type Mode = keyof typeof MODES;
type Totals = { communes: number; population: number; population18: number; names: string[]; code: string };

const fmt = (n: number) => n.toLocaleString('fr-FR');

/**
 * Classes de valeurs (jusqu'à 5, effectifs à peu près égaux) parmi les valeurs non nulles ; 0 = sans couleur.
 * Renvoie la classe d'une valeur (-1 si nulle) et, pour la légende, les bornes réelles de chaque classe.
 */
function classify(values: number[], k = 5) {
  const positive = values.filter((v) => v > 0).sort((a, b) => a - b);
  const distinct = new Set(positive).size;
  const n = Math.min(k, distinct);
  const cuts: number[] = [];
  for (let i = 1; i < n; i++) cuts.push(positive[Math.floor((i * positive.length) / n)]);
  const classOf = (v: number) => (v <= 0 || !n ? -1 : cuts.filter((c) => v >= c).length);
  const limits: { min: number; max: number }[] = [];
  positive.forEach((v) => {
    const c = classOf(v);
    limits[c] = limits[c] ? { min: Math.min(limits[c].min, v), max: Math.max(limits[c].max, v) } : { min: v, max: v };
  });
  return { n, classOf, limits };
}

/** /admin/dashboard/election/tbs — localisations : chiffres clés, districts (communes bénéficiaires, population) et carte. */
export function LocalisationDashboardPage() {
  const [reload, setReload] = useState(0);
  const [mode, setMode] = useState<Mode>('beneficiaires');
  const navigate = useNavigate();
  const counts = useApi(() => trackingApi.localisationCounts(), [reload]);
  const districts = useApi(() => trackingApi.localisationDistricts(), [reload]);

  // Totaux par tracé de la carte (districts renumérotés ou regroupés sur un même tracé additionnés).
  const byPath = useMemo(() => {
    const map: Record<string, Totals> = {};
    (districts.data ?? []).forEach((d: DistrictStats) => {
      if (!d.code) return;
      const path = mapCode(d.code);
      const t = map[path] ?? (map[path] = { communes: 0, population: 0, population18: 0, names: [], code: d.code });
      t.communes += d.communes;
      t.population += d.population;
      t.population18 += d.population18;
      if (d.name) t.names.push(d.name);
    });
    return map;
  }, [districts.data]);

  const current = MODES[mode];
  const scale = useMemo(() => classify(Object.values(byPath).map(current.value)), [byPath, current]);
  const colorOf = (v: number) => {
    const c = scale.classOf(v);
    // Moins de 5 classes (peu de valeurs différentes) : les nuances les plus foncées.
    return c < 0 ? null : current.colors[current.colors.length - scale.n + c];
  };
  const mapColors: Record<string, string | null> = {};
  const mapNames: Record<string, string> = {};
  Object.values(byPath).forEach((t) => {
    const v = current.value(t);
    const color = colorOf(v);
    if (color) mapColors[t.code] = color;
    mapNames[t.code] = `${t.names.join(', ')} : ${fmt(v)} ${current.unit}`;
  });

  return (
    <>
      <PageTitle title="Tableau de bord des localisations" breadcrumb={['Localisation', 'Tableau de bord']} />
      {(counts.loading && !counts.data) || (districts.loading && !districts.data) ? <Loading /> : null}
      {counts.error && <ErrorAlert message={counts.error} />}
      {districts.error && <ErrorAlert message={districts.error} />}
      {counts.data && (
        <div className="row">
          {LEVELS.map((l) => (
            <div key={l.key} className="col-md-6 col-xl">
              <div className="card"><div className="card-body">
                <div className="row d-flex justify-content-center">
                  <div className="col">
                    <p className="text-dark mb-0 fw-semibold">{l.label}</p>
                    <h3 className="my-1 font-22 fw-bold">{fmt(counts.data![l.key])}</h3>
                    <Link to={`${BASE}/${l.list}`} className="font-13">Voir la liste</Link>
                  </div>
                  <div className="col-auto align-self-center">
                    <i className={'ti ' + l.icon + ' font-24 align-self-center text-muted'}></i>
                  </div>
                </div>
              </div></div>
            </div>
          ))}
        </div>
      )}
      {districts.data && (
        <div className="row">
          <div className="col-lg-7">
            <Card title="Districts : communes bénéficiaires et population">
              <div className="table-responsive" style={{ maxHeight: 640, overflowY: 'auto' }}>
                <table className="table table-sm table-hover mb-0">
                  <thead className="thead-light" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
                    <tr>
                      <th>Nom district</th>
                      <th className="text-center">Nb communes bénéficiaires projet FDL</th>
                      <th className="text-end">Nombre population</th>
                      <th className="text-end">Nb population +18 ans</th>
                    </tr>
                  </thead>
                  <tbody>
                    {districts.data.map((d) => (
                      <tr key={d.code ?? d.name ?? ''}>
                        <td>{d.name}</td>
                        <td className="text-center">{fmt(d.communes)}</td>
                        <td className="text-end">{fmt(d.population)}</td>
                        <td className="text-end">{fmt(d.population18)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </Card>
          </div>
          <div className="col-lg-5">
            <Card title={current.label}
              actions={(
                <div className="btn-group btn-group-sm" role="group" aria-label="Lecture de la carte">
                  {(['population', 'beneficiaires'] as Mode[]).map((m) => (
                    <button key={m} type="button" className={'btn ' + (mode === m ? 'btn-primary' : 'btn-outline-primary')}
                      onClick={() => setMode(m)}>{MODES[m].label}</button>
                  ))}
                </div>
              )}>
              <div className="text-center">
                <MadagascarMap districts={mapColors} names={mapNames}
                  onDistrictClick={(code) => navigate(`/admin/project/district/${code}/projects`)} />
              </div>
              <div className="d-flex flex-wrap gap-3 justify-content-center mt-2 font-12">
                {scale.limits.map((l, c) => ({ l, c })).reverse().map(({ l, c }) => l && (
                  <span key={c} className="d-inline-flex align-items-center">
                    <span className="me-1" style={{ width: 14, height: 14, borderRadius: 3, display: 'inline-block',
                      background: current.colors[current.colors.length - scale.n + c] }} />
                    {l.min === l.max ? fmt(l.min) : `${fmt(l.min)} – ${fmt(l.max)}`}
                  </span>
                ))}
                <span className="d-inline-flex align-items-center">
                  <span className="me-1" style={{ width: 14, height: 14, borderRadius: 3, display: 'inline-block', background: '#e9ecef' }} />
                  0
                </span>
              </div>
            </Card>
          </div>
        </div>
      )}
      <ImportCard url="/api/imports/localisations" onImported={() => setReload((n) => n + 1)}
        columns={<>Feuille « base » : CodeProv, Province, CodeReg, REGION, CodeDist, DISTRICT, CodeCom, COMMUNE, CodeFKT,
          FOKONTANY ; feuille de population : ID Fokontany, Population, Population +18ans. Mise à jour par code : les noms,
          contacts et liens des sous-projets existants sont conservés, les nouveaux éléments sont créés, rien n'est supprimé.</>} />
    </>
  );
}
