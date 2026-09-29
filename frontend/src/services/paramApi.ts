import { api } from './apiClient';

export type FieldType = 'TEXT' | 'TEXTAREA' | 'INT' | 'DOUBLE' | 'DATE' | 'BOOLEAN' | 'ENUM' | 'REF' | 'REF_SET';

export interface ParamField {
  name: string; label: string; type: FieldType; required: boolean; editable: boolean;
  ref: string | null; options: { value: string; label: string }[]; maxLength: number;
  /** Saisi à la création seulement (parents et code d'une localisation), ensuite affiché. */
  createOnly: boolean;
  /** Étape de la fiche (onglet) ; null = fiche d'un seul bloc. */
  section: string | null;
  /** Mise en page de la fiche. */
  ui: FieldUi;
}
/**
 * layout : null = demi-largeur ; « full » = toute la largeur ; « break » = nouvelle ligne ; « right » = nouvelle ligne,
 * colonne de droite ; « hidden » = non affiché. labelFrom : « champ.attribut » (libellé lu dans la référence choisie).
 */
export interface FieldUi { layout: 'full' | 'break' | 'right' | 'hidden' | null; heading: string | null; labelFrom: string | null }
/** Présentation de la fiche : grille 2 colonnes, titres, boutons d'étapes. */
export interface FormDef { layout: 'grid' | null; pageTitle: string | null; editTitle: string | null; newTitle: string | null; steps: boolean }
/** Colonne ; {@code with}/{@code op} : valeurs combinées (« join » séparées par « | », « sum » additionnées). */
export interface ParamColumn { field: string; header: string; with: string[] | null; op: 'join' | 'sum' | null }
/**
 * Lien d'une ligne vers un autre écran : liste générique ({@code key}, {@code var} = identifiant de la ligne),
 * ou écran dédié ({@code path} avec {var} et {id} : sous-projets, tableau de bord, suivi d'avancement, « Voir »).
 */
export interface ChildLink { key: string | null; label: string; var: string | null; path: string | null }
export interface ParamMeta {
  key: string; title: string; section: string; entityLabel: string; creatable: boolean;
  columns: ParamColumn[]; fields: ParamField[]; filters: string[];
  /** Parents portés par l'URL (ex. ptaFinancementId). */
  scope: string[];
  /** Champs dont les choix dépendent du précédent (région → district → communes). */
  cascade: string[];
  children: ChildLink[];
  form: FormDef | null;
}
export interface Choice {
  id: string; label: string | null; parent: string | null; deleted: boolean;
  /** Champs de la référence exposés au formulaire (ex. textes de l'indicateur KPI). */
  attributes?: Record<string, unknown> | null;
}
export interface ParentLabel { var: string; id: string; label: string | null }
export type ParamRow = Record<string, unknown> & { externalId: string };
export type Refs = Record<string, Choice[]>;
export type Vars = Record<string, string>;

export interface ParamList {
  meta: ParamMeta; parents: ParentLabel[]; rows: ParamRow[]; refs: Refs;
  canCreate: boolean; canEdit: boolean; canDelete: boolean;
}
export interface ParamDetail { meta: ParamMeta; parents: ParentLabel[]; row: ParamRow | null; refs: Refs; canDelete: boolean }

const enc = encodeURIComponent;
const query = (vars: Vars) => {
  const q = new URLSearchParams(vars).toString();
  return q ? '?' + q : '';
};

/** Écrans génériques (paramètres, PTA…) : description et données viennent du backend. */
export const paramApi = {
  list: (key: string, vars: Vars = {}) => api<ParamList>(`/api/params/${enc(key)}${query(vars)}`),
  get: (key: string, vars: Vars, id: string) => api<ParamDetail>(`/api/params/${enc(key)}/${enc(id)}${query(vars)}`),
  create: (key: string, vars: Vars, values: Record<string, unknown>) =>
    api<{ externalId: string }>(`/api/params/${enc(key)}${query(vars)}`, { method: 'POST', body: JSON.stringify(values) }),
  update: (key: string, vars: Vars, id: string, values: Record<string, unknown>) =>
    api<ParamDetail>(`/api/params/${enc(key)}/${enc(id)}${query(vars)}`, { method: 'PUT', body: JSON.stringify(values) }),
  remove: (key: string, vars: Vars, id: string) =>
    api<void>(`/api/params/${enc(key)}/${enc(id)}${query(vars)}`, { method: 'DELETE' }),
};

/**
 * Écrans et routes. {@code :var} = parent porté par l'URL ; {@code parent} = écran
 * de niveau supérieur (bouton Retour de la liste).
 */
export interface ParamScreen { key: string; list: string; edit: string; parent?: string }

const simple = (base: string, key: string, singular: string): ParamScreen =>
  ({ key, list: `${base}/${key}`, edit: `${base}/${singular}/edit/:id` });

const PTA_FINANCEMENT = '/admin/pta/ptaFinancement/:ptaFinancementId';
const PTA_ACTIVITE = `${PTA_FINANCEMENT}/ptaActivite/:ptaActiviteId`;

export const PARAM_SCREENS: ParamScreen[] = [
  ...[
    ['categories', 'categorie'], ['secteurs', 'secteur'], ['infrastructures', 'infrastructure'],
    ['typeInfrastructures', 'typeInfrastructure'], ['typeTravaux', 'typeTravaux'], ['sousProjets', 'sousProjet'],
    ['avancements', 'avancement'], ['questions', 'question'], ['validateurs', 'validateur'], ['indicateurs', 'indicateur'],
  ].map(([key, singular]) => simple('/admin/project/parametre', key, singular)),
  ...[
    ['provinces', 'province'], ['regions', 'region'], ['districts', 'district'], ['communes', 'commune'], ['fokontanies', 'fokontany'],
  ].map(([key, singular]) => simple('/admin/dashboard/election', key, singular)),
  simple('/admin/project', 'programmes', 'programme'),
  { key: 'subProgrammes', list: '/admin/project/programme/:programmeId/subProgrammes',
    edit: '/admin/project/programme/:programmeId/edit/:id', parent: 'programmes' },
  simple('/admin/project', 'prestataires', 'prestataire'),
  { key: 'projects', list: '/admin/project/programme/:programmeId/subProgramme/:subProgrammeId/projects',
    edit: '/admin/project/programme/:programmeId/subProgramme/:subProgrammeId/edit/:id', parent: 'subProgrammes' },
  { key: 'allProjects', list: '/admin/project/projects', edit: '/admin/project/project/edit/:id' },
  { key: 'regionProjects', list: '/admin/project/:regionId/projects', edit: '/admin/project/:regionId/project/edit/:id' },
  { key: 'districtProjects', list: '/admin/project/district/:districtCode/projects', edit: '/admin/project/project/edit/:id' },
  { key: 'programmeValidateurs', list: '/admin/project/programme/validateur/:programmeId/validateurs',
    edit: '/admin/project/programme/validateur/:programmeId/edit/:id', parent: 'programmes' },
  simple('/admin/project', 'contrats', 'contrat'),
  simple('/admin/project', 'groupeContrats', 'groupeContrat'),
  { key: 'groupeContratLots', list: '/admin/project/groupeContrat/:groupeContratId/contrats',
    edit: '/admin/project/groupeContrat/:groupeContratId/edit/:id', parent: 'groupeContrats' },
  simple('/admin/project', 'paiements', 'paiement'),
  simple('/admin/pta', 'ptaFinancements', 'ptaFinancement'),
  { key: 'ptaActivites', list: `${PTA_FINANCEMENT}/ptaActivites`, edit: `${PTA_FINANCEMENT}/edit/:id`, parent: 'ptaFinancements' },
  { key: 'ptaSousActivites', list: `${PTA_ACTIVITE}/ptaSousActivites`, edit: `${PTA_ACTIVITE}/edit/:id`, parent: 'ptaActivites' },
];

function screen(key: string): ParamScreen {
  return PARAM_SCREENS.find((s) => s.key === key)!;
}

function fill(template: string, vars: Vars): string {
  return template.replace(/:(\w+)/g, (_, name: string) => enc(vars[name] ?? ''));
}

export function listPath(key: string, vars: Vars = {}): string {
  return fill(screen(key).list, vars);
}

export function editPath(key: string, vars: Vars, id: string): string {
  return fill(screen(key).edit, { ...vars, id });
}

/** Chemin d'un lien de ligne (écran générique ou écran dédié). */
export function childPath(child: ChildLink, vars: Vars, rowId: string): string {
  if (child.key) return listPath(child.key, { ...vars, [child.var!]: rowId });
  return child.path!.replace(/\{(\w+)\}/g, (_, name: string) => enc(name === 'id' ? rowId : vars[name] ?? ''));
}

export function parentScreen(key: string): string | undefined {
  return screen(key).parent;
}

/** Date ISO de l'API → jj/mm/aaaa. */
function formatDay(value: unknown): string {
  const d = new Date(String(value));
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleDateString('fr-FR', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

/** Valeur affichée d'un champ (libellé de la référence, de l'option, date…). */
export function displayValue(field: ParamField | undefined, value: unknown, refs: Refs): string {
  if (value === null || value === undefined || value === '') return '';
  if (!field) return String(value);
  if (field.type === 'REF') return refs[field.ref!]?.find((c) => c.id === value)?.label ?? '';
  if (field.type === 'REF_SET' && Array.isArray(value)) {
    return value.map((v) => refs[field.ref!]?.find((c) => c.id === v)?.label ?? '').filter(Boolean).join(', ');
  }
  if (field.type === 'ENUM') return field.options.find((o) => o.value === value)?.label ?? String(value);
  if (field.type === 'DATE') return formatDay(value);
  if (field.type === 'BOOLEAN') return value === true ? 'Oui' : 'Non';
  if (field.type === 'DOUBLE' && typeof value === 'number') return value.toLocaleString('fr-FR');
  // Population (entiers) : séparateur de milliers ; les autres entiers (années, numéros) restent tels quels.
  if (field.type === 'INT' && typeof value === 'number' && field.name.startsWith('population')) return value.toLocaleString('fr-FR');
  return String(value);
}
