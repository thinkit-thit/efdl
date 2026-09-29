package com.ffs.efdl.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.dto.ApiDtos.SchoolDto;
import com.ffs.efdl.security.TenantResolver;

import jakarta.servlet.http.HttpServletRequest;

/** École (« application ») résolue pour la requête — utilisée par la page de connexion. */
@RestController
@RequestMapping("/api/tenant")
public class TenantController {

    private final TenantResolver tenantResolver;

    public TenantController(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @GetMapping
    public ResponseEntity<SchoolDto> current(HttpServletRequest request) {
        return tenantResolver.resolve(request)
                .map(s -> ResponseEntity.ok(SchoolDto.of(s)))
                .orElse(ResponseEntity.noContent().build());
    }
}
