package io.github.center2055.coinflipxd.util;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

import java.lang.reflect.Method;
import java.util.Locale;

/**
 * Turns config sound names into Adventure keys. {@code org.bukkit.Sound} was an enum before 1.21.3 and is an
 * interface since, so it is only touched through reflection here; the plugin never calls Sound methods directly.
 */
public final class SoundUtil {

    private SoundUtil() {
    }

    /**
     * Accepts Bukkit names (UI_BUTTON_CLICK), keys (minecraft:ui.button.click or ui.button.click), or
     * "none"/"" for silence (returns null). Throws IllegalArgumentException for unknown names.
     */
    public static Key parse(String name) {
        if (name == null || name.isBlank() || name.equalsIgnoreCase("none")) {
            return null;
        }
        String value = name.trim();
        if (value.contains(":") || value.contains(".")) {
            String lower = value.toLowerCase(Locale.ROOT);
            return lower.contains(":") ? Key.key(lower) : Key.key("minecraft", lower);
        }
        Object sound = bukkitSound(value.toUpperCase(Locale.ROOT));
        NamespacedKey key = keyOf(sound);
        return Key.key(key.getNamespace(), key.getKey());
    }

    private static Object bukkitSound(String name) {
        try {
            Method valueOf = Sound.class.getMethod("valueOf", String.class);
            Object sound = valueOf.invoke(null, name);
            if (sound == null) {
                throw new IllegalArgumentException("Unknown sound " + name);
            }
            return sound;
        } catch (java.lang.reflect.InvocationTargetException e) {
            throw new IllegalArgumentException("Unknown sound " + name, e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Can't look up sound " + name, e);
        }
    }

    private static NamespacedKey keyOf(Object sound) {
        for (String method : new String[] {"getKey", "key"}) {
            try {
                Object key = sound.getClass().getMethod(method).invoke(sound);
                if (key instanceof NamespacedKey nk) {
                    return nk;
                }
            } catch (ReflectiveOperationException ignored) {
                // try the next one
            }
        }
        throw new IllegalArgumentException("Can't read the key of sound " + sound);
    }
}
