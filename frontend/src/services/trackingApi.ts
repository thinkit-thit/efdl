import { api } from './apiClient';

export interface Milestone {
  avancementId: string; rang: number; title: string | null; pourcentage: number; prevision: string | null; reel: string | null;
  debutPrevision: string | null; debutReel: string | null; evidence: string | null; statut: string | null;
}
export interface Progress { title: string | null; jalons: Milestone[]; pourcentage: number }
export interface MilestoneInput { avancementId: string; prevision: string; reel: string; evidence: string }

export interface Bar { label: string; rang: number; count: number }
export interface TimelineRow {
  rang: string; title: string | null; debutPrevision: string | null; prevision: string | null; debutReel: string | null; reel: string | null;
}
export interface Dashboard {
  title: string | null; budget: number; projets: number; avancementMoyen: number; acheves: Bar[]; echus: Bar[]; frise: TimelineRow[];
  /** Districts et communes bénéficiaires (distincts, d'après les sous-projets actifs). */
  districts: number; communes: number;
  /** Couleur du programme et districts bénéficiaires (code → nom) pour la carte. */
  color: string | null; districtMap: Record<string, string>;
  /** Projet : nom de son programme (null pour un programme). */
  programmeTitle: string | null;
  /** Indicateurs de produit 1 et 2 (libellé de l'indicateur KPI → total des sous-projets), totaux non nuls. */
  indicateurs: { label: string; value: number }[];
}
export interface ProgrammeCard {
  id: string; title: string | null; color: string | null; budget: number; avancementMoyen: number; projets: number;
  /** Communes bénéficiaires (distinctes, d'après la commune des sous-projets). */
  communes: number;
}
export interface Home {
  programmes: ProgrammeCard[]; programmeId: string | null; programmeTitle: string | null;
  districts: Record<string, string | null>; districtNames: Record<string, string>;
}

/** Niveau de suivi : programme, projet (sous-programme) ou sous-projet, avec les parents portés par l'URL. */
export interface ProgressTarget { programmeId?: string; subProgrammeId?: string; id: string }

const enc = encodeURIComponent;

function progressUrl(t: ProgressTarget): string {
  if (t.subProgrammeId) return `/api/progress/programme/${enc(t.programmeId!)}/subProgramme/${enc(t.subProgrammeId)}/project/${enc(t.id)}`;
  if (t.programmeId) return `/api/progress/programme/${enc(t.programmeId)}/subProgramme/${enc(t.id)}`;
  return `/api/progress/programme/${enc(t.id)}`;
}

/** Nombre d'éléments enregistrés par niveau de localisation (éléments supprimés exclus). */
export interface LocalisationCounts { provinces: number; regions: number; districts: number; communes: number; fokontany: number }

/** District : communes bénéficiaires des projets FDL et population (somme de ses fokontany). */
export interface DistrictStats { code: string | null; name: string | null; communes: number; population: number; population18: number }

export const trackingApi = {
  localisationCounts: () => api<LocalisationCounts>('/api/dashboards/localisation'),
  localisationDistricts: () => api<DistrictStats[]>('/api/dashboards/localisation/districts'),
  progress: (t: ProgressTarget) => api<Progress>(progressUrl(t)),
  saveProgress: (t: ProgressTarget, jalons: MilestoneInput[]) =>
    api<void>(progressUrl(t), { method: 'PUT', body: JSON.stringify({ jalons }) }),
  programmeDashboard: (id: string) => api<Dashboard>(`/api/dashboards/programme/${enc(id)}`),
  subProgrammeDashboard: (programmeId: string, id: string) =>
    api<Dashboard>(`/api/dashboards/programme/${enc(programmeId)}/subProgramme/${enc(id)}`),
  home: (programmeId?: string | null) => api<Home>(`/api/dashboards/home${programmeId ? '?programmeId=' + enc(programmeId) : ''}`),
};
