package com.ffs.efdl;

import java.lang.reflect.Field;
import java.util.Map;

/** Construction de documents de test (les Models n'ont volontairement pas de setters : lecture seule). */
public final class TestData {

    private TestData() {
    }

    public static <T> T with(T target, Map<String, Object> fields) {
        fields.forEach((name, value) -> set(target, name, value));
        return target;
    }

    private static void set(Object target, String name, Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalArgumentException("Champ inconnu : " + name);
    }
}
