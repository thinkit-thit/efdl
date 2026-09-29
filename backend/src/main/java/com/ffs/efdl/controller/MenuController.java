package com.ffs.efdl.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.dto.ApiDtos.MenuItemDto;
import com.ffs.efdl.model.School;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePathInterceptor;
import com.ffs.efdl.security.TenantResolver;
import com.ffs.efdl.service.MenuService;

import jakarta.servlet.http.HttpServletRequest;

/** Menu de gauche, filtré par les droits de l'utilisateur. */
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;
    private final TenantResolver tenantResolver;

    public MenuController(MenuService menuService, TenantResolver tenantResolver) {
        this.menuService = menuService;
        this.tenantResolver = tenantResolver;
    }

    @GetMapping("/me")
    public List<MenuItemDto> myMenu(HttpServletRequest request) {
        EfdlUserPrincipal user = RoutePathInterceptor.currentUser();
        String schoolId = tenantResolver.resolve(request).map(School::getExternalId).orElse(user.schoolExternalId());
        return menuService.menuFor(user, schoolId);
    }
}
