package io.github.center2055.coinflipxd.util;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/** Finding players by name without stalling the server. */
public final class PlayerLookup {

    private static final Method IF_CACHED = findIfCached();

    private PlayerLookup() {
    }

    /**
     * An online player with that exact name, or (on Paper) a known offline player from the server's cache.
     * Never makes a web request. Null if unknown.
     */
    public static OfflinePlayer cached(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            return online;
        }
        if (IF_CACHED != null) {
            try {
                return (OfflinePlayer) IF_CACHED.invoke(Bukkit.getServer(), name);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // fall through
            }
        }
        return null;
    }

    /** Like {@link #cached}, but falls back to Bukkit's full lookup, which can block. Call it off the main thread. */
    public static OfflinePlayer blocking(String name) {
        OfflinePlayer cached = cached(name);
        if (cached != null || IF_CACHED != null) {
            return cached; // Paper's cache already covers everyone who has joined
        }
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);
        return player.hasPlayedBefore() || player.isOnline() ? player : null;
    }

    private static Method findIfCached() {
        try {
            return Server.class.getMethod("getOfflinePlayerIfCached", String.class);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
