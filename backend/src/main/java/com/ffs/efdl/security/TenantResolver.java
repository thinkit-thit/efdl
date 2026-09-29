package com.ffs.efdl.security;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.ffs.efdl.config.EfdlProperties;
import com.ffs.efdl.model.School;
import com.ffs.efdl.repository.SchoolRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Résolution de l'« application » (School) :
 * 1. en-tête {@code Host} = {@code School.domainName} ;
 * 2. sinon paramètre {@code schoolId} ;
 * 3. sinon école déjà mémorisée en session ;
 * 4. sinon {@code efdl.tenant.default-school-id} (développement local).
 * L'école retenue est mémorisée en session. Une école supprimée ({@code status=DELETED}) est ignorée.
 */
@Component
public class TenantResolver {

    public static final String SESSION_ATTRIBUTE = "efdl.schoolExternalId";

    private final SchoolRepository schoolRepository;
    private final EfdlProperties properties;

    public TenantResolver(SchoolRepository schoolRepository, EfdlProperties properties) {
        this.schoolRepository = schoolRepository;
        this.properties = properties;
    }

    public Optional<School> resolve(HttpServletRequest request) {
        Optional<School> school = Optional.empty();
        String host = request.getHeader("Host");
        if (StringUtils.hasText(host)) {
            school = schoolRepository.findFirstByDomainName(host).filter(s -> !s.isDeleted());
        }
        if (school.isEmpty() && StringUtils.hasText(request.getParameter("schoolId"))) {
            school = schoolRepository.findFirstByExternalId(request.getParameter("schoolId")).filter(s -> !s.isDeleted());
        }
        HttpSession session = request.getSession(false);
        if (school.isEmpty() && session != null && session.getAttribute(SESSION_ATTRIBUTE) instanceof String id) {
            school = schoolRepository.findFirstByExternalId(id).filter(s -> !s.isDeleted());
        }
        String defaultId = properties.tenant() == null ? null : properties.tenant().defaultSchoolId();
        if (school.isEmpty() && StringUtils.hasText(defaultId)) {
            school = schoolRepository.findFirstByExternalId(defaultId).filter(s -> !s.isDeleted());
        }
        school.ifPresent(s -> request.getSession(true).setAttribute(SESSION_ATTRIBUTE, s.getExternalId()));
        return school;
    }
}
