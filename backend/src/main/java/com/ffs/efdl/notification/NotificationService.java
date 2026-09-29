package com.ffs.efdl.notification;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import com.ffs.efdl.service.MailNotifier;

/**
 * Notifications personnalisées : dans l'application (collection {@code user_notification},
 * nouvelle) et/ou par e-mail, selon les préférences de chaque utilisateur par type
 * ({@code user_notification_pref}, nouvelle). Chacun ne voit et ne règle que ses propres notifications.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    static final String COLLECTION = "user_notification";
    static final String PREFS = "user_notification_pref";

    /** Types, avec leurs réglages par défaut (e-mail activé pour les notifications essentielles). */
    public enum Type {
        VALIDATION_ATTENTE("Facture à valider", "Une facture arrive à votre étape du circuit de validation", true, true),
        VALIDATION_RETARD("Retard de validation", "Le délai de traitement de votre étape est dépassé", true, true),
        FACTURE_VALIDEE("Facture validée", "Une facture que vous avez saisie est validée par le dernier validateur", true, false),
        FACTURE_REJETEE("Facture rejetée", "Une facture que vous avez saisie est rejetée", true, false);

        public final String label;
        public final String description;
        final boolean appDefault;
        final boolean mailDefault;

        Type(String label, String description, boolean appDefault, boolean mailDefault) {
            this.label = label;
            this.description = description;
            this.appDefault = appDefault;
            this.mailDefault = mailDefault;
        }
    }

    public record Item(String id, String type, String title, String message, String link, boolean read, Date date) {
    }

    public record Page(List<Item> items, long total, long unread) {
    }

    public record Preference(String type, String label, String description, boolean app, boolean mail) {
    }

    private final MongoTemplate mongo;
    private final MailNotifier mail;

    public NotificationService(MongoTemplate mongo, MailNotifier mail) {
        this.mongo = mongo;
        this.mail = mail;
    }

    // ---------- envoi ----------

    /**
     * Notifie un utilisateur selon ses préférences : notification dans l'application et/ou e-mail
     * ({@code mailSubject}, {@code mailBody} reçoit le nom affiché). Ne fait jamais échouer l'action d'origine.
     */
    public void notify(String school, String userId, Type type, String title, String message, String link,
                       String mailSubject, Function<String, String> mailBody) {
        if (userId == null) return;
        try {
            Preference pref = preference(userId, type);
            if (pref.app()) {
                mongo.getCollection(COLLECTION).insertOne(new Document("className", "com.ffs.efdl.model.UserNotification")
                        .append("schoolExternalId", school).append("userExternalId", userId).append("type", type.name())
                        .append("title", title).append("message", message).append("link", link)
                        .append("read", false).append("date", new Date()));
            }
            if (pref.mail() && mail.isConfigured()) {
                mail.notifyUser(school, userId, mailSubject, mailBody);
            }
        } catch (RuntimeException e) {
            log.error("Notification {} impossible pour l'utilisateur {}", type, userId, e);
        }
    }

    // ---------- consultation (ses propres notifications) ----------

    public Page list(String userId, boolean unreadOnly, int page, int size) {
        Criteria c = Criteria.where("userExternalId").is(userId);
        if (unreadOnly) c = c.and("read").is(false);
        Query q = Query.query(c);
        long total = mongo.count(q, COLLECTION);
        q.with(Sort.by(Sort.Direction.DESC, "date")).skip((long) page * size).limit(size);
        List<Item> items = mongo.find(q, Document.class, COLLECTION).stream()
                .map(d -> new Item(d.getObjectId("_id").toHexString(), d.getString("type"), d.getString("title"),
                        d.getString("message"), d.getString("link"), Boolean.TRUE.equals(d.getBoolean("read")), d.getDate("date")))
                .toList();
        return new Page(items, total, unread(userId));
    }

    public long unread(String userId) {
        return mongo.count(Query.query(Criteria.where("userExternalId").is(userId).and("read").is(false)), COLLECTION);
    }

    public void markRead(String userId, String id) {
        if (!ObjectId.isValid(id)) return;
        mongo.updateFirst(Query.query(Criteria.where("_id").is(new ObjectId(id)).and("userExternalId").is(userId)),
                new Update().set("read", true), COLLECTION);
    }

    public void markAllRead(String userId) {
        mongo.updateMulti(Query.query(Criteria.where("userExternalId").is(userId).and("read").is(false)),
                new Update().set("read", true), COLLECTION);
    }

    // ---------- préférences ----------

    public List<Preference> preferences(String userId) {
        List<Preference> result = new ArrayList<>();
        for (Type t : Type.values()) result.add(preference(userId, t));
        return result;
    }

    public void savePreferences(String userId, String school, List<Preference> prefs) {
        Map<String, Object> types = new LinkedHashMap<>();
        for (Preference p : prefs) {
            Type t;
            try {
                t = Type.valueOf(p.type());
            } catch (IllegalArgumentException e) {
                continue;
            }
            types.put(t.name(), new Document("app", p.app()).append("mail", p.mail()));
        }
        mongo.upsert(Query.query(Criteria.where("userExternalId").is(userId)),
                new Update().set("types", new Document(types)).set("schoolExternalId", school).set("lastUpdateDate", new Date())
                        .setOnInsert("className", "com.ffs.efdl.model.UserNotificationPreference"),
                PREFS);
    }

    private Preference preference(String userId, Type type) {
        Document doc = mongo.findOne(Query.query(Criteria.where("userExternalId").is(userId)), Document.class, PREFS);
        Document t = doc == null || doc.get("types", Document.class) == null ? null : doc.get("types", Document.class).get(type.name(), Document.class);
        boolean app = t == null || t.getBoolean("app") == null ? type.appDefault : t.getBoolean("app");
        boolean mailOn = t == null || t.getBoolean("mail") == null ? type.mailDefault : t.getBoolean("mail");
        return new Preference(type.name(), type.label, type.description, app, mailOn);
    }
}
