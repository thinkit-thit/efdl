package com.ffs.efdl.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ffs.efdl.dto.ApiDtos.MenuItemDto;
import com.ffs.efdl.model.Menu;
import com.ffs.efdl.repository.MenuRepository;
import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.PermissionService;

/**
 * Menu de l'utilisateur :
 * menus de l'école triés par {@code order}, 3 niveaux (parentId vide → enfants → petits-enfants),
 * chaque entrée visible seulement si {@code isAuthorizedPath(user, menu.url)}.
 */
@Service
public class MenuService {

    private final MenuRepository menuRepository;
    private final PermissionService permissionService;

    public MenuService(MenuRepository menuRepository, PermissionService permissionService) {
        this.menuRepository = menuRepository;
        this.permissionService = permissionService;
    }

    public List<MenuItemDto> menuFor(EfdlUserPrincipal user, String schoolExternalId) {
        List<Menu> menus = menuRepository.findBySchoolExternalIdOrderByOrderAsc(schoolExternalId).stream()
                .filter(m -> !m.isDeleted()).toList();
        Map<String, List<Menu>> byParent = menus.stream()
                .filter(m -> StringUtils.hasText(m.getParentId()))
                .collect(Collectors.groupingBy(Menu::getParentId));
        return menus.stream()
                .filter(m -> !StringUtils.hasText(m.getParentId()))
                .filter(m -> permissionService.isAuthorizedPath(user, m.getUrl()))
                .map(m -> toDto(m, byParent, user, 1))
                .toList();
    }

    private MenuItemDto toDto(Menu menu, Map<String, List<Menu>> byParent, EfdlUserPrincipal user, int level) {
        List<MenuItemDto> children = level >= 3 ? List.of()
                : byParent.getOrDefault(menu.getExternalId(), List.of()).stream()
                .filter(c -> permissionService.isAuthorizedPath(user, c.getUrl()))
                .map(c -> toDto(c, byParent, user, level + 1))
                .toList();
        return new MenuItemDto(menu.getExternalId(), menu.getTitle(), menu.getIcon(), menu.getUrl(),
                menu.getUrlExterne(), menu.getPath(), children);
    }
}
