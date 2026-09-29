package com.ffs.efdl.model;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Collection {@code Contract} (RH). Lue uniquement pour reproduire l'exclusion
 * des utilisateurs dans la liste « administration » (UserService.getByType).
 */
@Document(collection = "Contract")
public class Contract extends BaseEntity {

    private String userExternalId;
    private String contractType;

    public String getUserExternalId() { return userExternalId; }
    public String getContractType() { return contractType; }
}
