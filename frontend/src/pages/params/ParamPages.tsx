import { Fragment, useMemo, useRef, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { DataTable, type Column } from '../../components/DataTable';
import { deleteOptions, LeaveLink, markSaved, useConfirm, useLeaveGuard } from '../../components/ConfirmDialog';
import { AttachmentsCard } from '../../components/AttachmentsCard';
import { ImportCard } from '../../components/ImportCard';
import { PaymentStatusBadge, WaitingValidator } from '../../components/PaymentStatus';
import { SearchSelect, type SearchOption } from '../../components/SearchSelect';
import { PageTitle, type Crumb } from '../../components/PageTitle';
import { AddButton, Card, ErrorAlert, FormActions, FormRow, Loading, toInputDate, useApi, useSubmit } from '../../components/common';
import { ApiError } from '../../services/apiClient';
import { PROGRESS_STEP, PROGRESS_TAB } from '../../services/projectTabs';
import { ProjectProgressPanel } from '../project/TrackingPages';
import {
  childPath, displayValue, editPath, listPath, paramApi, parentScreen, type Choice, type ParamField, type ParamList, type ParamMeta,
  type ParamRow, type ParentLabel, type Refs, type Vars,
} from '../../services/paramApi';

/** Parents portés par l'URL (tous les paramètres de route sauf l'identifiant de la fiche). */
function useScopeVars(): Vars {
  const params = useParams();
  return useMemo(() => Object.fromEntries(Object.entries(params).filter(([k, v]) => k !== 'id' && k !== '*' && v)) as Vars,
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [JSON.stringify(params)]);
}

/**
 * Fil d'Ariane cliquable (Accueil › Programme › Projet › Sous-Projet) : la rubrique mène à la liste de
 * premier niveau (ex. FDL PROJET → programmes), chaque parent à la liste de ses enfants (PDCID I → ses projets,
 * MOD → ses sous-projets), le titre à la liste de l'écran.
 */
function breadcrumb(key: string, vars: Vars, meta: ParamMeta | undefined, parents: ParentLabel[] | undefined,
                    last?: string): Crumb[] {
  if (!meta) return [];
  const chain = [key];
  for (let p = parentScreen(key); p && !chain.includes(p); p = parentScreen(p)) chain.unshift(p);
  const labels = parents ?? [];
  // Liens seulement si la chaîne des écrans correspond aux parents de l'URL.
  const linked = chain.length - 1 === meta.scope.length && labels.length === meta.scope.length;
  const varsUpTo = (n: number) => Object.fromEntries(meta.scope.slice(0, n).map((v) => [v, vars[v]]));
  const crumbs: Crumb[] = [
    { label: meta.section, to: linked ? listPath(chain[0]) : undefined },
    ...labels.map((p, i) => ({ label: p.label ?? '', to: linked ? listPath(chain[i + 1], varsUpTo(i + 1)) : undefined })),
    { label: meta.title, to: listPath(key, vars) },
  ];
  if (last) crumbs.push(last);
  return crumbs.filter((c) => (typeof c === 'string' ? c : c.label));
}

/** Liste de l'écran parent (bouton Retour d'une liste imbriquée, ex. activités → financements). */
function parentListPath(key: string, vars: Vars, meta: ParamMeta): string | null {
  const parent = parentScreen(key);
  if (!parent) return null;
  const parentVars = Object.fromEntries(meta.scope.slice(0, -1).map((v) => [v, vars[v]]));
  return listPath(parent, parentVars);
}

/**
 * Liste d'un écran : colonnes décrites par l'API, icônes Modif./Suppr. selon les droits
 * (isAuthorizedPath sur .../edit/ et .../delete/), filtres en cascade, liens vers les niveaux inférieurs.
 */
export function ParamListPage({ paramKey }: { paramKey: string }) {
  const vars = useScopeVars();
  const [reload, setReload] = useState(0);
  const [actionError, setActionError] = useState<string | null>(null);
  const [search] = useSearchParams();
  const { data, error, loading } = useApi(() => paramApi.list(paramKey, vars), [paramKey, reload, JSON.stringify(vars)]);

  const confirm = useConfirm();
  const onDelete = async (row: ParamRow, label: string) => {
    if (!(await confirm(deleteOptions(label)))) return;
    setActionError(null);
    try {
      await paramApi.remove(paramKey, vars, row.externalId);
      setReload((n) => n + 1);
    } catch (e) {
      setActionError(e instanceof ApiError ? e.message : 'Suppression impossible');
    }
  };

  const back = data ? parentListPath(paramKey, vars, data.meta) : null;
  const parentLabel = data?.parents.length ? ` — ${data.parents[data.parents.length - 1].label}` : '';
  return (
    <>
      <PageTitle title={data?.meta.title ?? ''} breadcrumb={breadcrumb(paramKey, vars, data?.meta, data?.parents)} />
      <Card title={data ? `Liste : ${data.meta.title}${parentLabel}` : 'Liste'}
        actions={<>
          {back && <Link className="btn btn-sm btn-outline-secondary me-2" to={back}>Retour</Link>}
          {data?.canCreate && <AddButton to={editPath(paramKey, vars, 'new') + (search.size ? '?' + search.toString() : '')} />}
        </>}>
        {loading && !data && <Loading />}
        {error && <ErrorAlert message={error} />}
        {actionError && <div className="mb-3"><ErrorAlert message={actionError} /></div>}
        {data && <ParamTable paramKey={paramKey} vars={vars} data={data} onDelete={onDelete} />}
      </Card>
      {data && <ListImport paramKey={paramKey} vars={vars} onImported={() => setReload((n) => n + 1)} />}
    </>
  );
}

/** Imports Excel des listes (prestataires, factures, sous-projets d'un sous-programme). */
function ListImport({ paramKey, vars, onImported }: { paramKey: string; vars: Vars; onImported: () => void }) {
  const [historique, setHistorique] = useState(true);
  if (paramKey === 'prestataires') {
    return <ImportCard url="/api/imports/prestataires" onImported={onImported}
      columns={<>Colonnes, dans l'ordre : N°, Dénomination, NIF, STAT, Adresse, RCS, Contact (1re ligne = en-têtes). Un prestataire existant (même N°) est mis à jour.</>} />;
  }
  if (paramKey === 'paiements') {
    return <ImportCard url="/api/imports/paiements" onImported={onImported} extra={{ historique: String(historique) }}
      columns={<>Colonnes, dans l'ordre : N° du contrat, N° facture, Intitulé facture, N° attachement, Intitulé attachement, Montant. Une facture déjà enregistrée (même contrat, même numéro) est ignorée.</>}
      options={(
        <div className="form-check ms-2">
          <input className="form-check-input" type="checkbox" id="historique" checked={historique}
            onChange={(e) => setHistorique(e.target.checked)} />
          <label className="form-check-label" htmlFor="historique">Factures historiques (déjà validées)</label>
        </div>
      )} />;
  }
  if (paramKey === 'projects' && vars.programmeId && vars.subProgrammeId) {
    const url = `/api/imports/programme/${encodeURIComponent(vars.programmeId)}/subProgramme/${encodeURIComponent(vars.subProgrammeId)}/projects`;
    return <ImportCard url={url}
      onImported={onImported}
      columns={<>Fiche de suivi des sous-projets (modèle complet ou court) : colonnes repérées par leur en-tête (N°Dossier, Code Commune, Sous-projet, Infrastructure, Commune, Fokontany, District, Montant prévisionnel BE / Ese, blocs « Dénomination » BE / entreprise / fournisseur…). Sous-projet mis à jour par N° dossier ; contrats créés si une colonne « N° contrat » est remplie.</>} />;
  }
  return null;
}

function ParamTable({ paramKey, vars, data, onDelete }: {
  paramKey: string; vars: Vars; data: ParamList; onDelete: (row: ParamRow, label: string) => void;
}) {
  const { meta, refs } = data;
  const [search, setSearch] = useSearchParams();
  const fieldOf = (name: string) => meta.fields.find((f) => f.name === name);
  const mainField = meta.fields.find((f) => f.required) ?? meta.fields[0];

  // Filtres (?provinceId=…&regionId=…) : chaque filtre restreint les choix du suivant.
  const filterValues = meta.filters.map((f) => search.get(f) ?? '');
  const setFilter = (index: number, value: string) => {
    const next = new URLSearchParams(search);
    meta.filters.forEach((f, i) => {
      if (i === index) {
        if (value) next.set(f, value); else next.delete(f);
      } else if (i > index) {
        next.delete(f);
      }
    });
    setSearch(next, { replace: true });
  };

  const rows = useMemo(() => data.rows.filter((r) => meta.filters.every((f, i) => !filterValues[i] || r[f] === filterValues[i])),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [data.rows, search]);

  const columns: Column<ParamRow>[] = meta.columns.map((c, i) => {
    const field = fieldOf(c.field);
    if (c.with && c.op === 'sum') {
      const total = (r: ParamRow) => [c.field, ...c.with!].reduce((sum, f) => sum + (Number(r[f]) || 0), 0);
      return { key: c.field + '#' + i, header: c.header, value: total, render: (r: ParamRow) => total(r).toLocaleString('fr-FR') };
    }
    if (c.with && c.op === 'join') {
      const joined = (r: ParamRow) => [c.field, ...c.with!].map((f) => displayValue(fieldOf(f), r[f], refs)).filter(Boolean).join(' | ');
      return { key: c.field + '#' + i, header: c.header, value: joined };
    }
    const sortable = field?.type === 'INT' || field?.type === 'DOUBLE' ? (r: ParamRow) => r[c.field] as number | null
      : field?.type === 'DATE' ? (r: ParamRow) => (r[c.field] as string | null) ?? ''
      : (r: ParamRow) => displayValue(field, r[c.field], refs);
    const showDate = field?.type === 'DATE' || field?.type === 'DOUBLE';
    // Factures : statut rouge si le délai de l'étape est dépassé, attente sous le prochain validateur.
    if (paramKey === 'paiements' && c.field === 'status') {
      return { key: c.field + '#' + i, header: c.header, value: sortable,
        render: (r: ParamRow) => <PaymentStatusBadge status={r.status} enRetard={r.enRetard} delaiMax={r.delaiMax} /> };
    }
    if (paramKey === 'paiements' && c.field === 'prochainValidateur') {
      return { key: c.field + '#' + i, header: c.header, value: sortable,
        render: (r: ParamRow) => <WaitingValidator name={r.prochainValidateur} attenteJours={r.attenteJours} delaiMax={r.delaiMax} enRetard={r.enRetard} /> };
    }
    return {
      key: c.field + '#' + i,
      header: c.header,
      value: sortable,
      render: c.field === mainField?.name && data.canEdit
        ? (r) => <Link to={editPath(paramKey, vars, r.externalId)}>{displayValue(field, r[c.field], refs)}</Link>
        : showDate ? (r) => displayValue(field, r[c.field], refs) : undefined,
    };
  });
  // Liens vers les écrans liés (colonnes « Activité. », « Projets », « Tableau de bord »…).
  for (const child of meta.children) {
    columns.push({ key: '_child_' + child.label, header: child.label, render: (r) => (
      <Link to={childPath(child, vars, r.externalId)}>
        <i className={'las ' + (child.key ? 'la-list' : 'la-arrow-right') + ' text-secondary font-16 me-1'}></i>{child.label}
      </Link>) });
  }
  if (data.canEdit) {
    columns.push({ key: '_edit', header: 'Modif.', className: 'text-end', render: (r) => (
      <Link to={editPath(paramKey, vars, r.externalId)}><i className="las la-pen text-secondary font-16"></i></Link>) });
  }
  if (data.canDelete) {
    columns.push({ key: '_delete', header: 'Suppr.', className: 'text-end', render: (r) => (
      <a href="#" onClick={(e) => { e.preventDefault(); onDelete(r, displayValue(mainField, r[mainField.name], refs)); }}>
        <i className="las la-trash-alt text-secondary font-16"></i>
      </a>) });
  }

  return (
    <>
      {meta.filters.length > 0 && (
        <div className="row g-2 mb-3">
          {meta.filters.map((f, i) => {
            const field = fieldOf(f)!;
            const parentValue = i > 0 ? filterValues[i - 1] : '';
            const options = (refs[field.ref!] ?? []).filter((c) => !c.deleted && (!parentValue || c.parent == null || c.parent === parentValue));
            return (
              <div key={f} className="col-md-3">
                <SearchSelect size="sm" value={filterValues[i]} placeholder={`${field.label} : toutes`}
                  options={options.map((c) => ({ id: c.id, label: c.label ?? '' }))} onChange={(v) => setFilter(i, v)} />
              </div>
            );
          })}
        </div>
      )}
      <DataTable<ParamRow> rows={rows} rowKey={(r) => r.externalId} pageSize={25} columns={columns} />
    </>
  );
}

/** Fiche d'un écran, retour à la liste après enregistrement. */
export function ParamEditPage({ paramKey }: { paramKey: string }) {
  const { id = '' } = useParams();
  const vars = useScopeVars();
  const { data, error, loading } = useApi(() => paramApi.get(paramKey, vars, id), [paramKey, id, JSON.stringify(vars)]);
  const isNew = id === 'new';
  return (
    <>
      <PageTitle title={data?.meta.form?.pageTitle ?? data?.meta.title ?? ''}
        breadcrumb={breadcrumb(paramKey, vars, data?.meta, data?.parents, isNew ? 'Nouveau' : 'Modification')} />
      <Card title={data ? cardTitle(data.meta, data.row, isNew) : ''}
        actions={(
          <>
            {!isNew && data && !PROJECT_SCREENS.includes(paramKey) && progressPath(paramKey, vars, id, data.row) && (
              <LeaveLink className="btn btn-outline-primary me-2" to={progressPath(paramKey, vars, id, data.row)!}>
                <i className="las la-tasks me-1"></i>Suivi d'avancement
              </LeaveLink>
            )}
            <LeaveLink className="btn btn-outline-secondary" to={listPath(paramKey, vars)}>Retour</LeaveLink>
          </>
        )}>
        {loading && <Loading />}
        {error && <ErrorAlert message={error} />}
        {data && <ParamForm key={id} paramKey={paramKey} vars={vars} id={isNew ? null : id} meta={data.meta}
          row={data.row} refs={data.refs} canDelete={data.canDelete} />}
      </Card>
      {!isNew && PROJECT_SCREENS.includes(paramKey) && <ProjectContrats projectId={id} />}
      {!isNew && paramKey === 'paiements' && <AttachmentsCard target="paiements" id={id} title="Documents de la facture" />}
    </>
  );
}

const PROJECT_SCREENS = ['projects', 'allProjects', 'regionProjects', 'districtProjects'];

/** Sous-projet : programme et sous-programme de son suivi d'avancement (null si un parent manque). */
function projectProgressTarget(vars: Vars, id: string, row: ParamRow | null) {
  const text = (v: unknown) => (typeof v === 'string' && v ? v : undefined);
  const programmeId = text(row?.programmeExternalId) ?? vars.programmeId;
  const subProgrammeId = text(row?.subProgrammeExternalId) ?? vars.subProgrammeId;
  return programmeId && subProgrammeId ? { programmeId, subProgrammeId, id } : null;
}

/**
 * Page de suivi d'avancement de la fiche : programme, projet (sous-programme) ou
 * sous-projet ; null pour les autres écrans ou si un parent manque.
 */
function progressPath(key: string, vars: Vars, id: string, row: ParamRow | null): string | null {
  const text = (v: unknown) => (typeof v === 'string' && v ? v : undefined);
  const enc = encodeURIComponent;
  if (key === 'programmes') return `/admin/project/programme/avancement/${enc(id)}`;
  const programme = text(row?.programmeExternalId) ?? vars.programmeId;
  if (key === 'subProgrammes') return programme ? `/admin/project/programme/${enc(programme)}/avancement/${enc(id)}` : null;
  if (!PROJECT_SCREENS.includes(key)) return null;
  const sub = text(row?.subProgrammeExternalId) ?? vars.subProgrammeId;
  return programme && sub ? `/admin/project/programme/${enc(programme)}/subProgramme/${enc(sub)}/avancement/${enc(id)}` : null;
}

/** « Liste des contrats » du sous-projet (bas de la fiche) ; masquée sans droit sur les contrats. */
function ProjectContrats({ projectId }: { projectId: string }) {
  const { data, error } = useApi(() => paramApi.list('contrats'), []);
  if (error || !data) return null;
  const rows = data.rows.filter((r) => r.projectExternalId === projectId);
  const field = (name: string) => data.meta.fields.find((f) => f.name === name);
  return (
    <Card title="Liste des contrats">
      {rows.length === 0 ? <p className="text-muted mb-0">Aucun contrat.</p> : (
        <div className="table-responsive">
          <table className="table table-sm mb-0">
            <thead className="thead-light">
              <tr><th>N° Contrat</th><th>Type</th><th>Prestataire</th><th>Montant contrat</th><th>Montant payé</th><th>Date contrat</th></tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.externalId}>
                  <td><Link to={editPath('contrats', {}, r.externalId)}>{String(r.title ?? '')}</Link></td>
                  <td>{displayValue(field('typePrestataire'), r.typePrestataire, data.refs)}</td>
                  <td>{displayValue(field('providerExternalId'), r.providerExternalId, data.refs)}</td>
                  <td>{displayValue(field('montantContrat'), r.montantContrat, data.refs)}</td>
                  <td>{Number(r.montantPayeValide ?? 0).toLocaleString('fr-FR')}</td>
                  <td>{displayValue(field('dateContrat'), r.dateContrat, data.refs)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Card>
  );
}

/** Titre de la carte (ex. « Sous Projet N°: {numero} », « NOUVEAU SOUS PROJET »). */
function cardTitle(meta: ParamMeta, row: ParamRow | null, isNew: boolean): string {
  const form = meta.form;
  if (isNew) return form?.newTitle ?? `Nouveau : ${meta.entityLabel}`;
  if (!form?.editTitle) return `Modifier : ${meta.entityLabel}`;
  return form.editTitle.replace(/\{(\w+)\}/g, (_, name: string) => String(row?.[name] ?? ''));
}

function initialValue(field: ParamField, row: ParamRow | null, preset: URLSearchParams): unknown {
  const v = row ? row[field.name] : preset.get(field.name);
  if (field.type === 'REF_SET') return Array.isArray(v) ? v : [];
  if (field.type === 'BOOLEAN') return v === true || v === 'true';
  if (v === null || v === undefined) return field.type === 'INT' || field.type === 'DOUBLE' ? '0' : '';
  if (field.type === 'DATE') return toInputDate(String(v));
  return String(v);
}

function ParamForm({ paramKey, vars, id, meta, row, refs, canDelete }: {
  paramKey: string; vars: Vars; id: string | null; meta: ParamMeta; row: ParamRow | null; refs: Refs; canDelete: boolean;
}) {
  const { fields, cascade } = meta;
  const navigate = useNavigate();
  const [search, setSearch] = useSearchParams();
  const { saving, error, submit } = useSubmit();
  const [values, setValues] = useState<Record<string, unknown>>(
    () => Object.fromEntries(fields.map((f) => [f.name, initialValue(f, row, search)])));
  const [saved, setSaved] = useState<string | null>(null);
  /** Dernières valeurs enregistrées : rétablies si l'on quitte une étape sans enregistrer. */
  const savedValues = useRef(values);
  const leaveGuard = useLeaveGuard();
  const isNew = id === null;
  const editable = (f: ParamField) => f.editable || (isNew && f.createOnly);
  const mainField = fields.find((f) => f.required) ?? fields[0];
  const fieldOf = (name: string) => fields.find((x) => x.name === name);
  const back = listPath(paramKey, vars);

  // Étapes (onglets) : ?step=N ; une création commence par l'étape 1.
  const sections = [...new Set(fields.map((f) => f.section).filter((x): x is string => !!x))];
  const stepNumber = Number(search.get('step') ?? 1);
  const stepIndex = Math.min(Math.max((Number.isFinite(stepNumber) ? stepNumber : 1) - 1, 0), Math.max(sections.length - 1, 0));
  // Sous-projet : onglet « Avancement » (?step=avancement), suivi d'avancement affiché dans la fiche.
  const progress = !isNew && id && PROJECT_SCREENS.includes(paramKey) ? projectProgressTarget(vars, id, row) : null;
  const onProgress = !!progress && search.get('step') === PROGRESS_STEP;
  const current = sections.length ? sections[isNew ? 0 : stepIndex] : null;
  const visibleFields = current ? fields.filter((f) => f.section === current) : fields;
  const goStep = (index: number) => {
    const next = new URLSearchParams(search);
    next.set('step', String(index + 1));
    setSearch(next, { replace: true });
    setSaved(null);
  };

  // Choix en cascade (province → région → …, région → district → communes) : changer un niveau vide les suivants.
  const set = (name: string, value: unknown) => setValues((v) => {
    const next = { ...v, [name]: value };
    const index = cascade.indexOf(name);
    if (index >= 0) {
      cascade.slice(index + 1).forEach((f) => { next[f] = fieldOf(f)?.type === 'REF_SET' ? [] : ''; });
    }
    return next;
  });
  const parentValue = (f: ParamField) => {
    const index = cascade.indexOf(f.name);
    return index > 0 ? values[cascade[index - 1]] : undefined;
  };
  const choicesFor = (f: ParamField): Choice[] => {
    const all = f.ref ? refs[f.ref] ?? [] : [];
    const index = cascade.indexOf(f.name);
    if (index <= 0) return all;
    const p = parentValue(f);
    return p ? all.filter((c) => c.parent === p) : [];
  };
  const waitingFor = (f: ParamField): string | null => {
    const index = cascade.indexOf(f.name);
    if (index <= 0 || parentValue(f)) return null;
    return fieldOf(cascade[index - 1])?.label ?? null;
  };

  /** Enregistre l'étape affichée (ou toute la fiche) ; {@code thenQuit} = retour à la liste. */
  const save = (thenQuit: boolean) => {
    const body = Object.fromEntries(visibleFields.filter(editable).map((f) => [f.name, values[f.name]]));
    if (isNew) {
      submit(async () => {
        const created = await paramApi.create(paramKey, vars, body);
        // On continue sur l'étape 2 de la fiche créée.
        if (sections.length > 1 && !thenQuit) navigate(editPath(paramKey, vars, created.externalId) + '?step=2');
        else navigate(back);
      }, () => {});
      return;
    }
    submit(async () => {
      const detail = await paramApi.update(paramKey, vars, id!, body);
      // Valeurs recalculées par le serveur (région, district, indicateur…).
      if (detail.row) {
        const fresh = Object.fromEntries(fields.map((f) => [f.name, initialValue(f, detail.row, search)]));
        savedValues.current = fresh;
        setValues(fresh);
      }
      markSaved();
    }, () => {
      const last = stepIndex >= sections.length - 1;
      if (thenQuit || !sections.length || last) navigate(back);
      else { goStep(stepIndex + 1); setSaved(current); }
    });
  };
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    save(false);
  };

  const grid = meta.form?.layout === 'grid';
  const steps = !!meta.form?.steps && sections.length > 1;
  /** Libellé fixe, ou lu dans la référence choisie (ex. texte « Indicateur produit 1 » de l'indicateur KPI). */
  const labelOf = (f: ParamField): string => {
    const source = f.ui?.labelFrom;
    if (!source) return f.label;
    const [fieldName, attribute] = source.split('.');
    const sourceField = fieldOf(fieldName);
    const choice = sourceField?.ref ? refs[sourceField.ref]?.find((c) => c.id === values[fieldName]) : undefined;
    const text = choice?.attributes?.[attribute];
    return typeof text === 'string' && text.trim() ? text : f.label;
  };
  const input = (f: ParamField) => (
    <>
      <FieldInput field={f} editable={editable(f)} value={values[f.name]} choices={choicesFor(f)}
        showPath={cascade.indexOf(f.name) <= 0}
        all={f.ref ? refs[f.ref] ?? [] : []} refs={refs} waitingFor={waitingFor(f)} onChange={(v) => set(f.name, v)} />
      {isNew && f.createOnly && f.name === 'code' && (
        <small className="text-muted">Laisser vide : code du parent suivi du numéro suivant (ex. 110101 → 110102).</small>
      )}
    </>
  );

  /** Autre étape sans enregistrer : confirmation si modifiée, valeurs enregistrées rétablies. */
  const leaveStep = (i: number) => void leaveGuard(() => { setValues(savedValues.current); goStep(i); });
  const openProgress = () => void leaveGuard(() => {
    setValues(savedValues.current);
    const next = new URLSearchParams(search);
    next.set('step', PROGRESS_STEP);
    setSearch(next, { replace: true });
    setSaved(null);
  });

  const tabs = sections.length > 1 && (
    <ul className="nav nav-tabs mb-3" role="tablist">
      {sections.map((sec, i) => (
        <li key={sec} className="nav-item">
          <a href="#" className={'nav-link' + (sec === current && !onProgress ? ' active' : '') + (isNew && i > 0 ? ' disabled' : '')}
            onClick={(e) => {
              e.preventDefault();
              if (!isNew && (onProgress || i !== stepIndex)) leaveStep(i);
            }}>{sec}</a>
        </li>
      ))}
      {progress && (
        <li className="nav-item">
          <a href="#" className={'nav-link' + (onProgress ? ' active' : '')}
            onClick={(e) => { e.preventDefault(); if (!onProgress) openProgress(); }}>{PROGRESS_TAB}</a>
        </li>
      )}
    </ul>
  );
  // Onglet « Avancement » : hors du formulaire de la fiche (le suivi a son propre formulaire).
  if (onProgress && progress) {
    return <>{tabs}<ProjectProgressPanel target={progress} /></>;
  }

  return (
    <form onSubmit={onSubmit} data-unsaved-guard>
      {tabs}
      {error && <div className="mb-3"><ErrorAlert message={error} /></div>}
      {saved && <div className="alert alert-success py-2">Étape « {saved} » enregistrée.</div>}
      {isNew && sections.length > 1 && (
        <div className="alert alert-info py-2">Enregistrez d'abord l'étape 1 ; les étapes suivantes s'ouvrent ensuite.</div>
      )}
      {grid ? (
        <div className="row">
          {visibleFields.filter((f) => f.ui?.layout !== 'hidden').map((f) => {
            const full = f.ui?.layout === 'full';
            const label = labelOf(f);
            const star = editable(f) && (f.required || (isNew && f.createOnly && cascade.includes(f.name)));
            return (
              <Fragment key={f.name}>
                {(f.ui?.heading || f.ui?.layout === 'break' || f.ui?.layout === 'right') && <div className="w-100" />}
                {f.ui?.heading && <h4 className="card-title mt-3 mb-3 col-12">{f.ui.heading}</h4>}
                {f.ui?.layout === 'right' && <div className="col-md-6" />}
                <div className={full ? 'col-md-12' : 'col-md-6'}>
                  <div className="mb-3 row">
                    <label htmlFor={f.name} className={(full ? 'col-lg-2' : 'col-lg-3') + ' col-form-label text-end'}>
                      {label}{star && <span className="text-danger"> *</span>}
                    </label>
                    <div className={full ? 'col-lg-10' : 'col-lg-9'}>
                      {input(f)}
                    </div>
                  </div>
                </div>
              </Fragment>
            );
          })}
        </div>
      ) : visibleFields.filter((f) => f.ui?.layout !== 'hidden').map((f) => (
        <FormRow key={f.name} label={labelOf(f)} htmlFor={f.name}
          required={editable(f) && (f.required || (isNew && f.createOnly && cascade.includes(f.name)))}>
          {input(f)}
        </FormRow>
      ))}
      {steps ? (
        // Boutons de la fiche en étapes : « Enregistrer et quitter » (étape 1) ou
        // « Précédent » à gauche (float-start), « Suivant » / « Terminer » à droite (float-end).
        // Pas de d-flex : le thème Metrica étire les .btn enfants d'un .d-flex (flex:1).
        <div className="clearfix mt-3">
          {isNew || stepIndex === 0 ? (
            <button type="button" className="btn btn-secondary float-start" disabled={saving} onClick={() => save(true)}>
              Enregistrer et quitter
            </button>
          ) : (
            <button type="button" className="btn btn-secondary float-start" disabled={saving} onClick={() => leaveStep(stepIndex - 1)}>
              Précédent
            </button>
          )}
          <button type="submit" className="btn btn-primary float-end" disabled={saving}>
            {saving && <span className="spinner-border spinner-border-sm me-1" role="status" />}
            {!isNew && stepIndex >= sections.length - 1 ? 'Terminer' : 'Suivant'}
          </button>
        </div>
      ) : (
        <FormActions saving={saving} cancelTo={back}
          deleteLabel={displayValue(mainField, row?.[mainField.name], refs) || undefined}
          onDelete={id && canDelete ? () => submit(() => paramApi.remove(paramKey, vars, id), () => navigate(back)) : undefined} />
      )}
      {sections.length > 1 && !steps && (
        <div className="row mt-2">
          <div className="col-sm-8 offset-sm-4">
            <button type="button" className="btn btn-link px-0" disabled={saving} onClick={() => save(true)}>
              Enregistrer et revenir à la liste
            </button>
          </div>
        </div>
      )}
    </form>
  );
}

/**
 * Champ selon son type. Une référence supprimée reste visible si elle est déjà choisie, mais n'est plus proposée ;
 * un choix déjà enregistré reste visible même s'il n'appartient plus au niveau parent sélectionné.
 */
function FieldInput({ field, editable, value, choices, all, refs, waitingFor, showPath = false, onChange }: {
  field: ParamField; editable: boolean; value: unknown; choices: Choice[]; all: Choice[]; refs: Refs;
  waitingFor: string | null; showPath?: boolean; onChange: (value: unknown) => void;
}) {
  const id = field.name;
  if (!editable) {
    return <input id={id} className="form-control" readOnly value={displayValue(field, value, refs)} />;
  }
  const deletedSuffix = (c: Choice) => (c.deleted ? ' (supprimé)' : '');
  switch (field.type) {
    case 'TEXTAREA':
      return <textarea id={id} className="form-control" rows={3} maxLength={field.maxLength}
        value={value as string} onChange={(e) => onChange(e.target.value)} />;
    case 'INT':
    case 'DOUBLE':
      return <input id={id} className="form-control" type="number" step={field.type === 'INT' ? 1 : 'any'}
        value={value as string} onChange={(e) => onChange(e.target.value)} />;
    case 'DATE':
      return <input id={id} className="form-control" type="date" value={value as string} onChange={(e) => onChange(e.target.value)} />;
    case 'BOOLEAN':
      return (
        <select id={id} className="form-select" value={value === true ? 'true' : 'false'}
          onChange={(e) => onChange(e.target.value === 'true')}>
          <option value="true">OUI</option>
          <option value="false">NON</option>
        </select>
      );
    case 'ENUM':
      return (
        <select id={id} className="form-select" value={value as string} onChange={(e) => onChange(e.target.value)}>
          <option value="">—</option>
          {field.options.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
        </select>
      );
    case 'REF': {
      const current = all.find((c) => c.id === value);
      const visible = choices.filter((c) => !c.deleted || c.id === value);
      if (current && !visible.includes(current)) visible.unshift(current);
      if (value && !current) {
        visible.unshift({ id: value as string, label: 'Élément introuvable : choisissez-en un autre', parent: null, deleted: true });
      }
      const byId = showPath ? choiceIndex(refs) : null;
      const options: SearchOption[] = visible.map((c) => ({
        id: c.id, label: (c.label ?? '') + deletedSuffix(c), disabled: c.deleted, path: byId ? pathOf(c, byId) : undefined,
      }));
      return (
        <SearchSelect id={id} value={(value as string) ?? ''} options={options} onChange={onChange}
          disabled={waitingFor !== null} placeholder={waitingFor ? `Choisissez d'abord : ${waitingFor}` : '—'} />
      );
    }
    case 'REF_SET': {
      const selected = new Set(value as string[]);
      const toggle = (cid: string) => {
        const next = new Set(selected);
        if (next.has(cid)) next.delete(cid); else next.add(cid);
        onChange([...next]);
      };
      const visible = choices.filter((c) => !c.deleted || selected.has(c.id));
      all.filter((c) => selected.has(c.id) && !visible.includes(c)).forEach((c) => visible.push(c));
      if (waitingFor && visible.length === 0) {
        return <p className="form-control-plaintext text-muted">Choisissez d'abord : {waitingFor}</p>;
      }
      if (visible.length === 0) return <p className="form-control-plaintext text-muted">Aucun choix disponible</p>;
      return (
        <div className="pt-2" style={{ maxHeight: 260, overflowY: 'auto' }}>
          {visible.map((c) => (
            <div key={c.id} className="form-check">
              <input className="form-check-input" type="checkbox" id={`${id}-${c.id}`} checked={selected.has(c.id)}
                disabled={c.deleted && !selected.has(c.id)} onChange={() => toggle(c.id)} />
              <label className="form-check-label" htmlFor={`${id}-${c.id}`}>{c.label}{deletedSuffix(c)}</label>
            </div>
          ))}
        </div>
      );
    }
    default:
      return <input id={id} className="form-control" maxLength={field.maxLength} required={field.required}
        value={value as string} onChange={(e) => onChange(e.target.value)} />;
  }
}

/** Tous les choix connus de la fiche, par identifiant (pour remonter les parents). */
function choiceIndex(refs: Refs): Map<string, Choice> {
  const index = new Map<string, Choice>();
  Object.values(refs).forEach((list) => list.forEach((c) => index.set(c.id, c)));
  return index;
}

/** Parents d'un choix, du plus haut au plus proche (ex. « Analamanga | Antananarivo Renivohitra | » d'une commune). */
function pathOf(choice: Choice, index: Map<string, Choice>): string | undefined {
  const labels: string[] = [];
  const seen = new Set<string>([choice.id]);
  for (let p = choice.parent ? index.get(choice.parent) : undefined; p && !seen.has(p.id); p = p.parent ? index.get(p.parent) : undefined) {
    seen.add(p.id);
    labels.unshift(p.label ?? '');
  }
  return labels.length ? labels.join(' | ') + ' |' : undefined;
}
