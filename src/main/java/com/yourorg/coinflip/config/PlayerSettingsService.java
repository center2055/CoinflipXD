package com.yourorg.coinflip.config;

import com.yourorg.coinflip.CoinFlipPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class PlayerSettingsService {

    private static final String ROOT_KEY = "confirm-min";

    private final CoinFlipPlugin plugin;
    private final File file;
    private final Map<UUID, Double> confirmMinByPlayer = new ConcurrentHashMap<>();

    public PlayerSettingsService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_settings.yml");
        load();
    }

    public void load() {
        confirmMinByPlayer.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection(ROOT_KEY);
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            try {
                UUID playerId = UUID.fromString(key);
                double value = section.getDouble(key);
                confirmMinByPlayer.put(playerId, value);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid UUID in player settings: " + key);
            }
        }
    }

    public void reload() {
        load();
    }

    public OptionalDouble getConfirmMin(UUID playerId) {
        Double value = confirmMinByPlayer.get(playerId);
        if (value == null) {
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(value);
    }

    public void setConfirmMin(UUID playerId, double amount) {
        confirmMinByPlayer.put(playerId, amount);
        save();
    }

    public void clearConfirmMin(UUID playerId) {
        if (confirmMinByPlayer.remove(playerId) != null) {
            save();
        }
    }

    private void save() {
        YamlConfiguration config = new YamlConfiguration();
        ConfigurationSection section = config.createSection(ROOT_KEY);
        for (Map.Entry<UUID, Double> entry : confirmMinByPlayer.entrySet()) {
            section.set(entry.getKey().toString(), entry.getValue());
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Failed to save player settings: " + ex.getMessage());
        }
    }
}
