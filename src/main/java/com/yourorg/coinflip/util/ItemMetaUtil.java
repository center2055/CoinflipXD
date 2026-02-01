package com.yourorg.coinflip.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ItemMetaUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Method DISPLAY_NAME_COMPONENT = findDisplayNameComponent();
    private static final Method LORE_COMPONENT = findLoreComponent();
    private static final Method SET_DISPLAY_NAME = findSetDisplayName();
    private static final Method SET_LORE = findSetLore();

    private ItemMetaUtil() {
    }

    public static void displayName(ItemMeta meta, Component name) {
        if (meta == null) {
            return;
        }
        if (DISPLAY_NAME_COMPONENT != null) {
            try {
                DISPLAY_NAME_COMPONENT.invoke(meta, name);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to legacy String API
            }
        }
        String legacy = name == null ? null : LEGACY_SERIALIZER.serialize(name);
        if (SET_DISPLAY_NAME != null) {
            try {
                SET_DISPLAY_NAME.invoke(meta, legacy);
            } catch (ReflectiveOperationException ignored) {
                // No further fallback available
            }
        }
    }

    public static void lore(ItemMeta meta, List<Component> lore) {
        if (meta == null) {
            return;
        }
        if (LORE_COMPONENT != null) {
            try {
                LORE_COMPONENT.invoke(meta, lore);
                return;
            } catch (ReflectiveOperationException ignored) {
                // Fall back to legacy String API
            }
        }
        if (lore == null) {
            if (SET_LORE != null) {
                try {
                    SET_LORE.invoke(meta, (Object) null);
                } catch (ReflectiveOperationException ignored) {
                    // No further fallback available
                }
            }
            return;
        }
        List<String> legacyLore = new ArrayList<>(lore.size());
        for (Component line : lore) {
            legacyLore.add(line == null ? "" : LEGACY_SERIALIZER.serialize(line));
        }
        if (SET_LORE != null) {
            try {
                SET_LORE.invoke(meta, legacyLore);
            } catch (ReflectiveOperationException ignored) {
                // No further fallback available
            }
        }
    }

    private static Method findDisplayNameComponent() {
        try {
            return ItemMeta.class.getMethod("displayName", Component.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findLoreComponent() {
        try {
            return ItemMeta.class.getMethod("lore", List.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findSetDisplayName() {
        try {
            return ItemMeta.class.getMethod("setDisplayName", String.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static Method findSetLore() {
        try {
            return ItemMeta.class.getMethod("setLore", List.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
