/**
 * Onglet « Avancement » de la fiche sous-projet (après « Livrable Prest Frns ») : ?step=avancement affiche le suivi
 * d'avancement dans la fiche elle-même (même titre, fil d'Ariane et onglets).
 */
export const PROGRESS_TAB = 'Avancement';
export const PROGRESS_STEP = 'avancement';

/** Paramètre d'URL portant la fiche d'origine (liste globale, par région… ont chacune leur adresse de fiche). */
export const FROM_PARAM = 'from';

/** Adresse de fiche acceptée depuis l'URL (chemin interne uniquement). */
export function safeFrom(value: string | null): string | null {
  return value && value.startsWith('/admin/') && !value.startsWith('//') ? value : null;
}
