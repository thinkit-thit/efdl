package com.ffs.efdl.service;

import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ffs.efdl.dto.AdminDtos.GroupChoiceDto;
import com.ffs.efdl.dto.AdminDtos.GroupDto;
import com.ffs.efdl.dto.AdminDtos.MenuRowDto;
import com.ffs.efdl.dto.AdminDtos.SchoolRowDto;
import com.ffs.efdl.dto.AdminDtos.UserDetailDto;
import com.ffs.efdl.dto.AdminDtos.ValidatorChoiceDto;
import com.ffs.efdl.dto.AdminDtos.UserRowDto;
import com.ffs.efdl.model.Contract;
import com.ffs.efdl.model.Menu;
import com.ffs.efdl.model.School;
import com.ffs.efdl.model.User;
import com.ffs.efdl.model.UserGroup;
import com.ffs.efdl.model.UserType;
import com.ffs.efdl.repository.ContractRepository;
import com.ffs.efdl.repository.MenuRepository;
import com.ffs.efdl.repository.SchoolRepository;
import com.ffs.efdl.repository.UserGroupRepository;
import com.ffs.efdl.repository.UserRepository;
import com.ffs.efdl.files.UserPhotoService;

/**
 * Lectures du socle administration.
 */
@Service
public class AdminReadService {

    /** Types de contrat « fin » et « autre » (RhService.getContractsLastByType("end"/"other")). */
    static final List<String> EXCLUDING_CONTRACT_TYPES =
            List.of("DEATH", "DISMISSED", "REMOVED", "RESIGNATION", "RETIREMENT", "OTHER");

    private final UserGroupRepository groupRepository;
    private final MenuRepository menuRepository;
    private final SchoolRepository schoolRepository;
    private final UserRepository userRepository;
    private final ContractRepository contractRepository;
    private final UserPhotoService photos;
    private final LoginHistoryService loginHistory;

    public AdminReadService(UserGroupRepository groupRepository, MenuRepository menuRepository,
                            SchoolRepository schoolRepository, UserRepository userRepository,
                            ContractRepository contractRepository, UserPhotoService photos,
                            LoginHistoryService loginHistory) {
        this.loginHistory = loginHistory;
        this.photos = photos;
        this.groupRepository = groupRepository;
        this.menuRepository = menuRepository;
        this.schoolRepository = schoolRepository;
        this.userRepository = userRepository;
        this.contractRepository = contractRepository;
    }

    // ---------- Groupes (admin/user/group) ----------
    public List<GroupDto> groups(String schoolExternalId) {
        return groupRepository.findBySchoolExternalId(schoolExternalId).stream().filter(g -> !g.isDeleted())
                .map(AdminReadService::toDto).toList();
    }

    /** Recherche par externalId sans filtre d'école. */
    public Optional<GroupDto> group(String externalId) {
        return groupRepository.findFirstByExternalId(externalId).filter(g -> !g.isDeleted()).map(AdminReadService::toDto);
    }

    private static GroupDto toDto(UserGroup g) {
        List<String> paths = g.getAllowedPaths() == null ? List.of() : g.getAllowedPaths().stream().sorted().toList();
        return new GroupDto(g.getExternalId(), g.getName(), paths);
    }

    // ---------- Menus (admin/menu) ----------
    public List<MenuRowDto> menus(String schoolExternalId) {
        List<Menu> menus = activeMenus(schoolExternalId);
        Map<String, Menu> byId = menus.stream()
                .filter(m -> m.getExternalId() != null)
                .collect(Collectors.toMap(Menu::getExternalId, Function.identity(), (a, b) -> a));
        return menus.stream().map(m -> toRow(m, byId)).toList();
    }

    public Optional<MenuRowDto> menu(String schoolExternalId, String externalId) {
        return menuRepository.findFirstBySchoolExternalIdAndExternalId(schoolExternalId, externalId)
                .filter(m -> !m.isDeleted())
                .map(m -> toRow(m, activeMenus(schoolExternalId).stream()
                        .filter(x -> x.getExternalId() != null)
                        .collect(Collectors.toMap(Menu::getExternalId, Function.identity(), (a, b) -> a))));
    }

    private List<Menu> activeMenus(String schoolExternalId) {
        return menuRepository.findBySchoolExternalIdOrderByOrderAsc(schoolExternalId).stream().filter(m -> !m.isDeleted()).toList();
    }

    private static MenuRowDto toRow(Menu m, Map<String, Menu> byId) {
        Menu parent = m.getParentId() == null ? null : byId.get(m.getParentId());
        return new MenuRowDto(m.getExternalId(), m.getTitle(), m.getUrl(), m.getUrlExterne(), m.getIcon(), m.getPath(),
                m.getOrder(), m.getParentId(), parent == null ? null : parent.getTitle());
    }

    // ---------- Applications (admin/school), ADMIN uniquement ----------
    public List<SchoolRowDto> schools() {
        return schoolRepository.findAll().stream().filter(s -> !s.isDeleted()).map(AdminReadService::toRow).toList();
    }

    public Optional<SchoolRowDto> school(String externalId) {
        return schoolRepository.findFirstByExternalId(externalId).filter(s -> !s.isDeleted()).map(AdminReadService::toRow);
    }

    /** Choix du « Validateur info » : utilisateurs ADMINISTRATION, « username - prénom nom ». */
    public List<ValidatorChoiceDto> validatorChoices(String schoolExternalId) {
        return userRepository.findBySchoolExternalIdAndTypeOrderByCreationDateAsc(schoolExternalId, UserType.ADMINISTRATION)
                .stream().filter(u -> !u.isDeleted())
                .map(u -> new ValidatorChoiceDto(u.getExternalId(), (nz(u.getUsername()) + " - " + nz(u.getFirstName()) + " "
                        + nz(u.getLastName())).trim()))
                .toList();
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static SchoolRowDto toRow(School s) {
        return new SchoolRowDto(s.getExternalId(), s.getName(), s.getDomainName(), s.getYears(), s.getAddress(), s.getDescription(),
                s.getValidatorId());
    }

    // ---------- Utilisateurs (admin/user/administration) ----------

    /**
     * Équivalent de UserService.getByType(school, ADMINISTRATION) : utilisateurs ADMINISTRATION de l'école,
     * sauf ceux ayant un contrat de fin (décès, licenciement, radiation, démission, retraite) ou « autre » ;
     * tri par date de création.
     */
    public List<UserRowDto> administrationUsers(String schoolExternalId) {
        Set<String> excluded = contractRepository.findUserIdsBySchoolAndTypes(schoolExternalId, EXCLUDING_CONTRACT_TYPES)
                .stream().map(Contract::getUserExternalId).collect(Collectors.toSet());
        List<User> list = userRepository.findBySchoolExternalIdAndTypeOrderByCreationDateAsc(schoolExternalId, UserType.ADMINISTRATION)
                .stream()
                .filter(u -> !u.isDeleted() && !excluded.contains(u.getExternalId()))
                .toList();
        List<String> ids = list.stream().map(User::getExternalId).toList();
        Set<String> withPhoto = photos.withPhoto(ids);
        Map<String, Date> lastLogins = loginHistory.lastLogins(schoolExternalId, ids);
        return list.stream()
                .map(u -> new UserRowDto(u.getExternalId(), u.getDisplayName(), u.getUsername(), u.getPhone(),
                        u.getAddress(), u.getInDate(), u.getGender(), withPhoto.contains(u.getExternalId()),
                        lastLogins.get(u.getExternalId())))
                .toList();
    }

    /** Chargement par externalId, affiché seulement pour un utilisateur ADMINISTRATION. */
    public Optional<UserDetailDto> administrationUser(String externalId, String schoolExternalId) {
        return userRepository.findFirstByExternalId(externalId)
                .filter(u -> u.getType() == UserType.ADMINISTRATION && !u.isDeleted())
                .map(u -> {
                    List<GroupChoiceDto> groups = groupChoices(schoolExternalId,
                            u.getUserGroupIds() == null ? Set.of() : u.getUserGroupIds());
                    return new UserDetailDto(u.getExternalId(), u.getUsername(), u.getLastName(), u.getFirstName(),
                            u.getGender(), u.getPhone(), u.getEmail(), u.getAddress(), u.getBirthDate(), u.getBirthPlace(),
                            u.getCinNumber(), u.getCinDate(), u.getCinPlace(), u.getNumeroCnaps(), u.getInDate(), groups);
                });
    }

    /** Fiche vierge pour la création (edit/new) : seuls les groupes de l'école sont proposés. */
    public UserDetailDto newAdministrationUser(String schoolExternalId) {
        return new UserDetailDto(null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                groupChoices(schoolExternalId, Set.of()));
    }

    private List<GroupChoiceDto> groupChoices(String schoolExternalId, Set<String> memberOf) {
        return groupRepository.findBySchoolExternalId(schoolExternalId).stream()
                .filter(g -> !g.isDeleted())
                .map(g -> new GroupChoiceDto(g.getExternalId(), g.getName(), memberOf.contains(g.getExternalId())))
                .sorted(Comparator.comparing(GroupChoiceDto::name, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
    }
}
