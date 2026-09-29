package com.ffs.efdl.validation;

import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.ffs.efdl.notification.NotificationService;
import com.ffs.efdl.service.DocumentWriter;
import com.ffs.efdl.service.MailNotifier;

/**
 * Retards de validation : vérification périodique ({@code efdl.validation.delay-check-cron},
 * par défaut chaque heure) ; le validateur d'une facture dont le délai de l'étape est dépassé est notifié (dans
 * l'application et/ou par e-mail selon ses préférences),
 * une seule fois par facture et par étape (trace dans {@code payment_delay_notice}, nouvelle collection).
 */
@Service
public class ValidationDelayNotifier {

    private static final Logger log = LoggerFactory.getLogger(ValidationDelayNotifier.class);

    static final String COLLECTION = "payment_delay_notice";
    static final String CLASS_NAME = "com.ffs.efdl.model.PaymentDelayNotice";
    static final String MAIL_SUBJECT = "VALIDATION DE PAIEMENT EN RETARD";

    private final ValidationService validation;
    private final MailNotifier mail;
    private final MongoTemplate mongo;
    private final DocumentWriter writer;

    private final NotificationService notifications;

    public ValidationDelayNotifier(ValidationService validation, MailNotifier mail, MongoTemplate mongo, DocumentWriter writer,
                                   NotificationService notifications) {
        this.notifications = notifications;
        this.validation = validation;
        this.mail = mail;
        this.mongo = mongo;
        this.writer = writer;
    }

    @Scheduled(cron = "${efdl.validation.delay-check-cron:0 0 * * * *}")
    public void scheduledCheck() {
        notifyLate(new Date());
    }

    /** Prévient les validateurs des factures en retard pas encore signalées ; renvoie le nombre de notifications. */
    public int notifyLate(Date now) {
        int sent = 0;
        for (ValidationService.Late late : validation.late(now)) {
            Document p = late.paiement();
            String id = p.getString("externalId");
            int niveau = p.get("niveauValidation") instanceof Number n ? n.intValue() : 0;
            Query already = Query.query(Criteria.where("schoolExternalId").is(late.school()).and("paiementExternalId").is(id)
                    .and("niveau").is(niveau));
            if (mongo.exists(already, COLLECTION)) continue;
            // Trace d'abord : pas de second envoi si la vérification suivante démarre avant la fin de celle-ci.
            writer.insert(COLLECTION, CLASS_NAME, DocumentWriter.newExternalId(), late.school(),
                    DocumentWriter.fields("paiementExternalId", id, "niveau", niveau,
                            "userExternalId", late.step().userExternalId(), "attenteJours", late.delay().jours(),
                            "delaiMax", late.delay().maxJours()), null);
            String url = mail.link("/admin/project/validationPaiement/edit/" + id);
            String montant = NumberFormat.getNumberInstance(Locale.FRANCE).format(p.get("montant") instanceof Number m ? m.doubleValue() : 0);
            String text = "La facture N° " + nz(p.getString("numeroFacture")) + " (" + montant + " MGA) attend votre validation depuis "
                    + late.delay().jours() + " jours ; le délai de traitement de votre étape est de " + late.delay().maxJours() + " jours.";
            // Notification dans l'application et/ou e-mail, selon les préférences du validateur.
            notifications.notify(late.school(), late.step().userExternalId(), NotificationService.Type.VALIDATION_RETARD,
                    "Retard de validation : " + nz(p.getString("numeroFacture")), text,
                    "/admin/project/validationPaiement/edit/" + id,
                    MAIL_SUBJECT, name -> "Bonjour " + name + ",\n\n" + text + "\n" + "Pour la traiter : " + url + "\n");
            sent++;
        }
        if (sent > 0) log.info("Retards de validation : {} validateur(s) prévenu(s)", sent);
        return sent;
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }
}
