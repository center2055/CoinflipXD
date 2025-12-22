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
        meta.setDisplayName(name == null ? null : LEGACY_SERIALIZER.serialize(name));
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
            meta.setLore(null);
            return;
        }
        List<String> legacyLore = new ArrayList<>(lore.size());
        for (Component line : lore) {
            legacyLore.add(line == null ? "" : LEGACY_SERIALIZER.serialize(line));
        }
        meta.setLore(legacyLore);
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
}
