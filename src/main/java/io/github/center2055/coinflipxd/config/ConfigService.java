package io.github.center2055.coinflipxd.config;

import io.github.center2055.coinflipxd.CoinFlipPlugin;
import io.github.center2055.coinflipxd.util.PlayerLookup;
import io.github.center2055.coinflipxd.util.SoundUtil;
import net.kyori.adventure.key.Key;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class ConfigService {

    private final CoinFlipPlugin plugin;

    public ConfigService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
    }

    public CoinFlipConfig load() {
        plugin.reloadConfig();
        mergeMissingConfigKeys();
        FileConfiguration cfg = plugin.getConfig();

        CoinFlipConfig.EconomySettings economy = new CoinFlipConfig.EconomySettings(
                cfg.getDouble("economy.min-bet", 100D),
                cfg.getDouble("economy.max-bet", 100000D),
                clampPercent(cfg.getDouble("economy.max-balance-percent", 100.0D)),
                cfg.getBoolean("economy.require-whole-numbers", true)
        );

        String recipient = cfg.getString("tax.recipient", "server");
        CoinFlipConfig.TaxSettings tax = new CoinFlipConfig.TaxSettings(
                cfg.getBoolean("tax.enabled", true),
                cfg.getDouble("tax.percent", 10.0D),
                recipient,
                resolveTaxRecipient(recipient)
        );

        CoinFlipConfig.BroadcastSettings broadcast = new CoinFlipConfig.BroadcastSettings(
                cfg.getBoolean("broadcast.enabled", false),
                cfg.getString("broadcast.message", "<gray>%player% created a coinflip for <aqua>$%amount%</aqua>.</gray>")
        );

        CoinFlipConfig.BrowserSettings browser = new CoinFlipConfig.BrowserSettings(
                clampRows(cfg.getInt("ui.browser.rows", 6)),
                cfg.getInt("ui.browser.items-per-page", 45)
        );

        CoinFlipConfig.UiSounds sounds = new CoinFlipConfig.UiSounds(
                parseSound(cfg.getString("ui.sounds.open", "UI_BUTTON_CLICK"), "minecraft:ui.button.click"),
                parseSound(cfg.getString("ui.sounds.accept", "ENTITY_EXPERIENCE_ORB_PICKUP"), "minecraft:entity.experience_orb.pickup"),
                parseSound(cfg.getString("ui.sounds.win", "UI_TOAST_CHALLENGE_COMPLETE"), "minecraft:ui.toast.challenge_complete"),
                parseSound(cfg.getString("ui.sounds.lose", "BLOCK_ANVIL_LAND"), "minecraft:block.anvil.land")
        );

        CoinFlipConfig.UiSettings ui = new CoinFlipConfig.UiSettings(
                browser,
                Math.max(5, cfg.getInt("ui.expire-seconds", 120)),
                Math.max(5, cfg.getInt("ui.private-expire-seconds", 60)),
                Math.max(0D, cfg.getDouble("ui.create-confirm-min", 0D)),
                sounds
        );

        CoinFlipConfig.LimitSettings limits = new CoinFlipConfig.LimitSettings(
                cfg.getBoolean("limits.one-active-per-player", true)
        );

        boolean miniMessage = "MINI_MESSAGE".equalsIgnoreCase(cfg.getString("messages-format", "MINI_MESSAGE"));

        CoinFlipConfig configuration = new CoinFlipConfig(economy, tax, ui, limits, broadcast, miniMessage);
        plugin.setConfig(configuration);
        return configuration;
    }

    public CoinFlipConfig reload() {
        return load();
    }

    private void mergeMissingConfigKeys() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("config.yml"), "Missing bundled config.yml"),
                StandardCharsets.UTF_8
        )) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            FileConfiguration current = plugin.getConfig();
            boolean changed = mergeMissingKeys(current, defaults);
            if (changed) {
                plugin.saveConfig();
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to merge default config keys: " + ex.getMessage());
        }
    }

    private boolean mergeMissingKeys(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                ConfigurationSection defSection = defaults.getConfigurationSection(key);
                if (defSection == null) {
                    continue;
                }
                boolean hasKey = target.contains(key, true);
                ConfigurationSection targetSection = hasKey ? target.getConfigurationSection(key) : null;
                if (targetSection == null) {
                    if (hasKey) {
                        plugin.getLogger().warning("Config key '" + key + "' is not a section; leaving existing value.");
                        continue;
                    }
                    targetSection = target.createSection(key);
                    changed = true;
                }
                changed |= mergeMissingKeys(targetSection, defSection);
                continue;
            }

            if (!target.contains(key, true)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    private int clampRows(int rows) {
        return Math.max(1, Math.min(6, rows));
    }

    private double clampPercent(double percent) {
        if (percent < 0.0D) {
            return 0.0D;
        }
        if (percent > 100.0D) {
            return 100.0D;
        }
        return percent;
    }

    private Key parseSound(String name, String fallback) {
        try {
            return SoundUtil.parse(name);
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Unknown sound '" + name + "', using " + fallback);
            return Key.key(fallback);
        }
    }

    /**
     * The player who receives taxed money, as a UUID. Accepts a UUID or a player name; names are looked up once
     * here instead of on every payout (looking up an unknown name can mean a blocking web request).
     */
    private UUID resolveTaxRecipient(String recipient) {
        if (recipient == null || recipient.isBlank() || "server".equalsIgnoreCase(recipient.trim())) {
            return null;
        }
        String value = recipient.trim();
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException notAUuid) {
            // a player name
        }
        OfflinePlayer cached = PlayerLookup.cached(value);
        OfflinePlayer player = cached != null ? cached : Bukkit.getOfflinePlayer(value);
        if (player == null || !player.hasPlayedBefore() && cached == null) {
            plugin.getLogger().warning("tax.recipient '" + value + "' has never joined this server. Taxed money goes "
                    + "to their account anyway; double-check the name or use their UUID.");
        }
        return player == null ? null : player.getUniqueId();
    }
}

