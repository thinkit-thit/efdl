package com.ffs.efdl.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ffs.efdl.model.BaseEntity;

import jakarta.annotation.PreDestroy;

/**
 * Notifications par e-mail aux utilisateurs (SMTP de backend/config/mail.properties).
 * <ul>
 *   <li>le destinataire est toujours relu dans la base ({@code user.email}) au moment de l'envoi : jamais d'adresse
 *       copiée ailleurs ni mise en cache ; compte supprimé ou sans adresse = pas d'envoi ;</li>
 *   <li>envoi en arrière-plan : une panne SMTP ne bloque ni ne fait échouer l'action de l'utilisateur (elle est journalisée) ;</li>
 *   <li>SMTP non configuré = aucune notification (l'application fonctionne sans).</li>
 * </ul>
 */
@Service
public class MailNotifier {

    private static final Logger log = LoggerFactory.getLogger(MailNotifier.class);

    private final MongoTemplate mongo;
    private final ObjectProvider<JavaMailSender> mailSender;
    private final String from;
    private final String appUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "efdl-mail");
        t.setDaemon(true);
        return t;
    });

    public MailNotifier(MongoTemplate mongo, ObjectProvider<JavaMailSender> mailSender,
                        @Value("${efdl.mail.from:}") String from,
                        @Value("${efdl.mail.app-url:http://localhost:5173}") String appUrl) {
        this.mongo = mongo;
        this.mailSender = mailSender;
        this.from = from;
        this.appUrl = appUrl.endsWith("/") ? appUrl.substring(0, appUrl.length() - 1) : appUrl;
    }

    /** Vrai si un serveur SMTP est configuré (sinon aucune notification n'est envoyée). */
    public boolean isConfigured() {
        return mailSender.getIfAvailable() != null;
    }

    /** Lien absolu vers une page de l'application (ex. « /admin/project/validationPaiements »). */
    public String link(String path) {
        return appUrl + path;
    }

    /**
     * Envoie un message à un utilisateur de l'école, adresse relue dans la base à l'envoi.
     * {@code body} reçoit le nom affiché de l'utilisateur.
     */
    public void notifyUser(String school, String userExternalId, String subject, java.util.function.Function<String, String> body) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || userExternalId == null) return;
        executor.execute(() -> {
            try {
                Document user = mongo.findOne(Query.query(Criteria.where("externalId").is(userExternalId)
                        .and("schoolExternalId").is(school)), Document.class, "user");
                if (user == null || BaseEntity.STATUS_DELETED.equals(user.getString("status"))) {
                    log.info("Notification « {} » non envoyée : utilisateur {} introuvable ou supprimé", subject, userExternalId);
                    return;
                }
                String email = user.getString("email");
                if (!StringUtils.hasText(email)) {
                    log.info("Notification « {} » non envoyée : l'utilisateur {} n'a pas d'adresse e-mail", subject, userExternalId);
                    return;
                }
                SimpleMailMessage message = new SimpleMailMessage();
                if (StringUtils.hasText(from)) message.setFrom(from);
                message.setTo(email.trim());
                message.setSubject(subject);
                message.setText(body.apply(displayName(user)));
                sender.send(message);
                log.info("Notification « {} » envoyée à l'utilisateur {}", subject, userExternalId);
            } catch (RuntimeException e) {
                log.error("Échec de la notification « {} » à l'utilisateur {}", subject, userExternalId, e);
            }
        });
    }

    private static String displayName(Document u) {
        String name = ((u.getString("lastName") == null ? "" : u.getString("lastName"))
                + (u.getString("firstName") == null ? "" : " " + u.getString("firstName"))).trim();
        return name.isEmpty() ? u.getString("username") : name;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
