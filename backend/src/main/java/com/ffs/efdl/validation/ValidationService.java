package com.ffs.efdl.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.ffs.efdl.model.BaseEntity;
import com.ffs.efdl.params.CreationListener;
import com.ffs.efdl.params.RowEnricher;
import com.ffs.efdl.notification.NotificationService;
import com.ffs.efdl.service.AuditService;
import com.ffs.efdl.service.MailNotifier;

/**
 * Circuit de validation des factures (admin/project/validationPaiement), seul lieu de validation.
 * Règles :
 * <ul>
 *   <li>le programme d'une facture est celui du contrat rattaché à son {@code groupeContratExternalId} ;</li>
 *   <li>circuit = validateurs actifs du programme triés par {@code ordre} ; une facture au niveau {@code n}
 *       (validations déjà faites) attend le validateur de rang {@code n} ;</li>
 *   <li>seul ce validateur peut valider ou rejeter ; la dernière validation passe la facture à VALIDATE,
 *       un rejet à REJECTED ; trace dans {@code statusUserId} / {@code dateValidationId} ;</li>
 *   <li>mise à jour conditionnelle (statut et niveau attendus) : une facture n'est jamais traitée deux fois ;</li>
 *   <li>e-mail « VALIDATION DE PAIEMENT » au 1er validateur quand une facture est saisie, puis au
 *       validateur suivant après chaque validation intermédiaire ; rien au rejet ni à la validation finale.</li>
 * </ul>
 */
@Service
public class ValidationService implements RowEnricher, CreationListener {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    static final String PENDING = "PENDING";
    static final String VALIDATE = "VALIDATE";
    static final String REJECTED = "REJECTED";

    /** Validateur d'un circuit, avec le libellé de l'utilisateur. */
    public record Step(String validateurId, int ordre, String title, String description, String userExternalId,
                       String userName, List<String> questionIds, int maxJours) {
    }

    /**
     * Attente à l'étape en cours : depuis la dernière validation (ou la saisie de la facture), en jours pleins ;
     * en retard au-delà du « Nb jour de traitement max » de l'étape (0 = sans délai).
     */
    public record Delay(Date depuis, long jours, int maxJours, boolean enRetard) {
    }

    public record Row(String externalId, Date creationDate, String numeroFacture, String intituleFacture, String prestataire,
                      String contrat, String projet, double montant, String prochainValidateur, String status,
                      Long attenteJours, Integer delaiMax, boolean enRetard) {
    }

    public record HistoryItem(String userName, String status, Date date) {
    }

    public record Detail(Row facture, String numeroAttachement, String intituleAttachement, double montantContrat,
                         double montantPaye, Step etape, List<String> questions, List<HistoryItem> historique,
                         int niveau, int etapes, boolean canValidate) {
    }

    static final String MAIL_SUBJECT = "VALIDATION DE PAIEMENT";

    private final MongoTemplate mongo;
    private final MailNotifier mail;

    private final AuditService audit;

    private final NotificationService notifications;

    public ValidationService(MongoTemplate mongo, MailNotifier mail, AuditService audit, NotificationService notifications) {
        this.notifications = notifications;
        this.audit = audit;
        this.mongo = mongo;
        this.mail = mail;
    }

    // ---------- lecture ----------

    /** Factures PENDING qui attendent l'utilisateur, les plus anciennes d'abord. */
    public List<Row> pendingFor(String school, String userExternalId) {
        List<Row> rows = new ArrayList<>();
        Map<String, List<Step>> circuits = new HashMap<>();
        for (Document p : mongo.find(Query.query(Criteria.where("schoolExternalId").is(school).and("status").is(PENDING))
                .with(Sort.by("creationDate")), Document.class, "Paiement")) {
            Optional<Step> next = nextStep(school, p, circuits);
            if (next.isPresent() && userExternalId.equals(next.get().userExternalId())) {
                rows.add(toRow(school, p, next));
            }
        }
        return rows;
    }

    public Detail detail(String school, String id, String userExternalId) {
        Document p = paiement(school, id);
        Map<String, List<Step>> circuits = new HashMap<>();
        Optional<Step> next = nextStep(school, p, circuits);
        List<Step> circuit = circuit(school, programmeOf(school, p), circuits);
        String groupe = p.getString("groupeContratExternalId");
        Document contrat = groupe == null ? null : mongo.findOne(Query.query(active(school).and("externalId").is(groupe)),
                Document.class, "GroupeContrat");
        List<String> questions = next.map(s -> s.questionIds().stream().map(q -> label("Question", school, q, "question"))
                .filter(java.util.Objects::nonNull).toList()).orElse(List.of());
        return new Detail(toRow(school, p, next), p.getString("numeroAttachement"), p.getString("intituleAttachement"),
                number(contrat == null ? null : contrat.get("montantContrat")), montantPaye(school, groupe),
                next.orElse(null), questions, history(school, p), niveau(p), circuit.size(),
                next.isPresent() && userExternalId.equals(next.get().userExternalId()));
    }

    /** Colonnes « Projet » et « Prochain validateur » de la liste des factures. */
    @Override
    public String key() {
        return "paiements";
    }

    @Override
    public void enrich(String school, List<Map<String, Object>> rows) {
        // Contrats et numéros de sous-projet chargés une fois pour toute la liste.
        Map<String, Document> contratByGroupe = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)).with(Sort.by("creationDate")), Document.class, "Contrat")
                .forEach(c -> contratByGroupe.putIfAbsent(c.getString("groupeContratExternalId"), c));
        Map<String, String> numeros = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school)), Document.class, "Project")
                .forEach(pr -> numeros.put(pr.getString("externalId"), pr.getString("numero")));
        Map<String, List<Step>> circuits = new HashMap<>();
        // Factures en attente (dates de validation pour le délai), chargées une fois.
        Map<String, Document> pending = new HashMap<>();
        mongo.find(Query.query(Criteria.where("schoolExternalId").is(school).and("status").is(PENDING)), Document.class, "Paiement")
                .forEach(p -> pending.put(p.getString("externalId"), p));
        for (Map<String, Object> row : rows) {
            Document contrat = contratByGroupe.get((String) row.get("groupeContratExternalId"));
            row.put("projetNumero", contrat == null ? null : numeros.get(contrat.getString("projectExternalId")));
            if (!PENDING.equals(row.get("status"))) {
                row.put("prochainValidateur", null);
                continue;
            }
            String programme = contrat == null ? (String) row.get("programmeExternalId") : contrat.getString("programmeExternalId");
            List<Step> circuit = circuit(school, programme, circuits);
            int niveau = row.get("niveauValidation") instanceof Number n ? n.intValue() : 0;
            row.put("prochainValidateur", niveau < circuit.size() ? circuit.get(niveau).userName() : "Aucun validateur");
            Document p = pending.get((String) row.get("externalId"));
            if (niveau < circuit.size() && p != null) {
                Delay delay = delay(p, circuit.get(niveau), new Date());
                row.put("attenteJours", delay.jours());
                row.put("delaiMax", delay.maxJours() > 0 ? delay.maxJours() : null);
                row.put("enRetard", delay.enRetard());
            }
        }
    }

    /** Attente de la facture à l'étape {@code step} (voir {@link Delay}). */
    static Delay delay(Document p, Step step, Date now) {
        Date depuis = p.getDate("creationDate");
        Document dates = p.get("dateValidationId", Document.class);
        if (dates != null) {
            for (Object d : dates.values()) {
                if (d instanceof Date date && (depuis == null || date.after(depuis))) depuis = date;
            }
        }
        long jours = depuis == null ? 0 : Math.max(0, (now.getTime() - depuis.getTime()) / 86_400_000L);
        return new Delay(depuis, jours, step.maxJours(), step.maxJours() > 0 && jours > step.maxJours());
    }

    /** Factures en attente dont le délai de l'étape en cours est dépassé : facture, étape, attente. */
    public record Late(String school, Document paiement, Step step, Delay delay) {
    }

    public List<Late> late(Date now) {
        List<Late> result = new ArrayList<>();
        Map<String, Map<String, List<Step>>> circuitsBySchool = new HashMap<>();
        for (Document p : mongo.find(Query.query(Criteria.where("status").is(PENDING)), Document.class, "Paiement")) {
            String school = p.getString("schoolExternalId");
            if (school == null) continue;
            nextStep(school, p, circuitsBySchool.computeIfAbsent(school, s -> new HashMap<>())).ifPresent(step -> {
                Delay delay = delay(p, step, now);
                if (delay.enRetard()) result.add(new Late(school, p, step, delay));
            });
        }
        return result;
    }

    // ---------- décisions ----------

    public void validate(String school, String id, String userExternalId) {
        decide(school, id, userExternalId, true);
    }

    public void reject(String school, String id, String userExternalId) {
        decide(school, id, userExternalId, false);
    }

    private void decide(String school, String id, String userExternalId, boolean approve) {
        Document p = paiement(school, id);
        if (!PENDING.equals(p.getString("status"))) throw conflict("Cette facture a déjà été traitée");
        Map<String, List<Step>> circuits = new HashMap<>();
        List<Step> circuit = circuit(school, programmeOf(school, p), circuits);
        int niveau = niveau(p);
        if (niveau >= circuit.size()) throw conflict("Aucun validateur n'est défini pour cette étape du programme");
        if (!userExternalId.equals(circuit.get(niveau).userExternalId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette facture n'attend pas votre validation");
        }
        String status = approve ? (niveau + 1 >= circuit.size() ? VALIDATE : PENDING) : REJECTED;
        Date now = new Date();
        Update update = new Update()
                .set("statusUserId." + userExternalId, approve ? VALIDATE : REJECTED)
                .set("dateValidationId." + userExternalId, now)
                .set("niveauValidation", niveau + 1)
                .set("status", status)
                .set("lastUpdateDate", now)
                .set("lastUpdateUserId", userExternalId);
        // Conditionnel : même statut et même niveau qu'à la lecture (pas de double traitement concurrent).
        Query query = Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(id)
                .and("status").is(PENDING).and("niveauValidation").is(niveau));
        if (mongo.updateFirst(query, update, "Paiement").getModifiedCount() == 0) {
            throw conflict("Cette facture vient d'être traitée par ailleurs : rechargez la page");
        }
        log.info("Facture {} {} au niveau {} par {} (statut {})", id, approve ? "validée" : "rejetée", niveau + 1, userExternalId, status);
        audit.record(school, userExternalId, approve ? AuditService.VALIDATION : AuditService.REJET, "Paiement", id,
                p.getString("numeroFacture"), List.of(
                        new AuditService.Change("niveauValidation", "Étape", String.valueOf(niveau), String.valueOf(niveau + 1)),
                        new AuditService.Change("status", "Statut", PENDING, status)));
        if (PENDING.equals(status)) notifyValidator(school, circuit.get(niveau + 1), p);
        else notifyCreator(school, p, userExternalId, approve);
    }

    // ---------- notifications ----------

    /** Nouvelle facture saisie à l'écran : le 1er validateur du programme est prévenu. */
    @Override
    public void created(String school, String id) {
        Document p = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, "Paiement");
        if (p == null) return;
        nextStep(school, p, new HashMap<>()).ifPresent(step -> notifyValidator(school, step, p));
    }

    /**
     * Facture à valider : notification (et e-mail, adresse relue dans la base) au validateur de
     * l'étape, selon ses préférences.
     */
    private void notifyValidator(String school, Step step, Document p) {
        String url = mail.link("/admin/project/validationPaiements");
        notifications.notify(school, step.userExternalId(), NotificationService.Type.VALIDATION_ATTENTE,
                "Facture à valider : " + nz(p.getString("numeroFacture")),
                "La facture N° " + nz(p.getString("numeroFacture")) + " (" + montant(p) + " MGA) attend votre validation.",
                "/admin/project/validationPaiement/edit/" + p.getString("externalId"),
                MAIL_SUBJECT, name -> "Bonjour " + name + ",\n\n"
                        + "Vous avez des paiements en attente de validation.\n"
                        + "Pour voir les détails : " + url + "\n");
    }

    /** Validation finale ou rejet : la personne qui a saisi la facture est prévenue (sauf si c'est elle qui décide). */
    private void notifyCreator(String school, Document p, String decidedBy, boolean approved) {
        String creator = creatorOf(p);
        if (creator == null || creator.equals(decidedBy)) return;
        String numero = nz(p.getString("numeroFacture"));
        String who = userName(decidedBy);
        NotificationService.Type type = approved ? NotificationService.Type.FACTURE_VALIDEE : NotificationService.Type.FACTURE_REJETEE;
        String title = (approved ? "Facture validée : " : "Facture rejetée : ") + numero;
        String message = "La facture N° " + numero + " (" + montant(p) + " MGA) a été " + (approved ? "validée" : "rejetée")
                + (who == null ? "" : " par " + who) + ".";
        String url = mail.link("/admin/project/paiement/edit/" + p.getString("externalId"));
        notifications.notify(school, creator, type, title, message, "/admin/project/paiement/edit/" + p.getString("externalId"),
                "PAIEMENT " + (approved ? "VALIDE" : "REJETE"), name -> "Bonjour " + name + ",\n\n" + message + "\n"
                        + "Pour voir la facture : " + url + "\n");
    }

    /** Auteur de la saisie : historique des modifications, sinon champ de création éventuel. */
    private String creatorOf(Document p) {
        Document created = mongo.findOne(Query.query(Criteria.where("collection").is("Paiement")
                .and("targetExternalId").is(p.getString("externalId")).and("action").is(AuditService.CREATION)), Document.class,
                AuditService.COLLECTION);
        if (created != null) return created.getString("userExternalId");
        return p.getString("creationUserId");
    }

    private static String montant(Document p) {
        return java.text.NumberFormat.getNumberInstance(java.util.Locale.FRANCE)
                .format(p.get("montant") instanceof Number n ? n.doubleValue() : 0).replace('\u202F', ' ').replace('\u00A0', ' ');
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    // ---------- circuit ----------

    private Optional<Step> nextStep(String school, Document p, Map<String, List<Step>> circuits) {
        if (!PENDING.equals(p.getString("status"))) return Optional.empty();
        List<Step> circuit = circuit(school, programmeOf(school, p), circuits);
        int niveau = niveau(p);
        return niveau < circuit.size() ? Optional.of(circuit.get(niveau)) : Optional.empty();
    }

    /** Programme du contrat de la facture (premier contrat du groupe). */
    String programmeOf(String school, Document p) {
        String groupe = p.getString("groupeContratExternalId");
        if (groupe != null) {
            Query query = Query.query(active(school).and("groupeContratExternalId").is(groupe)).with(Sort.by("creationDate"));
            Document contrat = mongo.findOne(query, Document.class, "Contrat");
            if (contrat != null && contrat.getString("programmeExternalId") != null) return contrat.getString("programmeExternalId");
        }
        return p.getString("programmeExternalId");
    }

    private List<Step> circuit(String school, String programme, Map<String, List<Step>> cache) {
        if (programme == null) return List.of();
        return cache.computeIfAbsent(programme, prog -> mongo.find(Query.query(active(school).and("programmeExternalId").is(prog)),
                        Document.class, "Validateur").stream()
                .sorted(Comparator.comparingInt((Document v) -> v.get("ordre") instanceof Number n ? n.intValue() : 0)
                        .thenComparing(v -> v.getDate("creationDate"), Comparator.nullsLast(Comparator.naturalOrder())))
                .map(v -> new Step(v.getString("externalId"), v.get("ordre") instanceof Number n ? n.intValue() : 0,
                        v.getString("title"), v.getString("description"), v.getString("userExternalId"),
                        userName(v.getString("userExternalId")), v.getList("questionIds", String.class, List.of()),
                        v.get("maxJourTraitement") instanceof Number n ? n.intValue() : 0))
                .toList());
    }

    // ---------- utilitaires ----------

    private Document paiement(String school, String id) {
        Document p = mongo.findOne(Query.query(active(school).and("externalId").is(id)), Document.class, "Paiement");
        if (p == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Facture introuvable");
        return p;
    }

    private Row toRow(String school, Document p, Optional<Step> next) {
        String groupe = p.getString("groupeContratExternalId");
        Delay delay = next.map(s -> delay(p, s, new Date())).orElse(null);
        return new Row(p.getString("externalId"), p.getDate("creationDate"), p.getString("numeroFacture"),
                p.getString("intituleFacture"), label("provider", school, p.getString("providerExternalId"), "name"),
                label("GroupeContrat", school, groupe, "title"), projetNumero(school, groupe), number(p.get("montant")),
                next.map(Step::userName).orElse(null), p.getString("status"),
                delay == null ? null : delay.jours(), delay == null || delay.maxJours() <= 0 ? null : delay.maxJours(),
                delay != null && delay.enRetard());
    }

    private String projetNumero(String school, String groupe) {
        if (groupe == null) return null;
        Document contrat = mongo.findOne(Query.query(Criteria.where("schoolExternalId").is(school)
                .and("groupeContratExternalId").is(groupe)).with(Sort.by("creationDate")), Document.class, "Contrat");
        return contrat == null ? null : label("Project", school, contrat.getString("projectExternalId"), "numero");
    }

    private double montantPaye(String school, String groupe) {
        if (groupe == null) return 0;
        return mongo.find(Query.query(Criteria.where("schoolExternalId").is(school).and("groupeContratExternalId").is(groupe)
                .and("status").is(VALIDATE)), Document.class, "Paiement").stream().mapToDouble(d -> number(d.get("montant"))).sum();
    }

    private List<HistoryItem> history(String school, Document p) {
        Document statuses = p.get("statusUserId", Document.class);
        Document dates = p.get("dateValidationId", Document.class);
        if (statuses == null) return List.of();
        List<HistoryItem> items = new ArrayList<>();
        statuses.forEach((user, status) -> items.add(new HistoryItem(userName(user), String.valueOf(status),
                dates == null ? null : dates.getDate(user))));
        items.sort(Comparator.comparing(HistoryItem::date, Comparator.nullsLast(Comparator.naturalOrder())));
        return items;
    }

    private String userName(String userExternalId) {
        if (userExternalId == null) return null;
        Document u = mongo.findOne(Query.query(Criteria.where("externalId").is(userExternalId)), Document.class, "user");
        if (u == null) return null;
        String name = ((u.getString("lastName") == null ? "" : u.getString("lastName"))
                + (u.getString("firstName") == null ? "" : " " + u.getString("firstName"))).replace("'", " ").trim();
        return name.isEmpty() ? u.getString("username") : name;
    }

    private String label(String collection, String school, String id, String field) {
        if (id == null) return null;
        Document d = mongo.findOne(Query.query(Criteria.where("schoolExternalId").is(school).and("externalId").is(id)),
                Document.class, collection);
        return d == null ? null : d.getString(field);
    }

    private static int niveau(Document p) {
        return p.get("niveauValidation") instanceof Number n ? n.intValue() : 0;
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }

    private static Criteria active(String school) {
        return Criteria.where("schoolExternalId").is(school).and("status").ne(BaseEntity.STATUS_DELETED);
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }
}
