package com.ffs.efdl.model;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Collection {@code Menu} (nom sensible à la casse).
 * Arbre sur 3 niveaux : {@code parentId} vide = entrée principale ; sinon externalId du parent.
 */
@Document(collection = "Menu")
public class Menu extends BaseEntity {

    private String title;
    private String icon;
    private String parentId;
    private String url;
    private String urlExterne;
    private String path;
    private int order;

    public String getTitle() { return title; }
    public String getIcon() { return icon; }
    public String getParentId() { return parentId; }
    public String getUrl() { return url; }
    public String getUrlExterne() { return urlExterne; }
    public String getPath() { return path; }
    public int getOrder() { return order; }
}
