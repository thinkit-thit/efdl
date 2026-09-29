import { api } from './apiClient';

/** Facture dans le circuit de validation. */
export interface ValidationRow {
  externalId: string; creationDate: string | null; numeroFacture: string | null; intituleFacture: string | null;
  prestataire: string | null; contrat: string | null; projet: string | null; montant: number;
  prochainValidateur: string | null; status: string;
  /** Attente à l'étape en cours (jours), délai de l'étape (null = sans délai), délai dépassé. */
  attenteJours: number | null; delaiMax: number | null; enRetard: boolean;
}
export interface ValidationStep {
  validateurId: string; ordre: number; title: string | null; description: string | null;
  userExternalId: string | null; userName: string | null; questionIds: string[];
}
export interface ValidationDetail {
  facture: ValidationRow; numeroAttachement: string | null; intituleAttachement: string | null;
  montantContrat: number; montantPaye: number; etape: ValidationStep | null; questions: string[];
  historique: { userName: string | null; status: string; date: string | null }[];
  niveau: number; etapes: number; canValidate: boolean;
}

const enc = encodeURIComponent;

/** « Validation Paiement » : factures qui attendent l'utilisateur connecté (contrôlé par le serveur). */
export const validationApi = {
  list: () => api<ValidationRow[]>('/api/validations'),
  get: (id: string) => api<ValidationDetail>(`/api/validations/${enc(id)}`),
  validate: (id: string) => api<void>(`/api/validations/${enc(id)}/validate`, { method: 'POST' }),
  reject: (id: string) => api<void>(`/api/validations/${enc(id)}/reject`, { method: 'POST' }),
};
