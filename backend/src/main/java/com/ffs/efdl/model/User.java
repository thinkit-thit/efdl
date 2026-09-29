package com.ffs.efdl.model;

import java.util.Date;
import java.util.Set;

import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Collection {@code user}.
 * Sous-ensemble des champs utiles à l'authentification et au layout ; les autres champs du document
 * sont ignorés en lecture et ne sont jamais réécrits (lecture seule).
 * <p>
 * {@code password} : champ historique en clair, non utilisé pour l'authentification ; jamais exposé par l'API
 * ni journalisée.
 */
@Document(collection = "user")
public class User extends BaseEntity {

    private String username;
    private String password;
    private String firstName;
    private String lastName;
    private String email;
    private String phone;
    private UserType type;
    private String nationality;
    private String language;
    private Set<String> userGroupIds;
    // Fiche « administration »
    private String gender;
    private String address;
    private Date birthDate;
    private String birthPlace;
    private String cinNumber;
    private Date cinDate;
    private String cinPlace;
    private String numeroCnaps;
    private Date inDate;
    private String jointId;

    public String getGender() { return gender; }
    public String getAddress() { return address; }
    public Date getBirthDate() { return birthDate; }
    public String getBirthPlace() { return birthPlace; }
    public String getCinNumber() { return cinNumber; }
    public Date getCinDate() { return cinDate; }
    public String getCinPlace() { return cinPlace; }
    public String getNumeroCnaps() { return numeroCnaps; }
    public Date getInDate() { return inDate; }
    public String getJointId() { return jointId; }

    public String getUsername() { return username; }
    public String getStoredPassword() { return password; }
    public String getFirstName() { return firstName; }
    public String getLastName() { return lastName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public UserType getType() { return type; }
    public String getNationality() { return nationality; }
    public String getLanguage() { return language; }
    public Set<String> getUserGroupIds() { return userGroupIds; }

    /** Nom affiché : nom puis prénom, apostrophes remplacées par des espaces. */
    public String getDisplayName() {
        String displayName = "";
        if (lastName != null) {
            displayName = lastName.replace("'", " ");
        }
        if (firstName != null) {
            displayName += " " + firstName.replace("'", " ");
        }
        return displayName;
    }
}
