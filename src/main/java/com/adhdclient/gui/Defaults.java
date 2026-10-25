package com.adhdclient.gui;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Zurücksetzen von Einstellungs-Objekten: alle Felder bekommen die Werte eines frisch erzeugten Objekts derselben
 * Klasse. Das Objekt selbst bleibt dasselbe, damit alle, die es schon in der Hand haben, die neuen Werte sehen.
 */
public final class Defaults {

    private Defaults() {
    }

    public static <T> T reset(T target) {
        try {
            var ctor = target.getClass().getDeclaredConstructor();
            ctor.setAccessible(true);
            Object fresh = ctor.newInstance();
            for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    int m = f.getModifiers();
                    if (Modifier.isStatic(m) || Modifier.isFinal(m) || Modifier.isTransient(m)) continue;
                    f.setAccessible(true);
                    f.set(target, f.get(fresh));
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Kann " + target.getClass().getSimpleName() + " nicht zurücksetzen", e);
        }
        return target;
    }
}
