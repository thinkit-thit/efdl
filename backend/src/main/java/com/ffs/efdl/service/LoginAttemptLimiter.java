package com.ffs.efdl.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limitation des tentatives de connexion :
 * après {@value #MAX_FAILURES} échecs pour un même identifiant + adresse, blocage de 15 minutes.
 */
@Component
public class LoginAttemptLimiter {

    static final int MAX_FAILURES = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);

    private record Attempts(int failures, Instant lastFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String key) {
        Attempts a = attempts.get(key);
        if (a == null) {
            return false;
        }
        if (a.lastFailure().plus(LOCK_DURATION).isBefore(Instant.now())) {
            attempts.remove(key);
            return false;
        }
        return a.failures() >= MAX_FAILURES;
    }

    public void recordFailure(String key) {
        attempts.merge(key, new Attempts(1, Instant.now()),
                (old, n) -> new Attempts(old.failures() + 1, Instant.now()));
    }

    public void reset(String key) {
        attempts.remove(key);
    }
}
