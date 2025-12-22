package com.yourorg.coinflip.game;

import com.yourorg.coinflip.CoinFlipPlugin;
import com.yourorg.coinflip.config.CoinFlipConfig;
import com.yourorg.coinflip.economy.EconomyService;
import com.yourorg.coinflip.messages.MessageService;
import com.yourorg.coinflip.stats.StatsService;
import com.yourorg.coinflip.util.BetUtil;
import com.yourorg.coinflip.util.PayoutCalculator;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class GameService implements Listener {

    private final CoinFlipPlugin plugin;
    private final EconomyService economy;
    private final MessageService messages;
    private final StatsService stats;

    private final Map<UUID, CoinFlipGame> gamesById = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> activeByCreator = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> privateByTarget = new ConcurrentHashMap<>();
    private final Set<UUID> resolvingPlayers = ConcurrentHashMap.newKeySet();

    private BukkitTask expiryTask;

    public GameService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        this.economy = plugin.economyService();
        this.messages = plugin.messageService();
        this.stats = plugin.statsService();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.expiryTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickExpiry, 20L, 20L);
    }

    public void shutdown() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        new ArrayList<>(gamesById.values()).forEach(game -> cancelGame(game, CancelReason.SHUTDOWN));
        gamesById.clear();
        activeByCreator.clear();
        privateByTarget.clear();
        resolvingPlayers.clear();
    }

    public Optional<CoinFlipGame> findById(UUID id) {
        return Optional.ofNullable(gamesById.get(id));
    }

    public Optional<CoinFlipGame> findByCreator(UUID creator) {
        UUID id = activeByCreator.get(creator);
        if (id == null) {
            return Optional.empty();
        }
        return findById(id);
    }

    public Optional<CoinFlipGame> findPrivateByTarget(UUID target) {
        UUID id = privateByTarget.get(target);
        if (id == null) {
            return Optional.empty();
        }
        return findById(id);
    }

    public List<CoinFlipGame> listPublicGames() {
        List<CoinFlipGame> list = new ArrayList<>();
        for (CoinFlipGame game : gamesById.values()) {
            if (game.type() == GameType.PUBLIC && game.state() == GameState.PENDING) {
                list.add(game);
            }
        }
        list.sort(Comparator.comparingLong(CoinFlipGame::createdAt));
        return Collections.unmodifiableList(list);
    }

    public boolean canCreate(Player player) {
        if (!plugin.config().limits().oneActivePerPlayer()) {
            return true;
        }
        return !activeByCreator.containsKey(player.getUniqueId());
    }

    public void forceCancel(CoinFlipGame game) {
        cancelGame(game, CancelReason.CANCELED);
    }

    private void cancelGame(CoinFlipGame game, CancelReason reason) {
        game.lock().lock();
        try {
            if (!gamesById.containsKey(game.id())) {
                return;
            }

            gamesById.remove(game.id());
            activeByCreator.remove(game.creator());
            game.target().ifPresent(privateByTarget::remove);

            if (game.state() == GameState.PENDING) {
                OfflinePlayer creator = Bukkit.getOfflinePlayer(game.creator());
                if (!economy.deposit(creator, game.amount())) {
                    messages.notifyStaffRaw("notify-error",
                            Placeholder.parsed("message", "Failed to refund creator for canceled game " + game.id()));
                }
            }

            game.state(reason.toState());
            notifyCancellation(game, reason);
        } finally {
            game.lock().unlock();
        }
    }

    public boolean createPublicGame(Player creator, double amount) {
        if (!canCreate(creator)) {
            messages.send(creator, "already-exists");
            return false;
        }
        if (!economy.hasBalance(creator, amount)) {
            messages.send(creator, "insufficient-funds");
            return false;
        }
        if (!checkBalanceLimit(creator, amount)) {
            return false;
        }
        if (!economy.withdraw(creator, amount)) {
            messages.send(creator, "insufficient-funds");
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + plugin.config().ui().expireSeconds() * 1000L;
        CoinFlipGame game = new CoinFlipGame(UUID.randomUUID(), creator.getUniqueId(), GameType.PUBLIC, null, amount, now, expiresAt);

        gamesById.put(game.id(), game);
        activeByCreator.put(creator.getUniqueId(), game.id());

        messages.send(creator, "game-created",
                Placeholder.parsed("amount", formatAmount(amount)),
                Placeholder.parsed("secs", String.valueOf(plugin.config().ui().expireSeconds())));

        playSound(creator, plugin.config().ui().sounds().open());
        broadcastCreatedGame(creator, amount);
        return true;
    }

    public boolean createPrivateGame(Player creator, Player target, double amount) {
        if (!canCreate(creator)) {
            messages.send(creator, "already-exists");
            return false;
        }
        if (privateByTarget.containsKey(target.getUniqueId())) {
            messages.send(creator, "already-exists");
            return false;
        }
        if (!economy.hasBalance(creator, amount)) {
            messages.send(creator, "insufficient-funds");
            return false;
        }
        if (!checkBalanceLimit(creator, amount)) {
            return false;
        }
        if (!economy.withdraw(creator, amount)) {
            messages.send(creator, "insufficient-funds");
            return false;
        }

        long now = System.currentTimeMillis();
        long expiresAt = now + plugin.config().ui().privateExpireSeconds() * 1000L;
        CoinFlipGame game = new CoinFlipGame(UUID.randomUUID(), creator.getUniqueId(), GameType.PRIVATE, target.getUniqueId(), amount, now, expiresAt);

        gamesById.put(game.id(), game);
        activeByCreator.put(creator.getUniqueId(), game.id());
        privateByTarget.put(target.getUniqueId(), game.id());

        messages.send(creator, "private-sent",
                Placeholder.parsed("target", target.getName()),
                Placeholder.parsed("amount", formatAmount(amount)));

        messages.send(target, "private-received",
                Placeholder.parsed("sender", creator.getName()),
                Placeholder.parsed("amount", formatAmount(amount)));

        sendPrivateActionBar(creator, target);
        playSound(target, plugin.config().ui().sounds().open());
        return true;
    }

    public boolean acceptPublic(Player acceptor, UUID gameId) {
        CoinFlipGame game = gamesById.get(gameId);
        if (game == null || game.type() != GameType.PUBLIC) {
            messages.send(acceptor, "not-found");
            return false;
        }
        return accept(acceptor, game);
    }

    public boolean acceptPrivate(Player acceptor, Player creator) {
        Optional<CoinFlipGame> optional = findPrivateByTarget(acceptor.getUniqueId());
        if (optional.isEmpty()) {
            messages.send(acceptor, "not-found");
            return false;
        }
        CoinFlipGame game = optional.get();
        if (!game.creator().equals(creator.getUniqueId())) {
            messages.send(acceptor, "not-found");
            return false;
        }
        return accept(acceptor, game);
    }

    public void denyPrivate(Player denier, Player creator) {
        Optional<CoinFlipGame> optional = findPrivateByTarget(denier.getUniqueId());
        if (optional.isEmpty()) {
            messages.send(denier, "not-found");
            return;
        }
        CoinFlipGame game = optional.get();
        if (!game.creator().equals(creator.getUniqueId())) {
            messages.send(denier, "not-found");
            return;
        }
        cancelGame(game, CancelReason.DENIED);
    }

    public void cancelOwn(Player player) {
        Optional<CoinFlipGame> optional = findByCreator(player.getUniqueId());
        if (optional.isEmpty()) {
            messages.send(player, "not-found");
            return;
        }
        cancelGame(optional.get(), CancelReason.CANCELED);
    }

    private boolean accept(Player acceptor, CoinFlipGame game) {
        if (game.creator().equals(acceptor.getUniqueId())) {
            messages.send(acceptor, "self-accept");
            return false;
        }
        if (resolvingPlayers.contains(acceptor.getUniqueId())) {
            messages.send(acceptor, "busy");
            return false;
        }
        Optional<Player> creatorOpt = Optional.ofNullable(Bukkit.getPlayer(game.creator()));
        if (creatorOpt.isEmpty()) {
            cancelGame(game, CancelReason.CREATOR_OFFLINE);
            messages.send(acceptor, "not-found");
            return false;
        }
        Player creator = creatorOpt.get();
        if (resolvingPlayers.contains(creator.getUniqueId())) {
            messages.send(acceptor, "busy");
            return false;
        }

        game.lock().lock();
        try {
            if (game.state() != GameState.PENDING) {
                messages.send(acceptor, "not-found");
                return false;
            }
            long now = System.currentTimeMillis();
            if (game.isExpired(now)) {
                cancelGame(game, CancelReason.EXPIRED);
                messages.send(acceptor, "expired", Placeholder.parsed("amount", formatAmount(game.amount())));
                return false;
            }

            if (!economy.hasBalance(acceptor, game.amount())) {
                messages.send(acceptor, "insufficient-funds");
                return false;
            }
            if (!checkBalanceLimit(acceptor, game.amount())) {
                return false;
            }

            if (!economy.withdraw(acceptor, game.amount())) {
                messages.send(acceptor, "insufficient-funds");
                return false;
            }

            resolvingPlayers.add(creator.getUniqueId());
            resolvingPlayers.add(acceptor.getUniqueId());

            game.state(GameState.RESOLVING);
            game.acceptor(acceptor.getUniqueId());

            messages.send(creator, "accepted",
                    Placeholder.parsed("amount", formatAmount(game.amount())),
                    Placeholder.parsed("other", acceptor.getName()));
            messages.send(acceptor, "accepted",
                    Placeholder.parsed("amount", formatAmount(game.amount())),
                    Placeholder.parsed("other", creator.getName()));

            resolveGame(game, creator, acceptor);
            return true;
        } finally {
            resolvingPlayers.remove(acceptor.getUniqueId());
            resolvingPlayers.remove(game.creator());
            game.lock().unlock();
        }
    }

    private void resolveGame(CoinFlipGame game, Player creator, Player acceptor) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean creatorWins = random.nextBoolean();
        long proof = random.nextLong();
        Player winner = creatorWins ? creator : acceptor;
        Player loser = creatorWins ? acceptor : creator;

        CoinFlipConfig.TaxSettings tax = plugin.config().tax();
        PayoutCalculator.Payout payout = PayoutCalculator.calculate(game.amount(), tax);
        double totalPot = payout.totalPot();
        double winnings = payout.winnings();
        double taxAmount = payout.taxAmount();

        if (!economy.deposit(winner, winnings)) {
            // Refund both players and abort
            economy.deposit(creator, game.amount());
            economy.deposit(acceptor, game.amount());
            messages.notifyStaffRaw("notify-error",
                    Placeholder.parsed("message", "Failed to pay winnings for game " + game.id()));
            messages.send(creator, "canceled", Placeholder.parsed("amount", formatAmount(game.amount())));
            messages.send(acceptor, "canceled", Placeholder.parsed("amount", formatAmount(game.amount())));
            game.state(GameState.CANCELED);
            gamesById.remove(game.id());
            activeByCreator.remove(game.creator());
            game.target().ifPresent(privateByTarget::remove);
            return;
        }

        handleTaxSink(tax, taxAmount);

        game.state(GameState.COMPLETED);
        game.resolvedAt(Instant.now());

        gamesById.remove(game.id());
        activeByCreator.remove(game.creator());
        game.target().ifPresent(privateByTarget::remove);

        double loserLoss = game.amount();

        messages.send(winner, "resolved-win",
                Placeholder.parsed("won", formatAmount(winnings)),
                Placeholder.parsed("tax", formatAmount(taxAmount)));
        messages.send(loser, "resolved-lose",
                Placeholder.parsed("lost", formatAmount(loserLoss)));

        messages.broadcast("broadcast-result",
                Placeholder.parsed("w", winner.getName()),
                Placeholder.parsed("l", loser.getName()),
                Placeholder.parsed("pot", formatAmount(totalPot)));

        playSound(winner, plugin.config().ui().sounds().win());
        playSound(loser, plugin.config().ui().sounds().lose());

        stats.recordResult(winner.getUniqueId(), loser.getUniqueId(), winnings, loserLoss);

        String logMessage = "CoinFlip resolved: " + creator.getName() + " vs " + acceptor.getName()
                + ", winner=" + winner.getName() + ", amount=" + game.amount()
                + ", tax=" + taxAmount
                + ", proof=" + proof
                + ", timestamp=" + Instant.now();
        plugin.getLogger().info(logMessage);
    }

    private void handleTaxSink(CoinFlipConfig.TaxSettings tax, double taxAmount) {
        if (!tax.enabled() || taxAmount <= 0) {
            return;
        }
        if ("server".equalsIgnoreCase(tax.recipient())) {
            return;
        }
        OfflinePlayer recipient = economy.offlinePlayer(tax.recipient());
        if (!economy.deposit(recipient, taxAmount)) {
            messages.notifyStaffRaw("notify-error",
                    Placeholder.parsed("message", "Failed to deposit tax to " + tax.recipient()));
        }
    }

    private void tickExpiry() {
        long now = System.currentTimeMillis();
        for (CoinFlipGame game : new ArrayList<>(gamesById.values())) {
            if (game.isExpired(now)) {
                cancelGame(game, CancelReason.EXPIRED);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        findByCreator(playerId).ifPresent(game -> cancelGame(game, CancelReason.CREATOR_QUIT));
        findPrivateByTarget(playerId).ifPresent(game -> cancelGame(game, CancelReason.TARGET_QUIT));
    }

    private void notifyCancellation(CoinFlipGame game, CancelReason reason) {
        Player creator = Bukkit.getPlayer(game.creator());
        TagResolver amount = Placeholder.parsed("amount", formatAmount(game.amount()));
        switch (reason) {
            case EXPIRED -> {
                if (creator != null) {
                    messages.send(creator, "expired", amount);
                    playSound(creator, plugin.config().ui().sounds().lose());
                }
                game.target().ifPresent(targetId -> {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) {
                        messages.send(target, "expired", amount);
                    }
                });
            }
            case CANCELED, SHUTDOWN, CREATOR_OFFLINE, CREATOR_QUIT, TARGET_QUIT -> {
                if (creator != null) {
                    messages.send(creator, "canceled", amount);
                }
                game.target().ifPresent(targetId -> {
                    Player target = Bukkit.getPlayer(targetId);
                    if (target != null) {
                        messages.send(target, "canceled", amount);
                    }
                });
            }
            case DENIED -> {
                if (creator != null) {
                    messages.send(creator, "canceled", amount);
                }
            }
        }
    }

    private void sendPrivateActionBar(Player creator, Player target) {
        String commandBase = "/cf " + creator.getName() + " ";
        net.kyori.adventure.text.Component accept = net.kyori.adventure.text.Component.text("[ACCEPT]", net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to accept", net.kyori.adventure.text.format.NamedTextColor.GREEN)))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(commandBase + "accept"));
        net.kyori.adventure.text.Component deny = net.kyori.adventure.text.Component.text("[DENY]", net.kyori.adventure.text.format.NamedTextColor.RED)
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to deny", net.kyori.adventure.text.format.NamedTextColor.RED)))
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(commandBase + "deny"));
        net.kyori.adventure.text.Component combined = accept.append(net.kyori.adventure.text.Component.text(" ")).append(deny);
        messages.player(target).sendMessage(combined);
    }

    private void playSound(Player player, org.bukkit.Sound sound) {
        net.kyori.adventure.key.Key key = net.kyori.adventure.key.Key.key(
                sound.getKey().getNamespace(),
                sound.getKey().getKey()
        );
        messages.player(player).playSound(Sound.sound(
                key,
                Sound.Source.MASTER,
                1f,
                1f
        ));
    }

    private String formatAmount(double amount) {
        return economy.formatNumber(amount);
    }

    private void broadcastCreatedGame(Player creator, double amount) {
        CoinFlipConfig.BroadcastSettings broadcast = plugin.config().broadcast();
        if (!broadcast.enabled()) {
            return;
        }
        String template = broadcast.message();
        if (template == null || template.isBlank()) {
            return;
        }

        String formattedAmount = formatAmount(amount);
        String resolved = template
                .replace("%player%", creator.getName())
                .replace("%amount%", formattedAmount);

        Component message = messages.component("prefix").append(messages.parse(resolved,
                Placeholder.parsed("player", creator.getName()),
                Placeholder.parsed("amount", formattedAmount)
        ));
        messages.broadcast(message);
    }

    private enum CancelReason {
        EXPIRED(GameState.EXPIRED),
        CANCELED(GameState.CANCELED),
        DENIED(GameState.CANCELED),
        CREATOR_QUIT(GameState.CANCELED),
        TARGET_QUIT(GameState.CANCELED),
        CREATOR_OFFLINE(GameState.CANCELED),
        SHUTDOWN(GameState.CANCELED);

        private final GameState state;

        CancelReason(GameState state) {
            this.state = state;
        }

        public GameState toState() {
            return state;
        }
    }

    private boolean checkBalanceLimit(Player player, double amount) {
        if (player.hasPermission("coinflip.bypass.minmax")) {
            return true;
        }
        CoinFlipConfig.EconomySettings economySettings = plugin.config().economy();
        double maxAllowed = BetUtil.maxBalanceBet(economy.balance(player), economySettings);
        if (amount <= maxAllowed) {
            return true;
        }
        messages.send(player, "balance-limit",
                Placeholder.parsed("percent", formatPercent(economySettings.maxBalancePercent())),
                Placeholder.parsed("max", formatAmount(maxAllowed)));
        return false;
    }

    private String formatPercent(double percent) {
        return economy.formatNumber(percent);
    }
}
