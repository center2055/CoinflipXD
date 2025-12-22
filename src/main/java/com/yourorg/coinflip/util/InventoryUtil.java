package com.yourorg.coinflip.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.lang.reflect.Method;

public final class InventoryUtil {

    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
    private static final Method CREATE_INVENTORY_COMPONENT_TITLE = findCreateInventoryComponentTitle();

    private InventoryUtil() {
    }

    public static Inventory createInventory(InventoryHolder holder, int size, Component title) {
        if (CREATE_INVENTORY_COMPONENT_TITLE != null) {
            try {
                return (Inventory) CREATE_INVENTORY_COMPONENT_TITLE.invoke(null, holder, size, title);
            } catch (ReflectiveOperationException ignored) {
                // Fall back to String title
            }
        }
        String legacyTitle = title == null ? "" : LEGACY_SERIALIZER.serialize(title);
        return Bukkit.createInventory(holder, size, legacyTitle);
    }

    private static Method findCreateInventoryComponentTitle() {
        try {
            return Bukkit.class.getMethod("createInventory", InventoryHolder.class, int.class, Component.class);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }
}
