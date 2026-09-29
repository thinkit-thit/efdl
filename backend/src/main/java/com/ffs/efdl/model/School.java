package com.ffs.efdl.model;

import org.springframework.data.mongodb.core.mapping.Document;

/** Collection {@code school} : « application » / tenant. */
@Document(collection = "school")
public class School extends BaseEntity {

    private String name;
    private String domainName;
    private String address;
    private String description;
    private String years;
    /** « Validateur info » (utilisateur ADMINISTRATION) ; seul, avec l'administrateur, à pouvoir importer. */
    private String validatorId;

    public String getName() { return name; }
    public String getDomainName() { return domainName; }
    public String getAddress() { return address; }
    public String getDescription() { return description; }
    public String getYears() { return years; }
    public String getValidatorId() { return validatorId; }
}
