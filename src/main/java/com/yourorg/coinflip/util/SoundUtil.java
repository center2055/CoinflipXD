package com.yourorg.coinflip.util;

import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;

import java.lang.reflect.Method;

public final class SoundUtil {

    private static final Method SOUND_KEY_METHOD = findSoundKeyMethod();

    private SoundUtil() {
    }

    public static Key adventureKey(Sound sound) {
        NamespacedKey key = resolveKey(sound);
        return Key.key(key.getNamespace(), key.getKey());
    }

    private static NamespacedKey resolveKey(Sound sound) {
        if (sound == null) {
            throw new IllegalArgumentException("sound");
        }
        if (SOUND_KEY_METHOD == null) {
            throw new IllegalStateException("No sound key method available for " + sound.name());
        }
        try {
            return (NamespacedKey) SOUND_KEY_METHOD.invoke(sound);
        } catch (ReflectiveOperationException ex) {
            throw new IllegalStateException("Failed to resolve sound key for " + sound.name(), ex);
        }
    }

    private static Method findSoundKeyMethod() {
        try {
            return Sound.class.getMethod("key");
        } catch (NoSuchMethodException ignored) {
            // Fall back to legacy getKey()
        }
        try {
            return Sound.class.getMethod("getKey");
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
