package com.ffs.efdl.model;

import java.util.Set;

import org.springframework.data.mongodb.core.mapping.Document;

/** Collection {@code user_group} : groupes d'accès, {@code allowedPaths} = expressions régulières sur les routes. */
@Document(collection = "user_group")
public class UserGroup extends BaseEntity {

    private String name;
    private Set<String> allowedPaths;

    public String getName() { return name; }
    public Set<String> getAllowedPaths() { return allowedPaths; }
}
