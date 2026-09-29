package com.ffs.efdl.params;

/**
 * Action d'un autre module après une création depuis un écran générique (ex. prévenir le 1er validateur d'une
 * nouvelle facture). Appelé seulement pour les saisies à l'écran, pas pour les imports Excel.
 */
public interface CreationListener {

    /** Clé de l'écran concerné (ex. « paiements »). */
    String key();

    void created(String schoolExternalId, String externalId);
}
