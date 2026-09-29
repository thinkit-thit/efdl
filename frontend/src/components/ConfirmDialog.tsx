import { createContext, useCallback, useContext, useEffect, useRef, useState, type CSSProperties, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';

/**
 * Fenêtre de confirmation (modale) : en-tête coloré,
 * icône, question, précision, boutons « Annuler » / « Oui, … ». Remplace les window.confirm du navigateur.
 */
export interface ConfirmOptions {
  title: string;
  message: ReactNode;
  detail?: ReactNode;
  confirmLabel: string;
  cancelLabel?: string;
  tone: 'danger' | 'warning' | 'primary';
  icon: string;
}

type Confirm = (options: ConfirmOptions) => Promise<boolean>;

const ConfirmContext = createContext<Confirm | null>(null);

// ---------- messages types ----------

/** Suppression (« Êtes-vous sûr de vouloir supprimer … ? »). */
export function deleteOptions(what?: string | null): ConfirmOptions {
  return {
    title: 'Confirmation de suppression', tone: 'danger', icon: 'las la-exclamation-triangle',
    message: <>Êtes-vous sûr de vouloir supprimer {what ? <>« {what} »</> : 'cet élément'} ?</>,
    detail: 'Cette action est irréversible.', confirmLabel: 'Oui, supprimer',
  };
}

/** Abandon d'une saisie modifiée (bouton Annuler / Retour, changement d'étape). */
export function cancelOptions(): ConfirmOptions {
  return {
    title: "Confirmation d'annulation", tone: 'warning', icon: 'las la-exclamation-circle',
    message: 'Êtes-vous sûr de vouloir quitter sans enregistrer ?',
    detail: 'Les informations modifiées ne seront pas prises en compte si vous cliquez sur « Oui, annuler ».',
    confirmLabel: 'Oui, annuler', cancelLabel: 'Continuer la saisie',
  };
}

// ---------- saisie modifiée ----------

/**
 * Les formulaires de saisie portent {@code data-unsaved-guard} : toute frappe ou sélection les marque « modifiés »
 * (data-dirty) ; l'enregistrement réussi ou le départ de la page les remet à zéro.
 */
function isDirty(): boolean {
  return !!document.querySelector('form[data-unsaved-guard][data-dirty="true"]');
}

/** À appeler après un enregistrement réussi sans changement de page (ex. étape suivante d'une fiche). */
export function markSaved(): void {
  document.querySelectorAll<HTMLFormElement>('form[data-unsaved-guard]').forEach((f) => delete f.dataset.dirty);
}

// ---------- fournisseur ----------

export function ConfirmProvider({ children }: { children: ReactNode }) {
  const [pending, setPending] = useState<{ options: ConfirmOptions; resolve: (ok: boolean) => void } | null>(null);
  const cancelRef = useRef<HTMLButtonElement>(null);

  const confirm = useCallback<Confirm>((options) => new Promise<boolean>((resolve) => setPending({ options, resolve })), []);
  const close = (ok: boolean) => {
    pending?.resolve(ok);
    setPending(null);
  };

  // Suivi des modifications de tous les formulaires marqués.
  useEffect(() => {
    const onEdit = (e: Event) => {
      const form = (e.target as HTMLElement | null)?.closest?.('form[data-unsaved-guard]') as HTMLFormElement | null;
      if (form) form.dataset.dirty = 'true';
    };
    document.addEventListener('input', onEdit, true);
    document.addEventListener('change', onEdit, true);
    return () => {
      document.removeEventListener('input', onEdit, true);
      document.removeEventListener('change', onEdit, true);
    };
  }, []);

  // Tout lien interne (menu latéral, logo, menu du compte, déconnexion…) cliqué pendant une saisie modifiée :
  // confirmation, puis le clic est rejoué normalement (le formulaire n'est plus « modifié »).
  useEffect(() => {
    const onClick = async (e: MouseEvent) => {
      if (e.defaultPrevented || e.button !== 0 || e.ctrlKey || e.metaKey || e.shiftKey || e.altKey) return;
      const link = (e.target as HTMLElement | null)?.closest?.('a[href]') as HTMLAnchorElement | null;
      const href = link?.getAttribute('href');
      if (!link || !href || !href.startsWith('/') || link.target === '_blank' || !isDirty()) return;
      e.preventDefault();
      e.stopPropagation();
      if (await confirm(cancelOptions())) {
        markSaved();
        link.click();
      }
    };
    // Fermeture de l'onglet / rechargement : alerte native du navigateur (seule possible à ce moment).
    const onUnload = (e: BeforeUnloadEvent) => { if (isDirty()) e.preventDefault(); };
    document.addEventListener('click', onClick, true);
    window.addEventListener('beforeunload', onUnload);
    return () => {
      document.removeEventListener('click', onClick, true);
      window.removeEventListener('beforeunload', onUnload);
    };
  }, [confirm]);

  // Échap = Annuler ; le focus va sur « Annuler » (le choix sans risque).
  useEffect(() => {
    if (!pending) return;
    cancelRef.current?.focus();
    const onKey = (e: KeyboardEvent) => { if (e.key === 'Escape') close(false); };
    document.addEventListener('keydown', onKey);
    document.body.classList.add('modal-open');
    return () => {
      document.removeEventListener('keydown', onKey);
      document.body.classList.remove('modal-open');
    };
  });

  const o = pending?.options;
  const headerClass = o?.tone === 'danger' ? 'bg-danger' : o?.tone === 'warning' ? 'bg-warning' : 'bg-primary';
  const iconClass = o?.tone === 'danger' ? 'text-danger' : o?.tone === 'warning' ? 'text-warning' : 'text-primary';
  const buttonClass = o?.tone === 'danger' ? 'btn-danger' : o?.tone === 'warning' ? 'btn-warning' : 'btn-primary';
  return (
    <ConfirmContext.Provider value={confirm}>
      {children}
      {o && (
        <>
          <div className="modal fade show d-block" tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="confirmModalLabel"
            onMouseDown={(e) => { if (e.target === e.currentTarget) close(false); }}>
            <div className="modal-dialog modal-dialog-centered" role="document">
              <div className="modal-content">
                <div className={'modal-header ' + headerClass}>
                  <h6 className="modal-title m-0 text-white" id="confirmModalLabel">{o.title}</h6>
                  <button type="button" className="btn-close btn-close-white" aria-label="Fermer" onClick={() => close(false)}></button>
                </div>
                <div className="modal-body text-center">
                  <i className={o.icon + ' ' + iconClass} style={{ fontSize: 50 }}></i>
                  <h5 className="mt-3">{o.message}</h5>
                  {o.detail && <p className="text-muted mb-0">{o.detail}</p>}
                </div>
                <div className="modal-footer">
                  <button ref={cancelRef} type="button" className="btn btn-soft-secondary btn-sm" onClick={() => close(false)}>
                    {o.cancelLabel ?? 'Annuler'}
                  </button>
                  <button type="button" className={'btn btn-sm ' + buttonClass} onClick={() => close(true)}>{o.confirmLabel}</button>
                </div>
              </div>
            </div>
          </div>
          <div className="modal-backdrop fade show"></div>
        </>
      )}
    </ConfirmContext.Provider>
  );
}

/** Ouvre la fenêtre et renvoie le choix (true = « Oui, … »). */
export function useConfirm(): Confirm {
  const confirm = useContext(ConfirmContext);
  if (!confirm) throw new Error('useConfirm : ConfirmProvider manquant');
  return confirm;
}

/**
 * Quitter une saisie : demande confirmation seulement si un formulaire a été modifié, puis exécute {@code leave}.
 */
export function useLeaveGuard(): (leave: () => void) => Promise<void> {
  const confirm = useConfirm();
  return useCallback(async (leave: () => void) => {
    if (isDirty() && !(await confirm(cancelOptions()))) return;
    markSaved();
    leave();
  }, [confirm]);
}

/** Lien Annuler / Retour d'une fiche, avec confirmation si la saisie a été modifiée. */
export function LeaveLink({ to, className, style, children }: {
  to: string; className: string; style?: CSSProperties; children: ReactNode;
}) {
  const navigate = useNavigate();
  const guard = useLeaveGuard();
  return (
    <a href={to} className={className} style={style} onClick={(e) => {
      if (e.ctrlKey || e.metaKey || e.shiftKey || e.button !== 0) return; // nouvel onglet : comportement normal
      e.preventDefault();
      void guard(() => navigate(to));
    }}>{children}</a>
  );
}
