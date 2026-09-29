package com.ffs.efdl.service;

import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bson.Document;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ffs.efdl.model.User;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Historique des connexions : collection {@code history} (historyType CONNECTION,
 * connectionType LOGIN, adresse IP dans {@code content}). La liste des utilisateurs
 * en tire la date de dernière connexion.
 */
@Service
public class LoginHistoryService {

    static final String COLLECTION = "history";
    static final String CLASS_NAME = "com.ffs.kiwi.model.notification.History";

    private final DocumentWriter writer;
    private final MongoTemplate mongo;

    public LoginHistoryService(DocumentWriter writer, MongoTemplate mongo) {
        this.writer = writer;
        this.mongo = mongo;
    }

    /** Connexion réussie (titre, IP, notification à « admin »). */
    public void recordLogin(User user, String school, HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        String ip = StringUtils.hasText(forwarded) ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
        writer.insert(COLLECTION, CLASS_NAME, DocumentWriter.newExternalId(), school,
                DocumentWriter.fields("userId", user.getExternalId(), "title", "Login successful :" + user.getDisplayName(),
                        "content", ip, "notificationUserIds", List.of("admin"), "url", "", "type", "",
                        "historyType", "CONNECTION", "connectionType", "LOGIN"),
                user.getExternalId());
    }

    /** Date de dernière connexion de chaque utilisateur (une seule requête pour une liste). */
    public Map<String, Date> lastLogins(String school, Collection<String> userIds) {
        Map<String, Date> result = new HashMap<>();
        if (userIds.isEmpty()) return result;
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("userId").in(Set.copyOf(userIds)).and("schoolExternalId").is(school)
                        .and("historyType").is("CONNECTION").and("connectionType").is("LOGIN")),
                Aggregation.group("userId").max("creationDate").as("last"));
        mongo.aggregate(aggregation, COLLECTION, Document.class).forEach(d -> result.put(d.getString("_id"), d.getDate("last")));
        return result;
    }
}
