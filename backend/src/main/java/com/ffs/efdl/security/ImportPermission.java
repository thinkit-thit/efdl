package com.ffs.efdl.security;

import org.springframework.stereotype.Service;

import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.SchoolRepository;

/**
 * Imports Excel et historique des modifications réservés à l'administrateur (type ADMIN)
 * et au « Validateur info » de l'application ({@code school.validatorId}), relu dans la base à chaque contrôle.
 */
@Service
public class ImportPermission {

    private final SchoolRepository schoolRepository;

    public ImportPermission(SchoolRepository schoolRepository) {
        this.schoolRepository = schoolRepository;
    }

    public boolean canImport(EfdlUserPrincipal user, String schoolExternalId) {
        return isAdminOrValidatorInfo(user, schoolExternalId);
    }

    /** Historique des modifications : mêmes personnes que les imports. */
    public boolean canViewAudit(EfdlUserPrincipal user, String schoolExternalId) {
        return isAdminOrValidatorInfo(user, schoolExternalId);
    }

    private boolean isAdminOrValidatorInfo(EfdlUserPrincipal user, String schoolExternalId) {
        if (user == null) return false;
        if (user.type() == UserType.ADMIN) return true;
        return schoolExternalId != null && schoolRepository.findFirstByExternalId(schoolExternalId)
                .filter(s -> !s.isDeleted())
                .map(s -> user.externalId().equals(s.getValidatorId()))
                .orElse(false);
    }
}
