package com.ffs.efdl.imports;

import java.io.IOException;
import java.io.InputStream;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.params.ParamModel.ParamDef;
import com.ffs.efdl.params.ParamService;
import com.ffs.efdl.service.DocumentWriter;

/**
 * Imports Excel (prestataires, factures, sous-projets), en deux temps : analyse sans écriture
 * ({@code apply=false}) puis import. Les écritures passent par {@link ParamService} (mêmes règles que la saisie :
 * format des documents, dérivations, contrats jumeaux, numérotation, contrôles) ; une ligne en erreur n'empêche pas les autres.
 */
@Service
public class ImportService {

    private static final Logger log = LoggerFactory.getLogger(ImportService.class);

    public enum Action { CREATION, MISE_A_JOUR, IGNOREE, ERREUR }

    public record Line(int line, Action action, String label, List<String> messages) {
    }

    public record Report(boolean applied, int lignes, int creations, int misesAJour, int ignorees, int erreurs,
                         List<String> colonnes, List<Line> details) {
    }

    private final ParamService params;
    private final MongoTemplate mongo;

    private final DocumentWriter writer;

    public ImportService(ParamService params, MongoTemplate mongo, DocumentWriter writer) {
        this.writer = writer;
        this.params = params;
        this.mongo = mongo;
    }

    // ============================ Prestataires ============================

    /** Colonnes : N°, Dénomination, NIF, STAT, Adresse, RCS, Contact ; mise à jour par N°. */
    public Report prestataires(InputStream in, String school, boolean apply, String by) throws IOException {
        ExcelReader x = new ExcelReader(in);
        ParamDef def = params.definition("prestataires");
        List<Line> lines = new ArrayList<>();
        for (Row row : x.rows) {
            int line = row.getRowNum() + 1;
            String numero = ExcelReader.text(row, 0);
            if (numero == null) continue;
            List<String> messages = new ArrayList<>();
            try {
                int number;
                try {
                    number = (int) Double.parseDouble(numero.replace(',', '.'));
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("N° « " + numero + " » : un nombre est attendu");
                }
                String name = ExcelReader.text(row, 1);
                if (name == null) throw new IllegalArgumentException("Dénomination vide");
                Map<String, Object> body = fields("name", name, "nif", ExcelReader.text(row, 2), "stat", ExcelReader.text(row, 3),
                        "address", ExcelReader.text(row, 4), "rcs", ExcelReader.text(row, 5), "phone", ExcelReader.text(row, 6));
                Document existing = find(school, "provider", Criteria.where("number").is(number));
                if (existing != null) {
                    if (apply) params.update(def, school, Map.of(), existing.getString("externalId"), body, by);
                    lines.add(new Line(line, Action.MISE_A_JOUR, number + " — " + name, messages));
                } else {
                    if (apply) {
                        String id = params.create(def, school, Map.of(), body, by);
                        // Numéro du fichier conservé.
                        writer.update("provider", id, school, DocumentWriter.fields("number", number), by);
                    }
                    lines.add(new Line(line, Action.CREATION, number + " — " + name, messages));
                }
            } catch (RuntimeException e) {
                lines.add(error(line, numero, e));
            }
        }
        return report(apply, x, lines);
    }

    // ============================ Factures ============================

    /**
     * Colonnes : N° du contrat, N° facture, Intitulé facture, N° attachement,
     * Intitulé attachement, Montant. {@code historique} : factures déjà payées, enregistrées VALIDATE à l'
     * import ; sinon PENDING (circuit de validation). Une facture déjà présente (même contrat, même numéro) est ignorée.
     */
    public Report paiements(InputStream in, String school, boolean historique, boolean apply, String by) throws IOException {
        ExcelReader x = new ExcelReader(in);
        ParamDef def = params.definition("paiements");
        List<Line> lines = new ArrayList<>();
        for (Row row : x.rows) {
            int line = row.getRowNum() + 1;
            String contrat = ExcelReader.text(row, 0);
            if (contrat == null) continue;
            String numero = ExcelReader.text(row, 1);
            String label = contrat + " / " + (numero == null ? "?" : numero);
            List<String> messages = new ArrayList<>();
            try {
                if (numero == null) throw new IllegalArgumentException("N° facture vide");
                List<Document> groupes = mongo.find(Query.query(active(school).and("title").is(contrat)), Document.class, "GroupeContrat");
                if (groupes.isEmpty()) throw new IllegalArgumentException("contrat « " + contrat + " » introuvable");
                if (groupes.size() > 1) throw new IllegalArgumentException("plusieurs contrats portent le numéro « " + contrat + " »");
                Document groupe = groupes.get(0);
                if (find(school, "Paiement", Criteria.where("groupeContratExternalId").is(groupe.getString("externalId"))
                        .and("numeroFacture").is(numero)) != null) {
                    messages.add("facture déjà enregistrée pour ce contrat");
                    lines.add(new Line(line, Action.IGNOREE, label, messages));
                    continue;
                }
                Double montant = ExcelReader.number(row, 5);
                Map<String, Object> body = fields("providerExternalId", groupe.getString("providerExternalId"),
                        "groupeContratExternalId", groupe.getString("externalId"), "numeroFacture", numero,
                        "intituleFacture", ExcelReader.text(row, 2), "numeroAttachement", ExcelReader.text(row, 3),
                        "intituleAttachement", ExcelReader.text(row, 4), "montant", montant == null ? 0 : montant);
                if (apply) {
                    String id = params.create(def, school, Map.of(), body, by);
                    if (historique) {
                        writer.update("Paiement", id, school, DocumentWriter.fields("status", "VALIDATE", "datePaiement", new Date()), by);
                    }
                }
                messages.add(historique ? "enregistrée VALIDATE (historique)" : "enregistrée PENDING (circuit de validation)");
                lines.add(new Line(line, Action.CREATION, label, messages));
            } catch (RuntimeException e) {
                lines.add(error(line, label, e));
            }
        }
        return report(apply, x, lines);
    }

    // ============================ Sous-projets ============================

    private static final String[] TYPES = { "BE", "ENTREPRISE", "FOURNISSEUR" };

    /**
     * Fiche de suivi des sous-projets, colonnes repérées par leur en-tête pour accepter
     * les deux modèles connus (fiche complète de 111 colonnes et fiche courte de 47 colonnes). Sous-projet créé ou mis
     * à jour par N° dossier dans le sous-programme ; référentiels (infrastructure, types, secteur) et prestataires
     * créés s'ils manquent ; contrats BE / entreprise / fournisseur créés ou mis à jour (jamais dupliqués).
     */
    public Report projects(InputStream in, String school, String programmeId, String subProgrammeId, boolean apply, String by)
            throws IOException {
        ExcelReader x = new ExcelReader(in);
        ParamDef def = params.definition("projects");
        ParamDef contrats = params.definition("contrats");
        Map<String, String> scope = Map.of("programmeId", programmeId, "subProgrammeId", subProgrammeId);
        params.checkScope(def, school, scope);
        Columns c = new Columns(x);
        if (c.numero < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Colonne « N°Dossier » introuvable dans la première ligne");
        Map<String, String> created = new HashMap<>(); // référentiels créés pendant l'analyse
        List<Line> lines = new ArrayList<>();
        for (Row row : x.rows) {
            int line = row.getRowNum() + 1;
            String numero = ExcelReader.text(row, c.numero);
            if (numero == null) continue;
            List<String> messages = new ArrayList<>();
            try {
                Document existing = find(school, "Project", Criteria.where("numero").is(numero));
                if (existing != null && !subProgrammeId.equals(existing.getString("subProgrammeExternalId"))) {
                    throw new IllegalArgumentException("le dossier existe déjà dans un autre sous-programme (non déplacé)");
                }
                Map<String, Object> body = new LinkedHashMap<>();
                put(body, "numero", numero);
                put(body, "title", ExcelReader.text(row, c.title));
                put(body, "infrastructureExternalId", reference(school, "Infrastructure", "infrastructures", ExcelReader.text(row, c.infrastructure), apply, created, messages, by));
                put(body, "typeTravauxExternalId", reference(school, "TypeTravaux", "typeTravaux", ExcelReader.text(row, c.typeTravaux), apply, created, messages, by));
                put(body, "typeInfrastructureExternalId", reference(school, "TypeInfrastructure", "typeInfrastructures", ExcelReader.text(row, c.typeInfrastructure), apply, created, messages, by));
                put(body, "secteurExternalId", reference(school, "Secteur", "secteurs", ExcelReader.text(row, c.secteur), apply, created, messages, by));
                put(body, "tranche", ExcelReader.text(row, c.tranche));
                put(body, "vague", ExcelReader.text(row, c.vague));
                put(body, "indicateurProduit1", ExcelReader.number(row, c.indicateur1));
                put(body, "indicateurProduit2", ExcelReader.number(row, c.indicateur2));
                put(body, "montantPrevBE", ExcelReader.number(row, c.montantBE));
                put(body, "montantPrevEse", ExcelReader.number(row, c.montantEse));
                Double annee = ExcelReader.number(row, c.annee);
                put(body, "anneeFinancement", annee == null ? null : annee.intValue());
                put(body, "gpsInfrastructure", ExcelReader.text(row, c.gps));
                put(body, "nConvention", ExcelReader.text(row, c.convention));
                Document commune = commune(school, ExcelReader.text(row, c.codeCommune), ExcelReader.text(row, c.commune),
                        ExcelReader.text(row, c.district));
                if (commune != null) {
                    put(body, "communeId", commune.getString("externalId"));
                    String fkt = ExcelReader.text(row, c.fokontany);
                    Document fokontany = fkt == null ? null : find(school, "Fokontany",
                            Criteria.where("communeId").is(commune.getString("externalId")).and("name").regex("^" + java.util.regex.Pattern.quote(fkt) + "$", "i"));
                    if (fokontany != null) put(body, "fokontanyId", fokontany.getString("externalId"));
                    else if (fkt != null) messages.add("fokontany « " + fkt + " » introuvable dans la commune");
                } else if (ExcelReader.text(row, c.commune) != null || ExcelReader.text(row, c.codeCommune) != null) {
                    messages.add("commune introuvable (code « " + ExcelReader.text(row, c.codeCommune) + " », nom « "
                            + ExcelReader.text(row, c.commune) + " ») : localisation non renseignée");
                }

                String projectId;
                Action action;
                if (existing == null) {
                    if (body.get("title") == null) throw new IllegalArgumentException("intitulé du sous-projet vide");
                    projectId = apply ? params.create(def, school, scope, dates(body), by) : null;
                    action = Action.CREATION;
                } else {
                    projectId = existing.getString("externalId");
                    if (apply) params.update(def, school, scope, projectId, dates(body), by);
                    action = Action.MISE_A_JOUR;
                }
                // Contrats des blocs « Dénomination » (BE, entreprise, fournisseur).
                for (int i = 0; i < c.blocks.size() && i < TYPES.length; i++) {
                    contract(school, row, c.blocks.get(i), c.contractNumbers[i], TYPES[i], projectId, contrats, apply, messages, by);
                }
                lines.add(new Line(line, action, numero + " — " + (body.get("title") == null ? existing.getString("title") : body.get("title")), messages));
            } catch (RuntimeException e) {
                lines.add(error(line, numero, e));
            }
        }
        return report(apply, x, lines);
    }

    /** Colonnes repérées par en-tête dans la fiche de suivi (deux modèles connus). */
    private static final class Columns {
        final int numero, codeCommune, title, infrastructure, typeTravaux, typeInfrastructure, secteur, tranche, vague,
                indicateur1, indicateur2, montantBE, montantEse, annee, gps, fokontany, commune, district, convention;
        final List<Integer> blocks;
        final int[] contractNumbers = new int[3];

        Columns(ExcelReader x) {
            numero = x.column("N°Dossier", "N° Dossier", "Numero dossier");
            codeCommune = x.column("Code Commune");
            title = x.column("Sous-projet", "Intitulé");
            infrastructure = x.column("Infrastructure");
            typeTravaux = x.column("Type de Travaux", "Type d'aménagement");
            typeInfrastructure = x.column("Type d'Infrastructure");
            secteur = x.column("Secteur");
            tranche = x.column("Tranche");
            vague = x.column("Vague/CYCLE", "Vague");
            int ind1 = x.column("Indicateurs 1");
            int ind2 = x.column("Indicateurs 2");
            indicateur1 = ind1 < 0 ? -1 : ind1 + 2; // « Indicateurs 1 », « Unité », « Quantité »
            indicateur2 = ind2 < 0 ? -1 : ind2 + 2; // « Indicateurs 2 », « Unité de dimension », « Dimension »
            montantBE = x.column("Montant prévisionnel BE");
            montantEse = x.column("Montant prévisionnel Ese");
            annee = x.column("Année de financement");
            gps = x.column("Coordonnées GPS", "Latitude,Longitude");
            fokontany = x.column("Fokontany");
            commune = x.column("Commune");
            district = x.column("District");
            blocks = x.columns("Dénomination");
            // N° de contrat et de convention : en-têtes dédiés, sinon positions fixes (colonnes 112 à 115).
            String[][] names = { { "N° contrat BE" }, { "N° contrat Ese", "N° contrat Entreprise" }, { "N° contrat Fournisseur" } };
            for (int i = 0; i < 3; i++) {
                int col = x.column(names[i]);
                contractNumbers[i] = col >= 0 ? col : (x.headers.size() <= 111 + i || x.headers.get(111 + i).isEmpty() ? 111 + i : -1);
            }
            int conv = x.column("N° Convention", "Numéro convention");
            convention = conv >= 0 ? conv : (x.headers.size() <= 114 ? 114 : -1);
        }
    }

    /** Contrat d'un bloc : prestataire par dénomination (créé s'il manque), contrat retrouvé par sous-projet + type + N°. */
    private void contract(String school, Row row, int block, int numberColumn, String type, String projectId, ParamDef contrats,
                          boolean apply, List<String> messages, String by) {
        String provider = ExcelReader.text(row, block);
        String numero = ExcelReader.text(row, numberColumn);
        if (provider == null) return;
        if (numero == null) {
            messages.add("contrat " + type + " non importé : N° de contrat absent");
            return;
        }
        Document existingProvider = find(school, "provider", Criteria.where("name").is(provider));
        String providerId = existingProvider == null ? null : existingProvider.getString("externalId");
        if (existingProvider == null) {
            messages.add("prestataire « " + provider + " » créé");
            if (apply) {
                providerId = params.create(params.definition("prestataires"), school, Map.of(), fields("name", provider,
                        "rcs", ExcelReader.text(row, block + 1), "nif", ExcelReader.text(row, block + 2),
                        "stat", ExcelReader.text(row, block + 3)), by);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        put(body, "title", numero);
        put(body, "typePrestataire", type);
        put(body, "providerExternalId", providerId);
        put(body, "projectExternalId", projectId);
        put(body, "dateContrat", ExcelReader.date(row, block + 4));
        put(body, "delaiContrat", ExcelReader.number(row, block + 5));
        put(body, "dateDebut", ExcelReader.date(row, block + 6));
        put(body, "montantContrat", ExcelReader.number(row, block + 7));
        if (!"FOURNISSEUR".equals(type)) put(body, "avancementPrevu", ExcelReader.number(row, block + 8));
        Document existing = projectId == null ? null : find(school, "Contrat", Criteria.where("projectExternalId").is(projectId)
                .and("typePrestataire").is(type).and("title").is(numero));
        if (existing != null) {
            if (apply) params.update(contrats, school, Map.of(), existing.getString("externalId"), dates(body), by);
            messages.add("contrat " + type + " « " + numero + " » mis à jour");
        } else {
            if (apply) params.create(contrats, school, Map.of(), dates(body), by);
            messages.add("contrat " + type + " « " + numero + " » créé");
        }
    }

    /** Référentiel par libellé (sans tenir compte de la casse), créé s'il manque. */
    private String reference(String school, String collection, String key, String title, boolean apply, Map<String, String> created,
                             List<String> messages, String by) {
        if (title == null) return null;
        Document found = find(school, collection, Criteria.where("title").regex("^" + java.util.regex.Pattern.quote(title) + "$", "i"));
        if (found != null) return found.getString("externalId");
        String cacheKey = collection + "|" + title.toLowerCase();
        if (!created.containsKey(cacheKey)) {
            messages.add(collection + " « " + title + " » créé(e)");
            created.put(cacheKey, apply ? params.create(params.definition(key), school, Map.of(), fields("title", title), by) : null);
        }
        return created.get(cacheKey);
    }

    /** Commune par code, sinon par nom dans le district, sinon par nom s'il est unique. */
    private Document commune(String school, String code, String name, String district) {
        if (code != null) {
            Document byCode = find(school, "Commune", Criteria.where("code").is(code.replaceAll("\\.0$", "")));
            if (byCode != null) return byCode;
        }
        if (name == null) return null;
        Criteria byName = Criteria.where("name").regex("^" + java.util.regex.Pattern.quote(name) + "$", "i");
        if (district != null) {
            Document d = find(school, "District", Criteria.where("name").regex("^" + java.util.regex.Pattern.quote(district) + "$", "i"));
            if (d != null) {
                Document c = find(school, "Commune", byName.and("districtId").is(d.getString("externalId")));
                if (c != null) return c;
            }
        }
        List<Document> candidates = mongo.find(Query.query(active(school).and("name")
                .regex("^" + java.util.regex.Pattern.quote(name) + "$", "i")), Document.class, "Commune");
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    // ============================ utilitaires ============================

    private Document find(String school, String collection, Criteria criteria) {
        Criteria c = active(school);
        criteria.getCriteriaObject().forEach((k, v) -> c.and(k).is(v));
        return mongo.findOne(Query.query(c), Document.class, collection);
    }

    /** Les champs date de ParamService attendent yyyy-MM-dd. */
    private static Map<String, Object> dates(Map<String, Object> body) {
        Map<String, Object> out = new LinkedHashMap<>();
        body.forEach((k, v) -> out.put(k, v instanceof Date d ? d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate().toString() : v));
        return out;
    }

    private static void put(Map<String, Object> body, String key, Object value) {
        if (value != null) body.put(key, value);
    }

    private static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) if (keyValues[i + 1] != null) map.put((String) keyValues[i], keyValues[i + 1]);
        return map;
    }

    private static Line error(int line, String label, RuntimeException e) {
        String message = e instanceof ResponseStatusException r ? r.getReason() : e.getMessage();
        return new Line(line, Action.ERREUR, label, List.of(message == null ? "erreur inattendue" : message));
    }

    private Report report(boolean apply, ExcelReader x, List<Line> lines) {
        Report r = new Report(apply, lines.size(), count(lines, Action.CREATION), count(lines, Action.MISE_A_JOUR),
                count(lines, Action.IGNOREE), count(lines, Action.ERREUR), x.headers, lines);
        if (apply) log.info("Import appliqué : {} lignes, {} créations, {} mises à jour, {} erreurs", r.lignes(), r.creations(), r.misesAJour(), r.erreurs());
        return r;
    }

    private static int count(List<Line> lines, Action action) {
        return (int) lines.stream().filter(l -> l.action() == action).count();
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }
}
