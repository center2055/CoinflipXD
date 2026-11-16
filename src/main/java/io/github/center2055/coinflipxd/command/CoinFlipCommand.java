package io.github.center2055.coinflipxd.command;

import io.github.center2055.coinflipxd.CoinFlipPlugin;
import io.github.center2055.coinflipxd.config.CoinFlipConfig;
import io.github.center2055.coinflipxd.game.GameService;
import io.github.center2055.coinflipxd.messages.MessageService;
import io.github.center2055.coinflipxd.stats.PlayerStats;
import io.github.center2055.coinflipxd.util.BetUtil;
import io.github.center2055.coinflipxd.util.HelpUtil;
import io.github.center2055.coinflipxd.util.PlayerLookup;
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
import java.util.Locale;
import java.util.OptionalDouble;
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
                plugin.playerSettingsService().reload();
                messages.send(sender, "reloaded");
                return true;
            }
            case "confirmmin" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("Player only command.");
                    return true;
                }
                if (!player.hasPermission("coinflip.use")) {
                    messages.send(player, "no-permission");
                    return true;
                }
                handleConfirmMin(player, Arrays.copyOfRange(args, 1, args.length));
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
                // Games end when their creator quits, so only known (cached) players can have one.
                OfflinePlayer target = PlayerLookup.cached(args[1]);
                if (target == null) {
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
                // Determine if amount input (supports suffixes like 50k)
                if (isAmountInput(args[0])) {
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
        CoinFlipConfig.EconomySettings economy = plugin.config().economy();
        boolean bypass = player.hasPermission("coinflip.bypass.minmax");
        try {
            amount = BetUtil.parseAmount(args[0], economy, bypass);
        } catch (IllegalArgumentException ex) {
            sendInvalidAmount(player);
            return true;
        }

        if (args.length == 1) {
            if (!checkFundsAndLimits(player, amount, economy, bypass)) {
                return true;
            }
            double confirmMin = plugin.playerSettingsService()
                    .getConfirmMin(player.getUniqueId())
                    .orElse(plugin.config().ui().createConfirmMin());
            if (confirmMin > 0 && amount < confirmMin) {
                gameService.createPublicGame(player, amount);
            } else {
                plugin.guiService().openCreateConfirm(player, amount);
            }
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
            if (!checkFundsAndLimits(player, amount, economy, bypass)) {
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
        if (isAmountInput(args[1])) {
            // "/cf <player> <amount>" is the same challenge as "/cf <amount> <player>".
            return handleAmountSubcommand(player, new String[] {args[1], args[0]});
        }
        switch (args[1].toLowerCase(Locale.ROOT)) {
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
        CompletableFuture<OfflinePlayer> lookup;
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Console must specify a player.");
                return;
            }
            lookup = CompletableFuture.completedFuture(player);
        } else {
            String name = args[0];
            OfflinePlayer cached = PlayerLookup.cached(name);
            // An unknown name can mean a web request on Spigot, so look it up off the main thread.
            lookup = cached != null ? CompletableFuture.completedFuture(cached)
                    : CompletableFuture.supplyAsync(() -> PlayerLookup.blocking(name));
        }
        lookup.thenCompose(target -> {
            if (target == null) {
                messages.send(sender, "not-found");
                return CompletableFuture.completedFuture(null);
            }
            UUID playerId = target.getUniqueId();
            String shown = target.getName() != null ? target.getName() : playerId.toString();
            return plugin.statsService().fetchStats(playerId).thenAccept(stats -> sendStats(sender, shown, stats));
        }).exceptionally(e -> {
            plugin.getLogger().warning("Could not load stats: " + e);
            return null;
        });
    }

    private void sendStats(CommandSender sender, String name, PlayerStats stats) {
        Component header = Component.text("CoinflipXD Stats for ", NamedTextColor.GOLD)
                .append(Component.text(name, NamedTextColor.AQUA));
        Component body = Component.join(JoinConfiguration.separator(Component.text(" | ", NamedTextColor.DARK_GRAY)),
                Component.text("Wins: " + stats.wins(), NamedTextColor.GREEN),
                Component.text("Losses: " + stats.losses(), NamedTextColor.RED),
                Component.text("Total Won: " + plugin.economyService().formatNumber(stats.totalWon()), NamedTextColor.GREEN),
                Component.text("Total Lost: " + plugin.economyService().formatNumber(stats.totalLost()), NamedTextColor.RED));
        // Adventure audiences are thread safe; no need to hop to the main thread.
        messages.sender(sender).sendMessage(header);
        messages.sender(sender).sendMessage(body);
    }

    private void handleConfirmMin(Player player, String[] args) {
        if (args.length == 0) {
            double global = plugin.config().ui().createConfirmMin();
            OptionalDouble personal = plugin.playerSettingsService().getConfirmMin(player.getUniqueId());
            double effective = personal.orElse(global);
            String personalText = personal.isPresent()
                    ? plugin.economyService().formatNumber(personal.getAsDouble())
                    : "default";
            messages.send(player, "confirmmin-current",
                    Placeholder.parsed("effective", plugin.economyService().formatNumber(effective)),
                    Placeholder.parsed("personal", personalText),
                    Placeholder.parsed("global", plugin.economyService().formatNumber(global)));
            return;
        }

        String input = args[0];
        if (input.equalsIgnoreCase("default")
                || input.equalsIgnoreCase("clear")
                || input.equalsIgnoreCase("reset")) {
            plugin.playerSettingsService().clearConfirmMin(player.getUniqueId());
            messages.send(player, "confirmmin-cleared");
            return;
        }

        double amount;
        try {
            amount = BetUtil.parseAmountAllowZero(input, plugin.config().economy(), true);
        } catch (IllegalArgumentException ex) {
            messages.send(player, "confirmmin-invalid");
            return;
        }

        plugin.playerSettingsService().setConfirmMin(player.getUniqueId(), amount);
        messages.send(player, "confirmmin-updated",
                Placeholder.parsed("amount", plugin.economyService().formatNumber(amount)));
    }

    private void sendInvalidAmount(CommandSender sender) {
        messages.send(sender, "invalid-amount",
                Placeholder.parsed("min", plugin.economyService().formatNumber(plugin.config().economy().minBet())),
                Placeholder.parsed("max", plugin.economyService().formatNumber(plugin.config().economy().maxBet())));
    }

    private void sendHelp(CommandSender sender) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.text("CoinflipXD Commands:", NamedTextColor.GOLD));
        for (HelpUtil.HelpEntry entry : HelpUtil.entries(hasAdmin(sender))) {
            lines.add(Component.text(entry.command() + " - " + entry.description(), NamedTextColor.YELLOW));
        }
        for (Component line : lines) {
            messages.sender(sender).sendMessage(line);
        }
    }

    private boolean checkFundsAndLimits(Player player, double amount, CoinFlipConfig.EconomySettings economy, boolean bypass) {
        if (!plugin.economyService().has(player, amount)) {
            messages.send(player, "insufficient-funds");
            return false;
        }
        if (bypass) {
            return true;
        }
        double balance = plugin.economyService().balance(player);
        double maxAllowed = BetUtil.maxBalanceBet(balance, economy);
        if (amount <= maxAllowed) {
            return true;
        }
        sendBalanceLimit(player, maxAllowed, economy);
        return false;
    }

    private void sendBalanceLimit(Player player, double maxAllowed, CoinFlipConfig.EconomySettings economy) {
        messages.send(player, "balance-limit",
                Placeholder.parsed("percent", plugin.economyService().formatNumber(economy.maxBalancePercent())),
                Placeholder.parsed("max", plugin.economyService().formatNumber(maxAllowed)));
    }

    private boolean isAmountInput(String input) {
        return BetUtil.isAmountInput(input);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> suggestions = new ArrayList<>();
            suggestions.add("help");
            suggestions.add("stats");
            suggestions.add("cancel");
            suggestions.add("confirmmin");
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
            if ("confirmmin".equalsIgnoreCase(args[0])) {
                return filterSuggestions(Arrays.asList("default", "clear", "reset"), args[1]);
            }
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player != null && player.hasPermission("coinflip.private") && isAmountInput(args[0])) {
                return filterPlayerSuggestions(args[1]);
            }
            if (player != null && !isAmountInput(args[0])) {
                return filterSuggestions(Arrays.asList("accept", "deny"), args[1]);
            }
        }
        if (args.length == 3) {
            Player player = sender instanceof Player ? (Player) sender : null;
            if (player != null && !isAmountInput(args[0])) {
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
