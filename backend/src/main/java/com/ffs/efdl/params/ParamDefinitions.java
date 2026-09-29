package com.ffs.efdl.params;

import static com.ffs.efdl.params.ParamModel.FieldDef.bool;
import static com.ffs.efdl.params.ParamModel.FieldDef.choice;
import static com.ffs.efdl.params.ParamModel.FieldDef.date;
import static com.ffs.efdl.params.ParamModel.FieldDef.decimal;
import static com.ffs.efdl.params.ParamModel.FieldDef.integer;
import static com.ffs.efdl.params.ParamModel.FieldDef.ref;
import static com.ffs.efdl.params.ParamModel.FieldDef.refSet;
import static com.ffs.efdl.params.ParamModel.FieldDef.text;
import static com.ffs.efdl.params.ParamModel.FieldDef.textarea;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.bson.Document;
import org.springframework.stereotype.Component;

import com.ffs.efdl.params.ParamModel.ChildLink;
import com.ffs.efdl.params.ParamModel.ColumnDef;
import com.ffs.efdl.params.ParamModel.Derivation;
import com.ffs.efdl.params.ParamModel.FieldDef;
import com.ffs.efdl.params.ParamModel.FormDef;
import com.ffs.efdl.params.ParamModel.LockDef;
import com.ffs.efdl.params.ParamModel.MirrorDef;
import com.ffs.efdl.params.ParamModel.SumDef;
import com.ffs.efdl.params.ParamModel.UniqueDef;
import com.ffs.efdl.params.ParamModel.Virtual;
import com.ffs.efdl.params.ParamModel.Hierarchy;
import com.ffs.efdl.params.ParamModel.Option;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamModel.RefDef;
import com.ffs.efdl.params.ParamModel.Routes;
import com.ffs.efdl.params.ParamModel.ScopeDef;

/**
 * Écrans « Paramètre » : champs enregistrés, colonnes et libellés de chaque écran.
 * Collections = nom simple de la classe du document (pas d'{@code @Entity}).
 */
@Component
public class ParamDefinitions {

    static final String PROJECT = "/admin/project/parametre";
    static final String ELECTION = "/admin/dashboard/election";
    private static final String PKG_PROJECT = "com.ffs.kiwi.model.project.";
    private static final String PKG_ELECTION = "com.ffs.kiwi.model.dashboard.election.";

    private final Map<String, ParamDef> params = new LinkedHashMap<>();
    private final Map<String, RefDef> refs = new LinkedHashMap<>();
    private final Map<String, Hierarchy> hierarchies = new LinkedHashMap<>();
    private final Map<String, List<ChildLink>> children = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> createDefaults = new LinkedHashMap<>();
    private final Map<String, String> autoNumbers = new LinkedHashMap<>();
    private final Map<String, List<Derivation>> derivations = new LinkedHashMap<>();
    private final Map<String, FormDef> forms = new LinkedHashMap<>();
    private final Map<String, List<Virtual>> virtuals = new LinkedHashMap<>();
    private final Map<String, MirrorDef> mirrors = new LinkedHashMap<>();
    private final Map<String, Document> listFilters = new LinkedHashMap<>();
    private final Map<String, LockDef> locks = new LinkedHashMap<>();
    private final Map<String, SumDef> sums = new LinkedHashMap<>();
    private final Map<String, UniqueDef> uniques = new LinkedHashMap<>();

    public ParamDefinitions() {
        // ---------- Références (listes de choix, libellés) ----------
        addRef("categories", "Categorie", "title");
        addRef("secteurs", "Secteur", "title");
        addRef("sousProjets", "SousProjet", "title");
        addRef("infrastructures", "Infrastructure", "title");
        addRef("questions", "Question", "question");
        addRef("programmes", "Programme", "title");
        // Validateurs : utilisateurs ADMINISTRATION de l'école (UserService.getByType), libellé = getDisplayName().
        refs.put("users", new RefDef("users", "user", List.of("lastName", "firstName", "username"),
                ParamDefinitions::displayName, null, new Document("type", "ADMINISTRATION")));
        addLocalisationRef("provinces", "Province", null);
        addLocalisationRef("regions", "Region", "provinceId");
        addLocalisationRef("districts", "District", "regionId");
        addLocalisationRef("communes", "Commune", "districtId");

        // ---------- Projets : libellé seul (Categorie, Secteur, Infrastructure, TypeInfrastructure, TypeTravaux, SousProjet) ----------
        titled("categories", "categorie", "Categorie", "Catégories", "catégorie", "Nom de la categorie");
        titled("secteurs", "secteur", "Secteur", "Secteurs", "secteur", "Nom du secteur");
        titled("infrastructures", "infrastructure", "Infrastructure", "Infrastructure", "infrastructure", "Nom de l'infrastructure");
        titled("typeInfrastructures", "typeInfrastructure", "TypeInfrastructure", "Type Infrastructures", "type d'infrastructure", "Nom du type Infrastructure");
        titled("typeTravaux", "typeTravaux", "TypeTravaux", "Type Travaux", "type de travaux", "Nom du type Travaux");
        titled("sousProjets", "sousProjet", "SousProjet", "Natures", "nature", "Intitulé");

        // ---------- Projets : type d'avancement (Avancement, tri typeAvancement puis rang) ----------
        add(new ParamDef("avancements", "Type avancement", "Projets", "type d'avancement", "Avancement",
                PKG_PROJECT + "Avancement", PROJECT, "avancement", true, List.of("typeAvancement", "rang"),
                List.of(col("typeAvancement", "Type"), col("rang", "Ordre"), col("title", "Intitulé"),
                        col("pourcentage", "Equivalence en %")),
                List.of(choice("typeAvancement", "Jalon", opt("PROGRAMME"), opt("PROJET"), opt("SOUS_PROJET")),
                        integer("rang", "Ordre"), text("title", "Intitulé").mandatory(),
                        decimal("pourcentage", "Equivalence en pourcentage")),
                List.of(), null, null));

        // ---------- Projets : questions de la check-list avant paiement ----------
        add(new ParamDef("questions", "Question Check Av Paiement", "Projets", "question", "Question",
                PKG_PROJECT + "Question", PROJECT, "question", true, List.of(),
                List.of(col("question", "Nom de la question"), col("typeReponse", "Type de la réponse")),
                List.of(text("question", "Nom de la question").mandatory(),
                        choice("typeReponse", "Type de la réponse", new Option("OUI_NON", "Oui / Non"), new Option("TEXTE", "Texte"))),
                List.of(), null, null));

        // ---------- Validateurs paiement ----------
        add(new ParamDef("validateurs", "Validateurs paiement", "Paramètre", "validateur", "Validateur",
                PKG_PROJECT + "Validateur", PROJECT, "validateur", true, List.of(),
                List.of(col("ordre", "Numéro"), col("title", "Libelé"), col("programmeExternalId", "Programme"),
                        col("userExternalId", "Nom du validateur"), col("maxJourTraitement", "Nb de jour de traitement")),
                List.of(integer("ordre", "Ordre"), text("title", "Libelé").mandatory(),
                        ref("programmeExternalId", "Programme", "programmes"), ref("userExternalId", "Utilisateur", "users"),
                        textarea("description", "Note"), integer("maxJourTraitement", "Nb jour de traitement max"),
                        refSet("questionIds", "CHECK-LIST", "questions")),
                List.of(), null, null));

        // ---------- Indicateurs KPI ----------
        add(new ParamDef("indicateurs", "Indicateurs KPI", "Paramètre", "indicateur", "Indicateur",
                PKG_PROJECT + "Indicateur", PROJECT, "indicateur", true, List.of(),
                List.of(col("ordre", "Numéro"), col("categorieExternalId", "Catégorie"), col("secteurExternalId", "Secteur"),
                        col("sousProjetExternalId", "Nature"), col("infrastructureExternalId", "Infrastructure"),
                        col("indicateurProduit1", "Indicateur produit 1"), col("indicateurProduit2", "Indicateur produit 2"),
                        col("indicateurEffet1", "Indicateur effet 1"), col("indicateurEffet2", "Indicateur effet 2")),
                List.of(integer("ordre", "Numero"), ref("categorieExternalId", "Categorie", "categories"),
                        ref("secteurExternalId", "Secteur", "secteurs"), ref("sousProjetExternalId", "Nature", "sousProjets"),
                        ref("infrastructureExternalId", "Infrastructure", "infrastructures"),
                        textarea("indicateurProduit1", "Indicateur Produit 1"), textarea("indicateurProduit2", "Indicateur Produit 2"),
                        textarea("indicateurEffet1", "Indicateur effet 1"), textarea("indicateurEffet2", "Indicateur effet 2")),
                List.of("categorieExternalId", "secteurExternalId"), null, null));

        // ---------- Localisation (collections Election) ----------
        // Création : l'utilisateur choisit les parents en cascade ; le code est calculé
        // (code du parent + numéro suivant) ou saisi. Parents et code ne sont plus modifiables ensuite.
        FieldDef province = ref("provinceId", "Province", "provinces").onCreateOnly();
        FieldDef region = ref("regionId", "Région", "regions").onCreateOnly();
        FieldDef district = ref("districtId", "District", "districts").onCreateOnly();
        FieldDef commune = ref("communeId", "Commune", "communes").onCreateOnly();
        FieldDef code = text("code", "Code").onCreateOnly();
        List<FieldDef> contacts = List.of(text("contactName", "Nom du contact"), text("contactNumber", "Tél du contact"),
                text("contactPoste", "Poste du contact"));
        // Population : saisie sur le fokontany ; province → commune : somme de leurs fokontany.
        List<FieldDef> populationTotals = List.of(integer("population", "Population").readOnly().hidden(),
                integer("population18", "Population +18 ans").readOnly().hidden());

        add(localisation("provinces", "province", "Province", "Province", "province",
                withPopulation(List.of(col("name", "Nom"), col("description", "Description"))),
                concat(List.of(code, text("name", "Nom de la province").mandatory(), textarea("description", "Description")), populationTotals),
                List.of(), "Region", "provinceId"));
        hierarchies.put("provinces", new Hierarchy(null, null, List.of(), 1, Map.of()));

        add(localisation("regions", "region", "Region", "Région", "région",
                withPopulation(List.of(col("provinceId", "Province"), col("name", "Région"), col("description", "Description"))),
                concat(List.of(province, code, text("name", "Nom de la region").mandatory(), textarea("description", "Description")), populationTotals),
                List.of("provinceId"), "District", "regionId"));
        hierarchies.put("regions", new Hierarchy("provinceId", "Province", List.of(), 1, Map.of()));

        add(localisation("districts", "district", "District", "District", "district",
                withPopulation(List.of(col("regionId", "Région"), col("name", "District"), col("contactName", "Nom du contact"),
                        col("contactNumber", "Tél du contact"), col("contactPoste", "Poste du contact"))),
                concat(List.of(province, region, code, text("name", "Nom du district").mandatory()), contacts,
                        List.of(textarea("description", "Description")), populationTotals),
                List.of("provinceId", "regionId"), "Commune", "districtId"));
        hierarchies.put("districts", new Hierarchy("regionId", "Region", List.of("provinceId"), 2,
                Map.of("nbrCommune", 0, "nbrCommuneTotal", 0, "nbrBv", 0, "nbrBvTotal", 0, "siege", 0, "status", "TO_CHECK")));

        add(localisation("communes", "commune", "Commune", "Commune", "commune",
                withPopulation(List.of(col("regionId", "Région"), col("districtId", "District"), col("code", "Code Com"),
                        col("name", "Commune"), col("categorie", "Catégorie"), col("contactName", "Nom du maire"),
                        col("contactNumber", "Tél du maire"))),
                concat(List.of(province, region, district, text("code", "Code Com").onCreateOnly(),
                                choice("categorie", "Catégorie", opt("CR 1"), opt("CR 2"), opt("CU")).onCreateOnly(),
                                text("name", "Nom de la commune").mandatory()),
                        contacts, List.of(textarea("description", "Description")), populationTotals),
                List.of("provinceId", "regionId", "districtId"), "Fokontany", "communeId"));
        hierarchies.put("communes", new Hierarchy("districtId", "District", List.of("provinceId", "regionId"), 2,
                Map.of("data", false)));

        add(localisation("fokontanies", "fokontany", "Fokontany", "Fokontany", "fokontany",
                withPopulation(List.of(col("regionId", "Région"), col("districtId", "District"), col("communeId", "Commune"),
                        col("name", "Fokontany"), col("contactName", "Nom du contact"), col("contactNumber", "Tél du contact"),
                        col("contactPoste", "Poste du contact"))),
                concat(List.of(province, region, district, commune, code, text("name", "Nom du fokontany").mandatory()),
                        contacts, List.of(textarea("description", "Description"),
                                integer("population", "Population"), integer("population18", "Population +18 ans"))),
                List.of("provinceId", "regionId", "districtId", "communeId"), null, null));
        populationSums("provinces", "provinceId");
        populationSums("regions", "regionId");
        populationSums("districts", "districtId");
        populationSums("communes", "communeId");
        hierarchies.put("fokontanies", new Hierarchy("communeId", "Commune", List.of("provinceId", "regionId", "districtId"), 2,
                Map.of("data", false)));

        definePta();
        defineProgrammes();
        defineProjects();
        defineContrats();
        defineValidation();
    }

    /**
     * Validateurs d'un programme (admin/project/programme/validateur/{programmeId}/**) ; l'ordre fixe le circuit
     * de validation des factures, il est donc unique dans le programme. Colonnes « Projet » et « Prochain validateur »
     * des factures : calculées par le circuit (RowEnricher).
     */
    private void defineValidation() {
        String base = "/admin/project/programme/validateur/{programmeId}";
        add(new ParamDef("programmeValidateurs", "Validateurs du programme", "FDL PROJET", "validateur", "Validateur",
                PKG_PROJECT + "Validateur", "/admin/project", "validateur", true, List.of("ordre"),
                List.of(col("ordre", "Ordre"), col("title", "Libelé"), col("userExternalId", "Validateur"),
                        col("maxJourTraitement", "Nb jour de traitement max")),
                List.of(ref("programmeExternalId", "Programme", "programmes").readOnly(), integer("ordre", "Ordre"),
                        text("title", "Libelé").mandatory(), ref("userExternalId", "Utilisateur", "users").mandatory(),
                        textarea("description", "Note"), integer("maxJourTraitement", "Nb jour de traitement max"),
                        refSet("questionIds", "CHECK-LIST", "questions")),
                List.of(), null, null,
                new Routes(base + "/validateurs", base + "/edit/{id}", base + "/delete/{id}"),
                List.of(new ScopeDef("programmeId", "programmeExternalId", "Programme", "title")), List.of()));
        UniqueDef ordreUnique = new UniqueDef(List.of("programmeExternalId", "ordre"),
                "Cet ordre est déjà utilisé par un autre validateur du programme");
        uniques.put("programmeValidateurs", ordreUnique);
        uniques.put("validateurs", ordreUnique);

        // Factures : colonnes calculées par le circuit (voir ValidationService).
        ParamDef paiements = params.get("paiements");
        List<FieldDef> fields = new ArrayList<>(paiements.fields());
        fields.add(0, text("projetNumero", "Projet").readOnly().hidden());
        fields.add(0, text("prochainValidateur", "Prochain validateur").readOnly().hidden());
        fields.add(0, integer("niveauValidation", "Niveau de validation").readOnly().hidden());
        List<ColumnDef> columns = new ArrayList<>(paiements.columns());
        columns.add(5, col("projetNumero", "Projet"));
        columns.add(7, col("prochainValidateur", "Prochain validateur"));
        params.put("paiements", new ParamDef(paiements.key(), paiements.title(), paiements.section(), paiements.entityLabel(),
                paiements.collection(), paiements.className(), paiements.basePath(), paiements.singular(), paiements.creatable(),
                paiements.sort(), columns, fields, paiements.filters(), paiements.childCollection(), paiements.childField(),
                paiements.routes(), paiements.scope(), paiements.cascade()));
    }

    /**
     * FDL PROJET : contrats (simples, avec leur GroupeContrat jumeau), contrats multiples et leurs lots,
     * paiements (factures). Montant payé = somme des paiements VALIDATE du contrat (getPaiementsDoubleByGroupeContratByStatus).
     */
    private void defineContrats() {
        refs.put("prestataires", new RefDef("prestataires", "provider", List.of("name"), d -> d.getString("name"), null, null));
        refs.put("projectsNumero", new RefDef("projectsNumero", "Project", List.of("numero", "title"),
                d -> nz(d.getString("numero")) + " / " + nz(d.getString("title")), null, null));
        // Contrats (GroupeContrat) du prestataire choisi, pour la saisie d'une facture.
        refs.put("groupeContrats", new RefDef("groupeContrats", "GroupeContrat", List.of("title", "providerExternalId"),
                d -> d.getString("title"), "providerExternalId", null));

        Option[] types = { new Option("BE", "BUREAU D ETUDE"), new Option("ENTREPRISE", "ENTREPRISE"),
                new Option("FOURNISSEUR", "FOURNISSEUR") };
        String project = "/admin/project";
        Virtual montantPaye = new Virtual("montantPayeValide", "Paiement", "groupeContratExternalId", "groupeContratExternalId",
                "montant", "sum", new Document("status", "VALIDATE"));
        Map<String, Object> contratDefaults = Map.of("tauxExecutionFinanciere", 0.0, "dernierPaiement", 0.0,
                "soldeAverser", 0.0, "livraisonMge", false);
        Map<String, Object> groupeDefaults = Map.of("tauxExecutionFinanciere", 0.0, "montantPaye", 0.0,
                "dernierPaiement", 0.0, "soldeAverser", 0.0);
        List<Derivation> fromProject = List.of(new Derivation("projectExternalId", "Project", "externalId",
                Map.of("subProgrammeExternalId", "subProgrammeExternalId", "programmeExternalId", "programmeExternalId")));

        // ---- Contrats simples (grille 2 colonnes) ----
        add(new ParamDef("contrats", "Contrats", "FDL PROJET", "contrat", "Contrat", PKG_PROJECT + "Contrat", project,
                "contrat", true, List.of(),
                List.of(new ColumnDef("title", "N° Contrat", List.of("programmeExternalId", "subProgrammeExternalId"), "join"),
                        col("projectExternalId", "N° sous projet"), col("typePrestataire", "Type"),
                        col("providerExternalId", "Prestataire"), col("montantContrat", "Montant contrat"),
                        col("montantPayeValide", "Montant payé"), col("dateContrat", "Date contrat"), col("dateDebut", "Date début")),
                List.of(ref("programmeExternalId", "Programme", "programmes").readOnly().hidden(),
                        ref("subProgrammeExternalId", "Projet", "subProgrammes").readOnly().hidden(),
                        decimal("montantPayeValide", "Montant payé (paiements validés)").readOnly().hidden(),
                        text("groupeContratExternalId", "Contrat (groupe)").readOnly().hidden(),
                        ref("projectExternalId", "N° SOUS PROJET / PROJET / PROGRAMME", "projectsNumero").full(),
                        text("title", "Contrat N°").mandatory(), text("observation", "Note"),
                        choice("typePrestataire", "Type Prestataire", types), ref("providerExternalId", "BE/Ese/Frns", "prestataires"),
                        date("dateContrat", "Date contrat"), date("dateDebut", "Date début du contrat"),
                        decimal("avancementPrevu", "Avancement prévu"), decimal("avancementActuel", "Avancement actuel"),
                        decimal("montantContrat", "Montant contrat"), decimal("montantPaye", "Montant Payé"),
                        decimal("delaiContrat", "Délai contrat (mois)"), date("dateReceptionTechnique", "Date Réception Technique"),
                        date("dateReceptionProvisoire", "Date Réception Provisoire"),
                        date("dateReceptionDenifitive", "Date Reception Denifitive")),
                List.of("providerExternalId"), "Paiement", "groupeContratExternalId"));
        forms.put("contrats", new FormDef("grid", "Gestion des contrats", "Info sur le contrat", "Info sur le contrat", false));
        derivations.put("contrats", fromProject);
        createDefaults.put("contrats", contratDefaults);
        virtuals.put("contrats", List.of(montantPaye));
        // Jumeau GroupeContrat (même externalId, multiple=false), mis à jour à chaque enregistrement.
        Map<String, Object> mirrorConstants = new LinkedHashMap<>(groupeDefaults);
        mirrorConstants.put("multiple", false);
        mirrors.put("contrats", new MirrorDef("GroupeContrat", PKG_PROJECT + "GroupeContrat", "groupeContratExternalId",
                Map.of("title", "title", "typePrestataire", "typePrestataire", "providerExternalId", "providerExternalId",
                        "montantContrat", "montantContrat", "delaiContrat", "delaiContrat", "dateContrat", "dateContrat",
                        "dateDebut", "dateDebut"), mirrorConstants));

        // ---- Contrats multiples ----
        add(new ParamDef("groupeContrats", "Contrats Multiples", "FDL PROJET", "contrat multiple", "GroupeContrat",
                PKG_PROJECT + "GroupeContrat", project, "groupeContrat", true, List.of("-creationDate"),
                List.of(col("title", "N°contrat"), col("typePrestataire", "Type"), col("providerExternalId", "Prestataires"),
                        col("montantContrat", "Montant Contrat"), col("montantPayeValide", "Montant payé")),
                List.of(ref("providerExternalId", "Prestataire(BE/Ese/Frns)", "prestataires"), text("title", "N° du contrat").mandatory(),
                        choice("typePrestataire", "Type Prestataire", types), date("dateContrat", "Date contrat"),
                        date("dateDebut", "Date début du contrat"), decimal("delaiContrat", "Délai contrat (mois)"),
                        // Somme des montants des lots, recalculée à chaque enregistrement d'un lot (non saisie).
                        decimal("montantContrat", "Montant du contrat (somme des lots)").readOnly(),
                        decimal("montantPayeValide", "Montant Payé").readOnly()),
                List.of("providerExternalId"), "Contrat", "groupeContratExternalId"));
        listFilters.put("groupeContrats", new Document("multiple", true));
        Map<String, Object> multipleDefaults = new LinkedHashMap<>(groupeDefaults);
        multipleDefaults.put("multiple", true);
        multipleDefaults.put("montantContrat", 0.0); // puis somme des lots (voir sums)
        createDefaults.put("groupeContrats", multipleDefaults);
        virtuals.put("groupeContrats", List.of(montantPaye.withLocal("externalId")));
        children.put("groupeContrats", List.of(ChildLink.list("groupeContratLots", "Lots du contrat", "groupeContratId")));

        // ---- Lots d'un contrat multiple ----
        // Titre, type, prestataire et dates hérités du contrat multiple à la création, comme saveContrat.
        String groupe = project + "/groupeContrat";
        add(new ParamDef("groupeContratLots", "Lots du contrat", "FDL PROJET", "lot", "Contrat", PKG_PROJECT + "Contrat",
                project, "contrat", true, List.of("creationDate"),
                List.of(col("lot", "Contrat / Lot"), col("providerExternalId", "Prestataire"), col("projectExternalId", "Sous-projet"),
                        col("dateContrat", "Date contrat"), col("dateDebut", "Date début"), col("montantContrat", "Montant contrat")),
                List.of(ref("programmeExternalId", "Programme", "programmes").readOnly().hidden(),
                        ref("subProgrammeExternalId", "Projet", "subProgrammes").readOnly().hidden(),
                        text("title", "N° du contrat").readOnly(), ref("providerExternalId", "Prestataire", "prestataires").readOnly(),
                        choice("typePrestataire", "Type Prestataire", types).readOnly(),
                        date("dateContrat", "Date contrat").readOnly(), date("dateDebut", "Date début du contrat").readOnly(),
                        ref("projectExternalId", "Sous-projet", "projectsNumero").full(),
                        text("lot", "Lot").mandatory(), text("observation", "Note"),
                        decimal("montantContrat", "Montant contrat"), decimal("delaiContrat", "Délai contrat (mois)"),
                        decimal("avancementPrevu", "Avancement prévu"), decimal("avancementActuel", "Avancement actuel"),
                        decimal("montantPaye", "Montant Payé"), date("dateReceptionTechnique", "Date Réception Technique"),
                        date("dateReceptionProvisoire", "Date Réception Provisoire"),
                        date("dateReceptionDenifitive", "Date Reception Denifitive")),
                List.of(), null, null,
                new Routes(groupe + "/edit/{groupeContratId}", groupe + "/{groupeContratId}/edit/{id}", groupe + "/edit/{groupeContratId}"),
                List.of(new ScopeDef("groupeContratId", "groupeContratExternalId", "GroupeContrat", "title")), List.of()));
        forms.put("groupeContratLots", new FormDef("grid", "Gestion des contrats", "Lot du contrat", "Nouveau lot", false));
        derivations.put("groupeContratLots", List.of(
                new Derivation("groupeContratExternalId", "GroupeContrat", "externalId", Map.of("title", "title",
                        "typePrestataire", "typePrestataire", "providerExternalId", "providerExternalId",
                        "dateContrat", "dateContrat", "dateDebut", "dateDebut")),
                fromProject.get(0)));
        createDefaults.put("groupeContratLots", contratDefaults);
        // Montant d'un contrat multiple = somme de ses lots, qu'un lot soit enregistré depuis ses lots ou depuis « Contrats ».
        SumDef lotsTotal = new SumDef("GroupeContrat", "montantContrat", "groupeContratExternalId", "montantContrat");
        sums.put("groupeContratLots", lotsTotal);
        sums.put("contrats", lotsTotal);

        // ---- Paiements / factures : modifiables tant qu'ils ne sont pas traités ----
        add(new ParamDef("paiements", "Paiement Facture", "FDL PROJET", "facture", "Paiement", PKG_PROJECT + "Paiement",
                project, "paiement", true, List.of("-creationDate"),
                List.of(col("creationDate", "Date"), col("numeroFacture", "N° facture"), col("intituleFacture", "Intitulé"),
                        col("providerExternalId", "Prestataire"), col("groupeContratExternalId", "Contrat"),
                        col("montant", "Montant MGA"), col("status", "Statut")),
                List.of(date("creationDate", "Date").readOnly().hidden(),
                        choice("status", "Statut", new Option("PENDING", "PENDING"), new Option("VALIDATE", "VALIDATE"),
                                new Option("REJECTED", "REJECTED")).readOnly().hidden(),
                        ref("providerExternalId", "Prestataire(BE/Ese/Frns)", "prestataires").mandatory(),
                        ref("groupeContratExternalId", "N° CONTRAT / SOUS-PROJET / PRESTATAIRE", "groupeContrats").mandatory(),
                        text("numeroFacture", "Numéro facture").mandatory(), text("intituleFacture", "Intitulé facture"),
                        text("numeroAttachement", "Numéro Attachement"), text("intituleAttachement", "Intitulé Attachement"),
                        decimal("montant", "Montant"),
                        decimal("montantContratGroupe", "Montant du contrat").readOnly(),
                        decimal("montantPayeValide", "Montant payé").readOnly()),
                List.of("providerExternalId"), null, null, null, List.of(),
                List.of("providerExternalId", "groupeContratExternalId")));
        forms.put("paiements", new FormDef(null, "Gestion des factures", "Saisie de facture", "Saisie de facture", false));
        createDefaults.put("paiements", Map.of("status", "PENDING", "niveauValidation", 0));
        derivations.put("paiements", List.of(new Derivation("groupeContratExternalId", "GroupeContrat", "externalId",
                Map.of("typePrestataire", "typePrestataire"))));
        virtuals.put("paiements", List.of(montantPaye,
                new Virtual("montantContratGroupe", "GroupeContrat", "externalId", "groupeContratExternalId", "montantContrat",
                        "value", null)));
        locks.put("paiements", new LockDef("status", List.of("PENDING"),
                "Facture déjà traitée par le circuit de validation : modification et suppression impossibles"));
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    /**
     * FDL PROJET : sous-projets (collection Project). Fiche en 7 étapes, chaque étape
     * enregistrant ses champs ; tous les champs affichés à l'écran sont enregistrés.
     * Trois listes : par sous-programme, globale, par région.
     */
    private void defineProjects() {
        addRef("typeTravaux", "TypeTravaux", "title");
        addRef("typeInfrastructures", "TypeInfrastructure", "title");
        addRef("subProgrammes", "SubProgramme", "title");
        addLocalisationRef("fokontanies", "Fokontany", "communeId");
        refs.put("indicateursKpi", new RefDef("indicateursKpi", "Indicateur", List.of("ordre", "indicateurProduit1",
                "indicateurProduit2", "indicateurEffet1", "indicateurEffet2"),
                d -> "N° " + d.get("ordre") + (d.getString("indicateurProduit1") == null ? "" : " — " + d.getString("indicateurProduit1")),
                null, null, true));

        String s1 = "ID sous-projet", s2 = "Recrut. BE", s3 = "Livrable BE", s4 = "Recrut/ Ese",
                s5 = "Livrable Prest Ese", s6 = "Recrut. Frns", s7 = "Livrable Prest Frns";
        List<FieldDef> fields = List.of(
                // Valeurs utiles aux listes et aux libellés, absentes du formulaire.
                ref("programmeExternalId", "Programme", "programmes").readOnly().hidden().in(s1),
                ref("subProgrammeExternalId", "Projet", "subProgrammes").readOnly().hidden().in(s1),
                ref("regionId", "Région", "regions").readOnly().hidden().in(s1),
                ref("districtId", "District", "districts").readOnly().hidden().in(s1),
                ref("indicateurExternalId", "Indicateur KPI", "indicateursKpi").readOnly().hidden().in(s1),
                decimal("avancementPct", "Avancement (%)").readOnly().hidden().in(s1),

                // Ordre demandé le 2026-09-26 : intitulé (pleine largeur), commune, fokontany, numéro (demi-largeur).
                text("title", "Intitulé").mandatory().full().heading("Identification du sous projet").in(s1),
                ref("communeId", "Localité (commune)", "communes").in(s1),
                ref("fokontanyId", "Localité (fokontany)", "fokontanies").in(s1),
                text("numero", "Numéro").in(s1),
                ref("infrastructureExternalId", "Infrastructure", "infrastructures").in(s1),
                ref("typeTravauxExternalId", "Type de travaux", "typeTravaux").in(s1),
                text("niveauSectoriel", "Niveau Sectoriel").in(s1),
                ref("typeInfrastructureExternalId", "Type d'infrastructure", "typeInfrastructures").in(s1),
                ref("secteurExternalId", "Secteur", "secteurs").in(s1),
                decimal("montantPrevBE", "Montant Prév BE").newRow().in(s1), decimal("montantPrevEse", "Motant Prév Ese").in(s1),
                integer("anneeFinancement", "Année de financement").in(s1),
                text("tranche", "Tranche").newRow().in(s1), text("vague", "Vague").in(s1),
                bool("filtrationEnvironnementaleSociale", "Filtration E/S").in(s1),
                text("resultatFiltration", "Résultat Filtration").in(s1),
                text("nConvention", "N° Convention").in(s1), text("montantConvention", "Montant Convention").in(s1),
                date("dateSignatureConvention", "Date Signature Conv.").in(s1), text("composante", "Composante").in(s1),
                // Libellés = textes de l'indicateur KPI de l'infrastructure.
                decimal("indicateurProduit1", "Indicateur Produit1").labelFrom("indicateurExternalId.indicateurProduit1").in(s1),
                decimal("indicateurProduit2", "Indicateur Produit2").labelFrom("indicateurExternalId.indicateurProduit2").in(s1),
                decimal("indicateurEffet1", "Indicateur Effet1").labelFrom("indicateurExternalId.indicateurEffet1").in(s1),
                decimal("indicateurEffet2", "Indicateur Effet2").labelFrom("indicateurExternalId.indicateurEffet2").in(s1),

                date("dateAmiBe", "Date AMI BE").heading("Recrutement du Bureau d'Etude").in(s2),
                date("dateLancementConsultation", "Date Lancement Consult.").in(s2),
                date("dateDepouillement", "Date Dépouillement").in(s2),
                date("dateNotificationAttribution", "Date Notification Attrib.").in(s2),
                date("dateContractualisationBe", "Date Contractualisation").in(s2),
                date("dateNotificationOsBe", "Date Notification OS BE").in(s2),
                text("denominationContratBe", "Dénomination Contrat").in(s2), decimal("montantContratBe", "Montant Contrat").in(s2),

                bool("apsValide", "APS Validé").heading("Livrable de la prestation du BE").in(s3),
                bool("apdValide", "APD Validé").in(s3),
                bool("pgesBeValide", "PGES BE Validé").in(s3), bool("daoDcTravauxValide", "DAO DC Travaux Validé").in(s3),
                text("avancementControleSurveillance", "Avancement Contrôle/Surveillance").in(s3),
                bool("daoDcfournitureValide", "DAO DC Fourniture Validé").in(s3),

                date("dateLancementAoEntreprise", "Date Lancement AO").heading("Recrutement de l'Entreprise").in(s4),
                date("dateDepouillementEntreprise", "Date Dépouillement").in(s4),
                date("dateNotificationAttributionEntreprise", "Date Notification Attrib.").in(s4),
                text("denominationContratEntreprise", "Dénomination Contrat").in(s4),
                decimal("montantContratEntreprise", "Montant Contrat").in(s4),
                date("dateContractEntreprise", "Date Contrat").in(s4),
                date("dateNotificationContratEntreprise", "Date Notification Contrat").in(s4),

                text("prealable", "Pré-alable").heading("Validation préalable").in(s5),
                bool("pgesEntrepriseValide", "PGES Entreprise Validé").in(s5),
                date("dateOsEntreprise", "Date OS Entreprise").heading("Démarrage des travaux").in(s5),
                date("dateDemarrage", "Date Démarrage").in(s5),
                integer("delaiExecution", "Délai Exécution (jours)").in(s5),
                date("dateReelleRecepTechnique", "Réception Technique (Date Réelle)").right().heading("Suivi des réceptions").in(s5),
                date("datePrevRecepProvisoire", "Réception Provisoire (Date Prév.)").in(s5),
                date("dateReelleRecepProvisoire", "Réception Provisoire (Date Réelle)").in(s5),
                date("datePrevRecepDefinitive", "Réception Définitive (Date Prév.)").in(s5),
                date("dateReelleRecepDefinitive", "Réception Définitive (Date Réelle)").in(s5),
                text("avancementTravaux", "Avancement des Travaux (Description)").in(s5),
                decimal("pourcentageAvancement", "Pourcentage Avancement").in(s5),
                text("gpsInfrastructure", "GPS Infrastructure").heading("Localisation").in(s5),

                date("dateLancementAoFournisseur", "Date Lancement AO Fournisseur").heading("Recrutement Fournisseur").in(s6),
                date("dateDepouillementFournisseur", "Date Dépouillement Fournisseur").in(s6),
                date("dateNotificationAttributionFournisseur", "Date Notification Attrib. Fournisseur").in(s6),
                text("denominationFournisseur", "Dénomination Fournisseur").in(s6),
                decimal("montantContratFournisseur", "Montant Contrat Fournisseur").in(s6),
                date("dateContractFournisseur", "Date Contrat Fournisseur").in(s6),
                date("dateOsFournisseur", "Date OS Fournisseur").in(s6),

                date("datePrevRecepFourniture", "Date Prév. Réception Fourniture")
                        .heading("Livrable de la prestation du Fournisseur").in(s7),
                date("dateReelleRecepFourniture", "Date Réelle Réception Fourniture").in(s7));

        ColumnDef localisation = new ColumnDef("regionId", "Region | District | Commune | Fokontany",
                List.of("districtId", "communeId", "fokontanyId"), "join");
        ColumnDef montant = new ColumnDef("montantPrevBE", "Montant sous-projet", List.of("montantPrevEse"), "sum");
        List<ColumnDef> columns = List.of(col("programmeExternalId", "Programme"), col("subProgrammeExternalId", "Projet"),
                col("numero", "Dossier N°"), col("title", "Sous-projet"), localisation, col("montantPrevBE", "Montant Prev BE"),
                col("montantPrevEse", "Montant Prev Ese"), montant, col("avancementPct", "Avancement (%)"),
                col("anneeFinancement", "Année fin."));
        List<String> cascade = List.of("communeId", "fokontanyId");
        List<Derivation> derived = List.of(
                new Derivation("communeId", "Commune", "externalId", Map.of("regionId", "regionId", "districtId", "districtId")),
                new Derivation("infrastructureExternalId", "Indicateur", "infrastructureExternalId",
                        Map.of("indicateurExternalId", "externalId")));
        Map<String, Object> defaults = Map.of("order", 0, "rang_avancement", 0, "pourcentage_avancement", 0.0,
                "avancementPrevu", 0.0, "avancementActuel", 0.0, "montantPaye", 0.0, "dernierPaiement", 0.0,
                "cumulPaiement", 0.0, "soldeAVerser", 0.0);
        String project = "/admin/project";
        String sub = project + "/programme/{programmeId}/subProgramme/{subProgrammeId}";

        // Sous-projets d'un sous-programme (menus « FNP 2025 », liens « Sous projet ») : création possible.
        add(new ParamDef("projects", "Sous-projets", "FDL PROJET", "sous-projet", "Project", PKG_PROJECT + "Project",
                project, "project", true, List.of("-creationDate"), columns, fields, List.of("regionId", "districtId"),
                "Contrat", "projectExternalId", new Routes(sub + "/projects", sub + "/edit/{id}", sub + "/delete/{id}"),
                List.of(new ScopeDef("programmeId", "programmeExternalId", "Programme", "title"),
                        new ScopeDef("subProgrammeId", "subProgrammeExternalId", "SubProgramme", "title")), cascade));
        children.put("projects", List.of(ChildLink.pending("Avanc.", sub + "/avancement/{id}")));

        // Liste globale (menu « Sous-projet ») : consultation et modification, création depuis un sous-programme.
        List<ColumnDef> globalColumns = List.of(col("numero", "N° Dossier"), col("programmeExternalId", "Programme"),
                col("nConvention", "N° Convention"), col("title", "Sous-projet"), localisation,
                col("montantPrevBE", "Montant Prev BE"), col("montantPrevEse", "Montant Prev Ese"), montant,
                col("anneeFinancement", "Année fin."));
        add(new ParamDef("allProjects", "Sous-projets", "FDL PROJET", "sous-projet", "Project", PKG_PROJECT + "Project",
                project, "project", false, List.of("-creationDate"), globalColumns, fields,
                List.of("programmeExternalId", "regionId", "districtId"), "Contrat", "projectExternalId",
                new Routes(project + "/projects", project + "/project/edit/{id}", project + "/project/delete/{id}"),
                List.of(), cascade));

        // Sous-projets d'une région (menus « Sous-projet BOENY / DIANA »).
        add(new ParamDef("regionProjects", "Sous-projets de la région", "FDL PROJET", "sous-projet", "Project",
                PKG_PROJECT + "Project", project, "project", false, List.of("-creationDate"), columns, fields,
                List.of("programmeExternalId", "districtId"), "Contrat", "projectExternalId",
                new Routes(project + "/{regionId}/projects", project + "/{regionId}/project/edit/{id}",
                        project + "/project/delete/{id}"),
                List.of(new ScopeDef("regionId", "regionId", "Region", "name")), cascade));

        add(new ParamDef("districtProjects", "Sous-projets du district", "FDL PROJET", "sous-projet", "Project",
                PKG_PROJECT + "Project", project, "project", false, List.of("-creationDate"), columns, fields,
                List.of("programmeExternalId"), "Contrat", "projectExternalId",
                new Routes(project + "/district/{districtCode}/projects", project + "/project/edit/{id}", project + "/project/delete/{id}"),
                List.of(new ScopeDef("districtCode", "districtId", "District", "name", "code")), cascade));
        for (String key : List.of("allProjects", "districtProjects")) {
            children.put(key, List.of(ChildLink.pending("Voir", project + "/project/view/{id}")));
        }

        for (String key : List.of("projects", "allProjects", "regionProjects", "districtProjects")) {
            derivations.put(key, derived);
            createDefaults.put(key, defaults);
            forms.put(key, new FormDef("grid", "Gestion des sous projets", "Sous Projet N°: {numero}", "NOUVEAU SOUS PROJET", true));
        }
    }

    /**
     * FDL PROJET : programmes → sous-programmes (admin/project/programme/**) et prestataires
     * (collection {@code provider}, admin/project/prestataire/**).
     */
    private void defineProgrammes() {
        String project = "/admin/project";
        String programme = project + "/programme/{programmeId}";

        add(new ParamDef("programmes", "Programme", "FDL PROJET", "programme", "Programme", PKG_PROJECT + "Programme",
                project, "programme", true, List.of("order"),
                List.of(col("title", "Nom du programme"), col("financement", "Financement"), col("montantDescription", "Montant")),
                List.of(text("title", "Nom du programme").mandatory(), text("montantDescription", "Montant"),
                        text("financement", "Financement"), textarea("zoneIntervention", "Zone d'Intervention"),
                        textarea("objectifGlobal", "Objectif Global"), textarea("critereEligibilite", "Critere d'Eligibilité"),
                        textarea("objectifSpecifique", "Objectif Specifique"), textarea("sousProjet", "Sous Projet"),
                        textarea("odd", "Odd"), textarea("resultat", "resultat"),
                        textarea("reference", "Reference"), textarea("avancement", "Avancement"),
                        textarea("approche", "Approche").newRow(), textarea("color", "Couleur Map").newRow()),
                List.of(), "SubProgramme", "programmeExternalId"));
        forms.put("programmes", new FormDef("grid", "Gestion des programmes", "Info sur le programme", "Info sur le programme", false));
        children.put("programmes", List.of(
                ChildLink.list("subProgrammes", "Projets", "programmeId"),
                ChildLink.list("programmeValidateurs", "Validations Paiement", "programmeId"),
                ChildLink.pending("Tableau de bord", project + "/programme/dashboard/{id}"),
                ChildLink.pending("Suivi Avcmnt", project + "/programme/avancement/{id}")));
        // Primitifs de Programme absents du formulaire, conservés à chaque enregistrement.
        createDefaults.put("programmes", Map.of("montant", 0.0, "nbrValidateur", 0, "order", 0));

        add(new ParamDef("subProgrammes", "Projets du programme", "FDL PROJET", "projet", "SubProgramme",
                PKG_PROJECT + "SubProgramme", project, "subProgramme", true, List.of("order"),
                List.of(col("programmeExternalId", "Nom du programme"), col("title", "Nom du projet"), col("montant", "Montant"),
                        col("avancement", "% Avancement")),
                List.of(ref("programmeExternalId", "Programme", "programmes").readOnly(),
                        text("title", "Nom du projet").mandatory(), text("avancement", "Avancement"), decimal("montant", "Montant")),
                List.of(), "Project", "subProgrammeExternalId",
                new Routes(programme + "/subProgrammes", programme + "/edit/{id}", programme + "/delete/{id}"),
                List.of(new ScopeDef("programmeId", "programmeExternalId", "Programme", "title")), List.of()));
        children.put("subProgrammes", List.of(
                ChildLink.pending("Sous projet", programme + "/subProgramme/{id}/projects"),
                ChildLink.pending("Tableau de bord", programme + "/dashboard/{id}"),
                ChildLink.pending("Suivi Avcmnt", programme + "/avancement/{id}")));
        createDefaults.put("subProgrammes", Map.of("order", 0));

        add(new ParamDef("prestataires", "Préstataires", "FDL PROJET", "prestataire", "provider",
                "com.ffs.kiwi.model.dashboard.Provider", project, "prestataire", true, List.of(),
                List.of(col("number", "N°"), col("name", "Désignation"), col("rcs", "Rcs"), col("nif", "Nif"), col("stat", "Stat"),
                        col("address", "Adresse"), col("phone", "Contact")),
                List.of(integer("number", "Numéro d'enregistrement").readOnly(), text("name", "Dénomination").mandatory(),
                        text("rcs", "Rcs"), text("nif", "Nif"), text("stat", "Stat"), text("address", "Adresse"),
                        text("phone", "Contact")),
                List.of(), null, null));
        autoNumbers.put("prestataires", "number");
    }

    /**
     * FDL PTA : Projet / Financement → Activités → Sous-activités (admin/pta/financement/**), tri par date de
     * création décroissante. Le parent vient de l'URL.
     */
    private void definePta() {
        addRef("ptaFinancements", "PtaFinancement", "title");
        addRef("ptaActivites", "PtaActivite", "title");
        Option[] statut = { new Option("EN_COURS", "EN COURS"), new Option("TERMINE", "TERMINE"),
                new Option("EN_RETARD", "EN RETARD"), new Option("TERMINE_EN_RETARD", "TERMINE EN RETARD") };
        String pta = "/admin/pta";
        String financement = pta + "/ptaFinancement/{ptaFinancementId}";
        String activite = financement + "/ptaActivite/{ptaActiviteId}";
        ScopeDef scopeFinancement = new ScopeDef("ptaFinancementId", "ptaFinancementExternalId", "PtaFinancement", "title");
        ScopeDef scopeActivite = new ScopeDef("ptaActiviteId", "ptaActiviteExternalId", "PtaActivite", "title");

        add(new ParamDef("ptaFinancements", "Projet / Financement PTA", "FDL PTA", "projet PTA", "PtaFinancement",
                PKG_PROJECT + "PtaFinancement", pta, "ptaFinancement", true, List.of("-creationDate"),
                List.of(col("numero", "N°"), col("title", "Nom du Projet"), col("dateDemarrage", "Date de démarrage"),
                        col("dateFin", "Date de fin"), col("financement", "Source de financement"),
                        col("partenaire", "Nom du partenaire"), col("montant", "Montant Budgétisé"), col("devise", "Devise"),
                        col("statutAvancement", "Statut")),
                List.of(text("numero", "N° du projet"), text("title", "Nom du projet").mandatory(),
                        date("dateDemarrage", "Date de demarrage"), date("dateFin", "Date de fin"),
                        choice("statutAvancement", "Statut", statut), textarea("description", "Description"),
                        text("financement", "Source de financement"), text("partenaire", "Nom du Partenaire"),
                        decimal("montant", "Montant Budgétisé"), text("devise", "Unité monétaire")),
                List.of(), "PtaActivite", "ptaFinancementExternalId"));
        children.put("ptaFinancements", List.of(ChildLink.list("ptaActivites", "Activités", "ptaFinancementId")));

        add(new ParamDef("ptaActivites", "Activités", "FDL PTA", "activité", "PtaActivite",
                PKG_PROJECT + "PtaActivite", pta, "ptaActivite", true, List.of("-creationDate"),
                List.of(col("numero", "N° de l'activité"), col("title", "Nom de l'activité"),
                        col("ptaFinancementExternalId", "Nom du projet"), col("dateDemarrage", "Date_début"),
                        col("dateFin", "Date_fin"), col("statutAvancement", "Statut")),
                List.of(ref("ptaFinancementExternalId", "Projet", "ptaFinancements").readOnly(),
                        text("numero", "N° de l'activité"), text("title", "Nom de l'activité").mandatory(),
                        textarea("description", "Description"), date("dateDemarrage", "Date de début"),
                        date("dateFin", "Date de fin"), choice("statutAvancement", "Statut", statut)),
                List.of(), "PtaSousActivite", "ptaActiviteExternalId",
                new Routes(financement + "/ptaActivites", financement + "/edit/{id}", financement + "/delete/{id}"),
                List.of(scopeFinancement), List.of()));
        children.put("ptaActivites", List.of(ChildLink.list("ptaSousActivites", "Sous-activités", "ptaActiviteId")));

        add(new ParamDef("ptaSousActivites", "Sous-activités", "FDL PTA", "sous-activité", "PtaSousActivite",
                PKG_PROJECT + "PtaSousActivite", pta, "ptaSousActivite", true, List.of("-creationDate"),
                List.of(col("numero", "N° Sous-activité"), col("title", "Nom sous-activité"), col("dateDebut", "Date début"),
                        col("dateFin", "Date fin"), col("statutAvancement", "Statut"), col("regionId", "Région"),
                        col("districtId", "District"), col("communeIds", "Communes")),
                List.of(text("numero", "N° sous activité"), text("title", "Nom sous activite").mandatory(),
                        textarea("description", "Description"), ref("regionId", "Région", "regions"),
                        ref("districtId", "District", "districts"), refSet("communeIds", "Commune", "communes"),
                        date("dateDebut", "Date de début"), date("dateFin", "Date de fin"),
                        choice("statutAvancement", "Statut", statut)),
                List.of(), null, null,
                new Routes(activite + "/ptaSousActivites", activite + "/edit/{id}", activite + "/delete/{id}"),
                List.of(scopeFinancement, scopeActivite), List.of("regionId", "districtId", "communeIds")));
    }

    /** Valeurs écrites à la création en plus du formulaire (champs numériques absents de l'écran). */
    public Map<String, Object> createDefaults(String key) {
        return createDefaults.getOrDefault(key, Map.of());
    }

    public List<Virtual> virtuals(String key) {
        return virtuals.getOrDefault(key, List.of());
    }

    public MirrorDef mirror(String key) {
        return mirrors.get(key);
    }

    public SumDef sum(String key) {
        return sums.get(key);
    }

    /** Critère fixe de la liste et de la fiche (ex. contrats multiples : multiple=true). */
    public Document listFilter(String key) {
        return listFilters.get(key);
    }

    public UniqueDef unique(String key) {
        return uniques.get(key);
    }

    public LockDef lock(String key) {
        return locks.get(key);
    }

    /** Présentation de la fiche (null = présentation par défaut, une colonne). */
    public FormDef form(String key) {
        return forms.get(key);
    }

    /** Valeurs recopiées depuis des documents liés à l'enregistrement (vide si aucune). */
    public List<Derivation> derivations(String key) {
        return derivations.getOrDefault(key, List.of());
    }

    /** Champ numéroté automatiquement à la création (dernier numéro + 1), jamais modifié ensuite. */
    public String autoNumber(String key) {
        return autoNumbers.get(key);
    }

    /** Liens des lignes vers les listes enfants (vide si aucune). */
    public List<ChildLink> children(String key) {
        return children.getOrDefault(key, List.of());
    }

    /** Hiérarchie de création d'un écran de localisation (null pour les autres écrans). */
    public Hierarchy hierarchy(String key) {
        return hierarchies.get(key);
    }

    public Optional<ParamDef> param(String key) {
        return Optional.ofNullable(params.get(key));
    }

    public RefDef refDef(String key) {
        return refs.get(key);
    }

    public List<ParamDef> all() {
        return List.copyOf(params.values());
    }

    // ---------- construction ----------

    private void add(ParamDef def) {
        params.put(def.key(), def);
    }

    private void titled(String key, String singular, String className, String title, String entityLabel, String label) {
        add(new ParamDef(key, title, "Projets", entityLabel, className, PKG_PROJECT + className, PROJECT, singular, true,
                List.of(), List.of(col("title", label)), List.of(text("title", label).mandatory()), List.of(), null, null));
    }

    private static ParamDef localisation(String key, String singular, String className, String title, String entityLabel,
                                         List<ColumnDef> columns, List<FieldDef> fields, List<String> filters,
                                         String childCollection, String childField) {
        return new ParamDef(key, title, "Localisation", entityLabel, className, PKG_ELECTION + className, ELECTION,
                singular, true, List.of("name"), columns, fields, filters, childCollection, childField, null, List.of(), filters);
    }

    private void addRef(String key, String collection, String labelField) {
        refs.put(key, new RefDef(key, collection, List.of(labelField), d -> d.getString(labelField), null, null));
    }

    /** Colonnes « Population » et « Population +18 ans » ajoutées à la fin d'une liste de localisation. */
    private static List<ColumnDef> withPopulation(List<ColumnDef> columns) {
        List<ColumnDef> all = new ArrayList<>(columns);
        all.add(col("population", "Population"));
        all.add(col("population18", "Population +18 ans"));
        return all;
    }

    /** Population d'un niveau (province → commune) = somme de ses fokontany actifs ({@code field} = leur rattachement). */
    private void populationSums(String key, String field) {
        virtuals.put(key, List.of(
                new Virtual("population", "Fokontany", field, "externalId", "population", "sum", null),
                new Virtual("population18", "Fokontany", field, "externalId", "population18", "sum", null)));
    }

    private void addLocalisationRef(String key, String collection, String parentField) {
        List<String> fields = parentField == null ? List.of("name") : List.of("name", parentField);
        refs.put(key, new RefDef(key, collection, fields, d -> d.getString("name"), parentField, null));
    }

    private static ColumnDef col(String field, String header) {
        return new ColumnDef(field, header);
    }

    private static Option opt(String value) {
        return new Option(value, value);
    }

    @SafeVarargs
    private static List<FieldDef> concat(List<FieldDef>... parts) {
        return Stream.of(parts).flatMap(List::stream).collect(Collectors.toCollection(ArrayList::new));
    }

    /** Nom affiché d'un utilisateur (nom puis prénom, apostrophes remplacées). */
    static String displayName(Document d) {
        String last = d.getString("lastName");
        String first = d.getString("firstName");
        String name = (last == null ? "" : last) + (first == null ? "" : " " + first);
        name = name.replace("'", " ").trim();
        return name.isEmpty() ? d.getString("username") : name;
    }
}
