package com.ffs.efdl.model;

import java.util.Date;

import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;

/**
 * Champs communs aux documents de la base
 * (identifiant, externalId, application, dates, ordre, statut).
 * Les noms de champs sont ceux stockés en base : ne pas les renommer.
 */
public abstract class BaseEntity {

    /** {@code _id} (ObjectId). */
    @Id
    private ObjectId objectId;

    /** Identifiant métier (UUID, index unique). Jamais régénéré pour un document existant. */
    private String externalId;

    /** Tenant : externalId de l'école (« application »). */
    private String schoolExternalId;

    private Date creationDate;
    private Date lastUpdateDate;
    private String lastUpdateUserId;
    private int orderPlace;

    /**
     * Suppression logique : {@code DELETED} = élément supprimé, invisible dans l'application.
     * Champ présent sur toutes les collections gérées.
     */
    private String status;

    public static final String STATUS_DELETED = "DELETED";

    public ObjectId getObjectId() { return objectId; }
    public String getExternalId() { return externalId; }
    public String getSchoolExternalId() { return schoolExternalId; }
    public Date getCreationDate() { return creationDate; }
    public Date getLastUpdateDate() { return lastUpdateDate; }
    public String getLastUpdateUserId() { return lastUpdateUserId; }
    public int getOrderPlace() { return orderPlace; }
    public String getStatus() { return status; }
    public boolean isDeleted() { return STATUS_DELETED.equals(status); }
}
