package com.ffs.efdl.service;

import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import com.ffs.efdl.model.BaseEntity;
import com.mongodb.client.result.UpdateResult;

/**
 * Écritures des documents de la base.
 * <ul>
 *   <li><b>Création</b> : document complet ({@code _id} ObjectId, {@code className},
 *       {@code externalId}, {@code schoolExternalId}, {@code orderPlace}, dates) ; valeurs nulles non écrites.</li>
 *   <li><b>Modification</b> : {@code $set} des seuls champs de l'écran (et {@code $unset} des champs vidés,
 *       équivalent d'une valeur nulle). Les autres champs du document ne sont jamais touchés :
 *       les Models ne contiennent qu'une partie des champs, un remplacement complet
 *       effacerait les autres.</li>
 * </ul>
 * Aucune suppression de document : une « suppression » est un {@code status=DELETED} ({@link #markDeleted}).
 */
@Component
public class DocumentWriter {

    private static final Logger log = LoggerFactory.getLogger(DocumentWriter.class);

    private final MongoTemplate mongo;
    private final AuditService audit;

    public DocumentWriter(MongoTemplate mongo, AuditService audit) {
        this.mongo = mongo;
        this.audit = audit;
    }

    /** Nouvel identifiant métier, comme IdUtils.generateExternalId() (UUID). */
    public static String newExternalId() {
        return UUID.randomUUID().toString();
    }

    /**
     * Crée un document d'une classe héritant de BaseSchoolEntity (avec {@code schoolExternalId} et {@code orderPlace}).
     */
    public String insert(String collection, String className, String externalId, String schoolExternalId,
                         Map<String, Object> fields, String userExternalId) {
        return insert(collection, className, externalId, schoolExternalId, true, fields, userExternalId);
    }

    /**
     * @param schoolScoped vrai pour les classes BaseSchoolEntity ; faux pour MongoExternalEntity (ex. School),
     *                     qui n'ont ni {@code schoolExternalId} ni {@code orderPlace}
     */
    public String insert(String collection, String className, String externalId, String schoolExternalId,
                         boolean schoolScoped, Map<String, Object> fields, String userExternalId) {
        Date now = new Date();
        Document doc = new Document("_id", new ObjectId()).append("className", className);
        fields.forEach((k, v) -> {
            if (v != null) doc.append(k, v);
        });
        if (schoolScoped) {
            if (schoolExternalId != null) doc.append("schoolExternalId", schoolExternalId);
            doc.append("orderPlace", 0);
        }
        doc.append("externalId", externalId)
                .append("creationDate", now)
                .append("lastUpdateDate", now);
        if (userExternalId != null) doc.append("lastUpdateUserId", userExternalId);
        mongo.getCollection(collection).insertOne(doc);
        log.info("Création {} externalId={} par {}", collection, externalId, userExternalId);
        audit.created(collection, externalId, schoolScoped ? schoolExternalId : null, fields, userExternalId); // historique
        return externalId;
    }

    /**
     * Met à jour les champs donnés du document identifié par {@code externalId} (et l'école si fournie).
     * @return vrai si un document a été trouvé
     */
    public boolean update(String collection, String externalId, String schoolExternalId,
                          Map<String, Object> fields, String userExternalId) {
        Criteria criteria = Criteria.where("externalId").is(externalId);
        if (schoolExternalId != null) criteria = criteria.and("schoolExternalId").is(schoolExternalId);
        criteria = criteria.and("status").ne(BaseEntity.STATUS_DELETED); // un élément supprimé n'est plus modifiable
        Update update = new Update();
        fields.forEach((k, v) -> {
            if (v == null) update.unset(k); else update.set(k, v);
        });
        update.set("lastUpdateDate", new Date());
        if (userExternalId != null) update.set("lastUpdateUserId", userExternalId);
        Document before = mongo.findOne(Query.query(criteria), Document.class, collection); // valeurs « avant » de l'historique
        UpdateResult result = mongo.updateFirst(Query.query(criteria), update, collection);
        if (result.getMatchedCount() > 0) {
            audit.updated(collection, externalId, schoolExternalId, before, fields, userExternalId);
            log.info("Modification {} externalId={} champs={} par {}", collection, externalId, fields.keySet(), userExternalId);
            return true;
        }
        return false;
    }

    /**
     * Suppression logique : {@code status=DELETED}, le document reste en base.
     * @return vrai si un document actif a été trouvé
     */
    public boolean markDeleted(String collection, String externalId, String schoolExternalId, String userExternalId) {
        boolean done = update(collection, externalId, schoolExternalId,
                fields("status", BaseEntity.STATUS_DELETED), userExternalId);
        if (done) log.info("Suppression logique {} externalId={} par {}", collection, externalId, userExternalId);
        return done;
    }

    /** Map ordonnée tolérant les valeurs nulles (Map.of les refuse). */
    public static Map<String, Object> fields(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], keyValues[i + 1]);
        }
        return map;
    }
}
