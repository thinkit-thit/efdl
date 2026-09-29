/** Types des réponses de l'API e-FDL (miroir des DTO du backend). */

export type UserType =
  | 'PARENT' | 'STUDENT' | 'PROFESSOR' | 'ADMINISTRATION' | 'TEMPORARY' | 'ADMIN'
  | 'JOINT' | 'CHILD' | 'JOINT_TEMPORARY' | 'CHILD_TEMPORARY' | 'CANDIDAT';

export interface School {
  externalId: string;
  name: string;
  domainName: string | null;
}

export interface CurrentUser {
  externalId: string;
  username: string;
  displayName: string;
  type: UserType;
  language: 'fr' | 'en' | 'mg';
  school: School | null;
  /** Mot de passe provisoire (réinitialisé par un administrateur) : changement obligatoire. */
  mustChangePassword: boolean;
  /** Imports Excel : administrateur ou « Validateur info » de l'application (contrôlé aussi par le serveur). */
  canImport: boolean;
  /** Historique des modifications : administrateur ou « Validateur info » (contrôlé aussi par le serveur). */
  canViewAudit: boolean;
}

export interface MenuItem {
  externalId: string;
  title: string;
  icon: string | null;
  url: string | null;
  urlExterne: string | null;
  path: string | null;
  children: MenuItem[];
}

export interface ApiErrorBody {
  status: number;
  error: string;
  message: string;
}
