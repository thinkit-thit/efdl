package com.ffs.efdl.notification;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.ffs.efdl.security.EfdlUserPrincipal;
import com.ffs.efdl.security.RoutePathInterceptor;

/** Notifications de l'utilisateur connecté (les siennes seulement) et ses préférences. */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    public record UnreadCount(long unread) {
    }

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationService.Page list(@RequestParam(defaultValue = "false") boolean unread,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(me().externalId(), unread, Math.max(page, 0), Math.min(Math.max(size, 1), 100));
    }

    @GetMapping("/unread-count")
    public UnreadCount unreadCount() {
        return new UnreadCount(service.unread(me().externalId()));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable String id) {
        service.markRead(me().externalId(), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll() {
        service.markAllRead(me().externalId());
    }

    @GetMapping("/preferences")
    public List<NotificationService.Preference> preferences() {
        return service.preferences(me().externalId());
    }

    @PutMapping("/preferences")
    public List<NotificationService.Preference> savePreferences(@RequestBody List<NotificationService.Preference> prefs) {
        EfdlUserPrincipal me = me();
        service.savePreferences(me.externalId(), me.schoolExternalId(), prefs);
        return service.preferences(me.externalId());
    }

    private static EfdlUserPrincipal me() {
        return RoutePathInterceptor.currentUser();
    }
}
