package com.yourorg.coinflip.messages;

import com.yourorg.coinflip.CoinFlipPlugin;
import com.yourorg.coinflip.util.PlaceholderUtil;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class MessageService {

    private final CoinFlipPlugin plugin;
    private FileConfiguration messages;
    private MiniMessage miniMessage;

    public MessageService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        this.messages = YamlConfiguration.loadConfiguration(file);
        mergeMissingMessageKeys(file);
        this.miniMessage = MiniMessage.miniMessage();
    }

    private void mergeMissingMessageKeys(File file) {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(plugin.getResource("messages.yml"), "Missing bundled messages.yml"),
                StandardCharsets.UTF_8
        )) {
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(reader);
            boolean changed = mergeMissingKeys(messages, defaults);
            if (changed) {
                messages.save(file);
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to merge default message keys: " + ex.getMessage());
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
                ConfigurationSection targetSection = target.getConfigurationSection(key);
                if (targetSection == null) {
                    targetSection = target.createSection(key);
                    changed = true;
                }
                changed |= mergeMissingKeys(targetSection, defSection);
                continue;
            }

            if (!target.contains(key)) {
                target.set(key, defaults.get(key));
                changed = true;
            }
        }
        return changed;
    }

    public Component component(String key, TagResolver... placeholders) {
        String raw = messages.getString(key);
        if (raw == null) {
            return Component.text("Missing message: " + key);
        }
        return parse(raw, placeholders);
    }

    public Component parse(String raw, TagResolver... placeholders) {
        if (raw == null) {
            return Component.empty();
        }
        if (plugin.config().miniMessage()) {
            TagResolver resolver = PlaceholderUtil.merge(placeholders);
            return miniMessage.deserialize(raw, resolver);
        }
        return Component.text(raw);
    }

    public Component prefixed(String key, TagResolver... placeholders) {
        Component prefix = component("prefix");
        Component message = component(key, placeholders);
        return prefix.append(message);
    }

    public void send(Player player, String key, TagResolver... placeholders) {
        Audience audience = plugin.audiences().player(player);
        audience.sendMessage(prefixed(key, placeholders));
    }

    public void send(CommandSender sender, String key, TagResolver... placeholders) {
        Audience audience = plugin.audiences().sender(sender);
        audience.sendMessage(prefixed(key, placeholders));
    }

    public void sendRaw(Player player, String key, TagResolver... placeholders) {
        Audience audience = plugin.audiences().player(player);
        audience.sendMessage(component(key, placeholders));
    }

    public void sendRaw(CommandSender sender, String key, TagResolver... placeholders) {
        Audience audience = plugin.audiences().sender(sender);
        audience.sendMessage(component(key, placeholders));
    }

    public void broadcast(String key, TagResolver... placeholders) {
        Component message = prefixed(key, placeholders);
        Bukkit.getOnlinePlayers().forEach(player -> plugin.audiences().player(player).sendMessage(message));
    }

    public void broadcast(Component message) {
        Bukkit.getOnlinePlayers().forEach(player -> plugin.audiences().player(player).sendMessage(message));
    }

    public void notifyStaff(String key, TagResolver... placeholders) {
        Component message = prefixed(key, placeholders);
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("coinflip.notify"))
                .forEach(player -> plugin.audiences().player(player).sendMessage(message));
    }

    public void notifyStaffRaw(String key, TagResolver... placeholders) {
        Component message = component(key, placeholders);
        Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.hasPermission("coinflip.notify"))
                .forEach(player -> plugin.audiences().player(player).sendMessage(message));
    }

    public void reload() {
        load();
    }

    public String raw(String key) {
        return Objects.requireNonNull(messages.getString(key), "Missing message key: " + key);
    }

    public Audience player(Player player) {
        return plugin.audiences().player(player);
    }

    public Audience sender(CommandSender sender) {
        return plugin.audiences().sender(sender);
    }
}

