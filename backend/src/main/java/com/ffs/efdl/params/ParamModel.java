package com.ffs.efdl.params;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bson.Document;

/**
 * Description déclarative d'un écran de paramétrage (liste + fiche + suppression).
 * Les champs listés sont une <b>liste blanche</b> : aucun autre champ du document n'est lu, exposé ni écrit
 * (ex. {@code contactPassword}, résultats électoraux {@code bv}, {@code avancementMap}).
 */
public final class ParamModel {

    private ParamModel() {
    }

    public enum FieldType { TEXT, TEXTAREA, INT, DOUBLE, DATE, BOOLEAN, ENUM, REF, REF_SET }

    public record Option(String value, String label) {
    }

    /**
     * @param ref        clé de la référence ({@link RefDef}) pour REF / REF_SET
     * @param editable   faux = affiché seulement
     * @param createOnly saisi à la création seulement, ensuite affiché (parents et code d'une localisation)
     * @param section    étape / onglet de la fiche (null = fiche d'un seul bloc) ; chaque étape s'enregistre seule
     * @param ui         mise en page de la fiche (largeur, saut de ligne, titre de bloc, libellé dynamique)
     */
    public record FieldDef(String name, String label, FieldType type, boolean required, boolean editable,
                           String ref, List<Option> options, int maxLength, boolean createOnly, String section, Ui ui) {

        public static FieldDef text(String name, String label) {
            return new FieldDef(name, label, FieldType.TEXT, false, true, null, List.of(), 500, false, null, Ui.NONE);
        }

        public static FieldDef textarea(String name, String label) {
            return new FieldDef(name, label, FieldType.TEXTAREA, false, true, null, List.of(), 4000, false, null, Ui.NONE);
        }

        public static FieldDef integer(String name, String label) {
            return new FieldDef(name, label, FieldType.INT, false, true, null, List.of(), 0, false, null, Ui.NONE);
        }

        public static FieldDef decimal(String name, String label) {
            return new FieldDef(name, label, FieldType.DOUBLE, false, true, null, List.of(), 0, false, null, Ui.NONE);
        }

        /** Date saisie jour/mois/année (input type="date", stockée à minuit, fuseau du serveur). */
        public static FieldDef date(String name, String label) {
            return new FieldDef(name, label, FieldType.DATE, false, true, null, List.of(), 0, false, null, Ui.NONE);
        }

        public static FieldDef choice(String name, String label, Option... options) {
            return new FieldDef(name, label, FieldType.ENUM, false, true, null, Arrays.asList(options), 0, false, null, Ui.NONE);
        }

        public static FieldDef ref(String name, String label, String ref) {
            return new FieldDef(name, label, FieldType.REF, false, true, ref, List.of(), 0, false, null, Ui.NONE);
        }

        public static FieldDef refSet(String name, String label, String ref) {
            return new FieldDef(name, label, FieldType.REF_SET, false, true, ref, List.of(), 0, false, null, Ui.NONE);
        }

        public FieldDef mandatory() {
            return new FieldDef(name, label, type, true, editable, ref, options, maxLength, createOnly, section, ui);
        }

        public FieldDef readOnly() {
            return new FieldDef(name, label, type, false, false, ref, options, maxLength, false, section, ui);
        }

        public FieldDef onCreateOnly() {
            return new FieldDef(name, label, type, required, false, ref, options, maxLength, true, section, ui);
        }

        /** Case à cocher (booléen primitif : faux par défaut, toujours écrit). */
        public static FieldDef bool(String name, String label) {
            return new FieldDef(name, label, FieldType.BOOLEAN, false, true, null, List.of(), 0, false, null, Ui.NONE);
        }

        public FieldDef in(String section) {
            return new FieldDef(name, label, type, required, editable, ref, options, maxLength, createOnly, section, ui);
        }

        private FieldDef with(Ui newUi) {
            return new FieldDef(name, label, type, required, editable, ref, options, maxLength, createOnly, section, newUi);
        }

        /** Toute la largeur de la fiche (ex. commune, fokontany). */
        public FieldDef full() {
            return with(new Ui("full", ui.heading(), ui.labelFrom()));
        }

        /** Commence une nouvelle ligne (la case à droite du champ précédent reste vide). */
        public FieldDef newRow() {
            return with(new Ui("break", ui.heading(), ui.labelFrom()));
        }

        /** Nouvelle ligne, dans la colonne de droite (case de gauche vide). */
        public FieldDef right() {
            return with(new Ui("right", ui.heading(), ui.labelFrom()));
        }

        /** Non affiché dans la fiche (valeur utile aux listes ou aux libellés). */
        public FieldDef hidden() {
            return with(new Ui("hidden", ui.heading(), ui.labelFrom()));
        }

        /** Titre de bloc affiché avant le champ (h4). */
        public FieldDef heading(String heading) {
            return with(new Ui(ui.layout(), heading, ui.labelFrom()));
        }

        /**
         * Libellé lu dans la référence choisie par un autre champ, format {@code champ.attribut}
         * (ex. {@code indicateurExternalId.indicateurProduit1}) ; libellé fixe si absent.
         */
        public FieldDef labelFrom(String source) {
            return with(new Ui(ui.layout(), ui.heading(), source));
        }
    }

    /**
     * Mise en page d'un champ dans une fiche en grille (2 champs par ligne).
     * @param layout null (demi-largeur), « full », « break », « right » ou « hidden »
     */
    public record Ui(String layout, String heading, String labelFrom) {
        public static final Ui NONE = new Ui(null, null, null);
    }

    /**
     * Niveau d'une hiérarchie (localisation) pour la création.
     * @param parentField      parent direct choisi (ex. districtId pour une commune) ; null pour le premier niveau
     * @param inheritedFields  ancêtres recopiés depuis le document parent (ex. provinceId, regionId)
     * @param codeWidth        nombre de chiffres ajoutés au code du parent (province « 1 », région « 11 », district « 1101 »…)
     * @param defaults         valeurs écrites à la création (numériques, énumérations par défaut)
     */
    public record Hierarchy(String parentField, String parentCollection, List<String> inheritedFields, int codeWidth,
                            Map<String, Object> defaults) {
    }

    /**
     * Colonne de liste.
     * @param with champs combinés à {@code field} (null = colonne simple)
     * @param op   « join » (valeurs affichées séparées par « | ») ou « sum » (somme numérique)
     */
    public record ColumnDef(String field, String header, List<String> with, String op) {

        public ColumnDef(String field, String header) {
            this(field, header, null, null);
        }
    }

    /**
     * Valeurs recopiées depuis un document lié quand un champ est enregistré
     * (ex. commune → regionId/districtId ; infrastructure → indicateur KPI). Rien n'est modifié si le document
     * lié est introuvable.
     * @param copies champ cible → champ du document lié
     */
    public record Derivation(String source, String collection, String matchField, Map<String, String> copies) {
    }

    /**
     * Source des listes de choix et des libellés affichés (ex. titre de la catégorie d'un indicateur).
     * @param parentField champ du parent, pour les choix en cascade (région → district → communes)
     * @param extraFilter critère supplémentaire (ex. type d'utilisateur)
     */
    public record RefDef(String key, String collection, List<String> fields, Function<Document, String> label,
                         String parentField, Document extraFilter, boolean attributes) {

        public RefDef(String key, String collection, List<String> fields, Function<Document, String> label,
                      String parentField, Document extraFilter) {
            this(key, collection, fields, label, parentField, extraFilter, false);
        }
    }

    /**
     * Parent porté par l'URL de l'écran (ex. {@code /admin/pta/ptaFinancement/{ptaFinancementId}/ptaActivites}).
     * La liste est filtrée sur {@code field}, la création l'enregistre, et le parent doit exister dans l'école
     * (et appartenir aux parents précédents).
     * @param var        nom de la variable d'URL (et du paramètre de l'API)
     * @param field      champ du document qui porte le parent
     * @param collection collection du parent
     * @param labelField champ affiché pour le parent (fil d'Ariane)
     * @param matchField champ du parent comparé à la valeur de l'URL (externalId par défaut ; « code » pour
     *                   /admin/project/district/{districtCode}/projects) ; {@code field} reçoit toujours son externalId
     */
    public record ScopeDef(String var, String field, String collection, String labelField, String matchField) {

        public ScopeDef(String var, String field, String collection, String labelField) {
            this(var, field, collection, labelField, "externalId");
        }
    }

    /**
     * Lien d'une ligne vers un autre écran (colonnes « Activité. », « Projets », « Tableau de bord »…).
     * @param key  écran générique enfant (sa liste, avec {@code var} = identifiant de la ligne) ; null pour un écran dédié
     * @param path sinon, route de l'écran dédié ({var} du contexte, {id} = identifiant de la ligne)
     */
    public record ChildLink(String key, String label, String var, String path) {

        public static ChildLink list(String key, String label, String var) {
            return new ChildLink(key, label, var, null);
        }

        public static ChildLink pending(String label, String path) {
            return new ChildLink(null, label, null, path);
        }
    }

    /**
     * Présentation de la fiche.
     * @param layout    « grid » = 2 champs par ligne (col-md-6), sinon une colonne
     * @param pageTitle titre de page (h4.page-title)
     * @param editTitle titre de la carte en modification ; {champ} remplacé par sa valeur (ex. « Sous Projet N°: {numero} »)
     * @param newTitle  titre de la carte en création
     * @param steps     boutons de fiche en étapes : « Suivant » / « Terminer » et « Enregistrer et quitter » à l'étape 1
     */
    public record FormDef(String layout, String pageTitle, String editTitle, String newTitle, boolean steps) {
    }

    /**
     * Valeur calculée à la lecture (jamais écrite).
     * @param op « sum » : somme de {@code valueField} des documents de {@code collection} dont {@code foreignField}
     *           = la valeur {@code localField} de la ligne (ex. montant payé = somme des paiements VALIDATE) ;
     *           « value » : valeur {@code valueField} du document lié (ex. montant du contrat choisi)
     * @param filter critère supplémentaire (ex. status=VALIDATE) ; sinon les éléments supprimés sont exclus
     */
    public record Virtual(String name, String collection, String foreignField, String localField, String valueField,
                          String op, Document filter) {

        /** Même calcul, rattaché à un autre champ de la ligne (ex. le contrat multiple lui-même). */
        public Virtual withLocal(String field) {
            return new Virtual(name, collection, foreignField, field, valueField, op, filter);
        }
    }

    /**
     * Document jumeau tenu à jour, comme la sauvegarde d'un contrat simple qui crée / met à jour
     * le GroupeContrat de même externalId (multiple=false) et le supprime avec lui.
     * @param selfField champ de l'élément qui pointe vers le jumeau (même identifiant)
     * @param copies    champ du jumeau → champ de l'élément
     * @param constants valeurs écrites à la création du jumeau (numériques, multiple=false)
     */
    public record MirrorDef(String collection, String className, String selfField, Map<String, String> copies,
                            Map<String, Object> constants) {
    }

    /** Combinaison de champs unique parmi les éléments actifs de l'école (ex. ordre d'un validateur dans son programme). */
    public record UniqueDef(List<String> fields, String message) {
    }

    /**
     * Total recalculé sur le parent après chaque création, modification ou suppression d'un élément
     * (ex. montant d'un contrat multiple = somme des montants de ses lots actifs). Le parent est celui que désigne
     * l'élément enregistré ; un élément qui se désigne lui-même (contrat simple et son jumeau) est ignoré.
     * @param parentCollection collection du parent
     * @param parentField      champ du parent qui reçoit la somme
     * @param linkField        champ de l'élément qui désigne le parent
     * @param valueField       champ de l'élément à additionner
     */
    public record SumDef(String parentCollection, String parentField, String linkField, String valueField) {
    }

    /** Modification / suppression permises seulement si {@code field} vaut l'une de {@code allowed} (ex. paiement PENDING). */
    public record LockDef(String field, List<String> allowed, String message) {
    }

    /** Routes historiques ({var} et {id} remplacés) : liste, fiche, suppression. */
    public record Routes(String list, String edit, String delete) {
    }

    /**
     * Écran de paramétrage.
     * @param basePath    préfixe des routes des écrans ({@code /admin/project/parametre})
     * @param singular    segment des routes edit/delete ({@code categorie})
     * @param creatable   création possible
     * @param sort        tri (préfixe « - » = décroissant) ; vide = ordre naturel
     * @param childCollection / childField : suppression refusée tant qu'un enfant actif existe
     * @param filters     champs REF proposés comme filtres de liste
     * @param routes      routes particulières (null = basePath/key, basePath/singular/edit|delete/{id})
     * @param scope       parents portés par l'URL
     * @param cascade     champs dont les choix dépendent du précédent (région → district → communes)
     */
    public record ParamDef(String key, String title, String section, String entityLabel, String collection,
                           String className, String basePath, String singular, boolean creatable,
                           List<String> sort, List<ColumnDef> columns, List<FieldDef> fields, List<String> filters,
                           String childCollection, String childField, Routes routes, List<ScopeDef> scope,
                           List<String> cascade) {

        /** Écran simple, sans parent dans l'URL. */
        public ParamDef(String key, String title, String section, String entityLabel, String collection,
                        String className, String basePath, String singular, boolean creatable,
                        List<String> sort, List<ColumnDef> columns, List<FieldDef> fields, List<String> filters,
                        String childCollection, String childField) {
            this(key, title, section, entityLabel, collection, className, basePath, singular, creatable, sort, columns,
                    fields, filters, childCollection, childField, null, List.of(), List.of());
        }

        private Routes effectiveRoutes() {
            return routes != null ? routes : new Routes(basePath + "/" + key, basePath + "/" + singular + "/edit/{id}",
                    basePath + "/" + singular + "/delete/{id}");
        }

        public String listRoute(Map<String, String> vars) {
            return expand(effectiveRoutes().list(), vars, null);
        }

        public String editRoute(Map<String, String> vars, String id) {
            return expand(effectiveRoutes().edit(), vars, id);
        }

        public String deleteRoute(Map<String, String> vars, String id) {
            return expand(effectiveRoutes().delete(), vars, id);
        }

        public String listRoute() {
            return listRoute(Map.of());
        }

        public String editRoute(String id) {
            return editRoute(Map.of(), id);
        }

        public String deleteRoute(String id) {
            return deleteRoute(Map.of(), id);
        }

        private static String expand(String template, Map<String, String> vars, String id) {
            String path = template;
            for (Map.Entry<String, String> e : vars.entrySet()) {
                path = path.replace("{" + e.getKey() + "}", e.getValue());
            }
            return id == null ? path : path.replace("{id}", id);
        }

        public FieldDef field(String name) {
            return fields.stream().filter(f -> f.name().equals(name)).findFirst().orElse(null);
        }
    }
}
