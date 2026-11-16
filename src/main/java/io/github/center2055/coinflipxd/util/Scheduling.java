package io.github.center2055.coinflipxd.util;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Runs tasks on the right thread on Bukkit/Spigot/Paper and on Folia. Folia-only API is kept in {@link FoliaSupport},
 * which is only loaded on Folia, so Spigot never touches classes it doesn't have.
 */
public final class Scheduling {

    public static final boolean FOLIA = classExists("io.papermc.paper.threadedregions.RegionizedServer");

    private Scheduling() {
    }

    /** A repeating task that can be cancelled. */
    public interface Task {
        void cancel();
    }

    /** Repeats a task that touches no specific world or player (expiry checks, etc.). */
    public static Task repeatGlobal(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        if (FOLIA) {
            return FoliaSupport.repeatGlobal(plugin, task, delayTicks, periodTicks);
        }
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, task, delayTicks, periodTicks);
        return t::cancel;
    }

    /** Runs a task for a player on the thread that owns them (the next tick, so it's safe from inside events). */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            FoliaSupport.runForEntity(plugin, player, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs a task on the next server tick (global region on Folia), even when called from the main thread. */
    public static void nextTick(Plugin plugin, Runnable task) {
        if (FOLIA) {
            FoliaSupport.runGlobal(plugin, task);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs a task on the main thread (global region on Folia). Runs immediately if already there. */
    public static void runGlobal(Plugin plugin, Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        if (FOLIA) {
            FoliaSupport.runGlobal(plugin, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /** Folia's region schedulers. Only referenced when {@link #FOLIA} is true. */
    private static final class FoliaSupport {

        static Task repeatGlobal(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
            io.papermc.paper.threadedregions.scheduler.ScheduledTask t = Bukkit.getGlobalRegionScheduler()
                    .runAtFixedRate(plugin, st -> task.run(), Math.max(1, delayTicks), Math.max(1, periodTicks));
            return t::cancel;
        }

        static void runForEntity(Plugin plugin, Player player, Runnable task) {
            player.getScheduler().run(plugin, st -> task.run(), null);
        }

        static void runGlobal(Plugin plugin, Runnable task) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, task);
        }
    }
}
