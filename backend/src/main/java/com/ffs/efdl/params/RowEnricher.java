package com.ffs.efdl.params;

import java.util.List;
import java.util.Map;

/**
 * Ajoute aux lignes d'un écran générique des valeurs calculées par un autre module (ex. « Prochain validateur »
 * d'une facture, calculé par le circuit de validation). Les valeurs ne sont jamais écrites.
 */
public interface RowEnricher {

    /** Clé de l'écran concerné (ex. « paiements »). */
    String key();

    /** Écrans servis (par défaut : celui de {@link #key()}). */
    default boolean supports(String screenKey) {
        return key().equals(screenKey);
    }

    void enrich(String schoolExternalId, List<Map<String, Object>> rows);
}
