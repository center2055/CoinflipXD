package com.yourorg.coinflip.command;

import com.yourorg.coinflip.CoinFlipPlugin;
import com.yourorg.coinflip.game.CoinFlipGame;
import com.yourorg.coinflip.game.GameService;
import com.yourorg.coinflip.messages.MessageService;
import com.yourorg.coinflip.stats.PlayerStats;
import com.yourorg.coinflip.util.BetUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class CoinFlipCommand implements CommandExecutor, TabCompleter {

    private final CoinFlipPlugin plugin;
    private final GameService gameService;
    private final MessageService messages;

    public CoinFlipCommand(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        this.gameService = plugin.gameService();
        this.messages = plugin.messageService();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("This command is player only.");
                return true;
            }
            if (!player.hasPermission("coinflip.use")) {
                messages.send(player, "no-permission");
                return true;
            }
            plugin.guiService().openBrowser(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help" -> {
                sendHelp(sender);
                return true;
            }
            case "reload" -> {
                if (!hasAdmin(sender)) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                plugin.setConfig(plugin.configService().reload());
                plugin.messageService().reload();
                messages.send(sender, "reloaded");
                return true;
            }
            case "cancel" -> {
                if (args.length == 1) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Player only command.");
                        return true;
                    }
                    if (!player.hasPermission("coinflip.use")) {
                        messages.send(player, "no-permission");
                        return true;
                    }
                    gameService.cancelOwn(player);
                    return true;
                }
                if (!hasAdmin(sender)) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                String targetName = args[1];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);
                if (target == null || target.getUniqueId() == null) {
                    messages.send(sender, "not-found");
                    return true;
                }
                gameService.findByCreator(target.getUniqueId()).ifPresentOrElse(
                        game -> {
                            gameService.forceCancel(game);
                            messages.send(sender, "canceled",
                                    Placeholder.parsed("amount", plugin.economyService().formatNumber(game.amount())));
                        },
                        () -> messages.send(sender, "not-found")
                );
                return true;
            }
            case "stats" -> {
                if (!sender.hasPermission("coinflip.use")) {
                    messages.send(sender, "no-permission");
                    return true;
                }
                handleStats(sender, Arrays.copyOfRange(args, 1, args.length));
                return true;
            }
            default -> {
                // Determine if numeric amount
                if (isNumeric(args[0])) {
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage("Player only command.");
                        return true;
                    }
                    if (!player.hasPermission("coinflip.use")) {
                        messages.send(player, "no-permission");
                        return true;
                    }
                    return handleAmountSubcommand(player, args);
                }

                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only command.");
                    return true;
                }
                if (!player.hasPermission("coinflip.use")) {
                    messages.send(player, "no-permission");
                    return true;
                }
                if (args.length < 2) {
                    messages.send(player, "not-found");
                    return true;
                }
                return handlePlayerDirective(player, args);
            }
        }
    }

    private boolean handleAmountSubcommand(Player player, String[] args) {
        double amount;
        try {
            amount = BetUtil.parseAmount(args[0], plugin.config().economy(), player.hasPermission("coinflip.bypass.minmax"));
        } catch (IllegalArgumentException ex) {
            sendInvalidAmount(player);
            return true;
        }

        if (args.length == 1) {
            if (!plugin.economyService().hasBalance(player, amount)) {
                messages.send(player, "insufficient-funds");
                return true;
            }
            plugin.guiService().openCreateConfirm(player, amount);
            return true;
        }

        if (args.length >= 2) {
            if (!player.hasPermission("coinflip.private")) {
                messages.send(player, "no-permission");
                return true;
            }
            String targetName = args[1];
            Player target = Bukkit.getPlayerExact(targetName);
            if (target == null) {
                messages.send(player, "not-found");
                return true;
            }
            if (target.getUniqueId().equals(player.getUniqueId())) {
                messages.send(player, "self-accept");
                return true;
            }
            if (!plugin.economyService().hasBalance(player, amount)) {
                messages.send(player, "insufficient-funds");
                return true;
            }
            gameService.createPrivateGame(player, target, amount);
            return true;
        }
        return true;
    }

    private boolean handlePlayerDirective(Player player, String[] args) {
        String targetName = args[0];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            messages.send(player, "not-found");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "self-accept");
            return true;
        }
        if (args.length < 2) {
            messages.send(player, "not-found");
            return true;
        }
        switch (args[1].toLowerCase()) {
            case "accept" -> {
                gameService.acceptPrivate(player, target);
                return true;
            }
            case "deny" -> {
                gameService.denyPrivate(player, target);
                return true;
            }
            default -> {
                messages.send(player, "not-found");
                return true;
            }
        }
    }

    private void handleStats(CommandSender sender, String[] args) {
        OfflinePlayer target;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must specify a player.");
                return;
            }
            target = player;
        } else {
            target = Bukkit.getOfflinePlayer(args[0]);
        }

        if (target == null || target.getUniqueId() == null) {
            messages.send(sender, "not-found");
            return;
        }
        UUID playerId = target.getUniqueId();
        CompletableFuture<PlayerStats> future = plugin.statsService().fetchStats(playerId);
        future.thenAccept(stats -> {
            Component header = Component.text("CoinflipXD Stats for ", NamedTextColor.GOLD)
                    .append(Component.text(target.getName() != null ? target.getName() : playerId.toString(), NamedTextColor.AQUA));
            Component body = Component.join(JoinConfiguration.separator(Component.text(" | ", NamedTextColor.DARK_GRAY)),
                    Component.text("Wins: " + stats.wins(), NamedTextColor.GREEN),
                    Component.text("Losses: " + stats.losses(), NamedTextColor.RED),
                    Component.text("Total Won: " + plugin.economyService().formatNumber(stats.totalWon()), NamedTextColor.GREEN),
                    Component.text("Total Lost: " + plugin.economyService().formatNumber(stats.totalLost()), NamedTextColor.RED));
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                messages.sender(sender).sendMessage(header);
                messages.sender(sender).sendMessage(body);
            });
        });
    }

    private void sendInvalidAmount(CommandSender sender) {
        messages.send(sender, "invalid-amount",
                Placeholder.parsed("min", plugin.economyService().formatNumber(plugin.config().economy().minBet())),
                Placeholder.parsed("max", plugin.economyService().formatNumber(plugin.config().economy().maxBet())));
    }

    private void sendHelp(CommandSender sender) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("CoinflipXD Commands:", NamedTextColor.GOLD));
        lines.add(Component.text("/cf - open browser", NamedTextColor.YELLOW));
        lines.add(Component.text("/cf <amount> - create public coinflip", NamedTextColor.YELLOW));
        lines.add(Component.text("/cf <amount> <player> - challenge player", NamedTextColor.YELLOW));
        lines.add(Component.text("/cf <player> accept|deny - respond to private challenge", NamedTextColor.YELLOW));
        lines.add(Component.text("/cf cancel - cancel your coinflip", NamedTextColor.YELLOW));
        lines.add(Component.text("/cf stats [player] - view stats", NamedTextColor.YELLOW));
        if (hasAdmin(sender)) {
            lines.add(Component.text("/cf reload - reload configuration", NamedTextColor.YELLOW));
            lines.add(Component.text("/cf cancel <player> - force cancel coinflip", NamedTextColor.YELLOW));
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (Component line : lines) {
                messages.sender(sender).sendMessage(line);
            }
        });
    }

    private boolean isNumeric(String input) {
        try {
            Double.parseDouble(input);
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("help");
            suggestions.add("stats");
            suggestions.add("cancel");
            if (hasAdmin(sender)) {
                suggestions.add("reload");
            }
            if (sender instanceof Player && sender.hasPermission("coinflip.private")) {
                Bukkit.getOnlinePlayers().stream()
                        .filter(p -> p != sender)
                        .forEach(p -> suggestions.add(p.getName()));
            }
            return filterSuggestions(suggestions, args[0]);
        }
        if (args.length == 2) {
            if ("cancel".equalsIgnoreCase(args[0]) && hasAdmin(sender)) {
                return filterPlayerSuggestions(args[1]);
            }
            if ("stats".equalsIgnoreCase(args[0])) {
                return filterPlayerSuggestions(args[1]);
            }
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player != null && player.hasPermission("coinflip.private") && isNumeric(args[0])) {
                return filterPlayerSuggestions(args[1]);
            }
            if (player != null && !isNumeric(args[0])) {
                return filterSuggestions(Arrays.asList("accept", "deny"), args[1]);
            }
        }
        if (args.length == 3) {
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player != null && !isNumeric(args[0])) {
                return filterSuggestions(Arrays.asList("accept", "deny"), args[2]);
            }
        }
        return Collections.emptyList();
    }

    private List<String> filterSuggestions(List<String> suggestions, String current) {
        if (current == null || current.isEmpty()) {
            return suggestions;
        }
        String lower = current.toLowerCase();
        List<String> filtered = new ArrayList<>();
        for (String suggestion : suggestions) {
            if (suggestion != null && suggestion.toLowerCase().startsWith(lower)) {
                filtered.add(suggestion);
            }
        }
        return filtered;
    }

    private List<String> filterPlayerSuggestions(String current) {
        List<String> suggestions = new ArrayList<>();
        Bukkit.getOnlinePlayers().forEach(p -> suggestions.add(p.getName()));
        return filterSuggestions(suggestions, current);
    }

    private boolean hasAdmin(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return true;
        }
        return player.isOp() || player.hasPermission("coinflip.admin");
    }
}

