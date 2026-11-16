package io.github.center2055.coinflipxd.game;

import io.github.center2055.coinflipxd.CoinFlipPlugin;
import io.github.center2055.coinflipxd.config.CoinFlipConfig;
import io.github.center2055.coinflipxd.economy.EconomyService;
import io.github.center2055.coinflipxd.messages.MessageService;
import io.github.center2055.coinflipxd.stats.StatsService;
import io.github.center2055.coinflipxd.util.BetUtil;
import io.github.center2055.coinflipxd.util.Scheduling;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Player-facing side of coinflips: checks, messages, sounds and stats. Money is handled by {@link BetBook}. */
public final class GameService implements Listener {

    private enum Ending { EXPIRED, CANCELED, DENIED, CREATOR_QUIT, TARGET_QUIT }

    private final CoinFlipPlugin plugin;
    private final BetBook book;
    private final EconomyService economy;
    private final MessageService messages;
    private final StatsService stats;
    private Scheduling.Task expiryTask;

    public GameService(CoinFlipPlugin plugin, BetBook book) {
        this.plugin = plugin;
        this.book = book;
        this.economy = plugin.economyService();
        this.messages = plugin.messageService();
        this.stats = plugin.statsService();
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        expiryTask = Scheduling.repeatGlobal(plugin, this::tickExpiry, 20L, 20L);
    }

    /**
     * Refunds open games while the economy plugin is still running. If it has already shut down, the stakes stay in
     * escrow and are refunded on the next start instead of disappearing.
     */
    public void shutdown() {
        if (expiryTask != null) {
            expiryTask.cancel();
        }
        if (economy.isUsable()) {
            for (BetBook.Refund refund : book.refundAll()) {
                if (!refund.refunded()) {
                    plugin.getLogger().warning("Stake of coinflip " + refund.game().id() + " will be refunded on the next start.");
                }
            }
        } else {
            int open = book.abandonAll();
            if (open > 0) {
                plugin.getLogger().warning(open + " open coinflip(s) could not be refunded because the economy plugin "
                        + "already shut down. They will be refunded automatically on the next start.");
            }
        }
    }

    // ------------------------------------------------------------------ queries

    public List<CoinFlipGame> listPublicGames() {
        return book.publicGames();
    }

    public Optional<CoinFlipGame> findById(UUID id) {
        return book.get(id);
    }

    public Optional<CoinFlipGame> findByCreator(UUID creator) {
        return book.byCreator(creator);
    }

    public Optional<CoinFlipGame> findPrivateByTarget(UUID target) {
        return book.byTarget(target);
    }

    // ------------------------------------------------------------------ create

    public boolean createPublicGame(Player creator, double amount) {
        if (!checkFunds(creator, amount)) {
            return false;
        }
        CoinFlipConfig cfg = plugin.config();
        BetBook.CreateResult result = book.create(creator.getUniqueId(), GameType.PUBLIC, null, amount,
                System.currentTimeMillis(), cfg.ui().expireSeconds() * 1000L, cfg.limits().oneActivePerPlayer());
        if (!reportCreateFailure(creator, result, null)) {
            return false;
        }
        messages.send(creator, "game-created",
                Placeholder.parsed("amount", format(amount)),
                Placeholder.parsed("secs", String.valueOf(cfg.ui().expireSeconds())));
        playSound(creator, cfg.ui().sounds().open());
        broadcastCreatedGame(creator, amount);
        return true;
    }

    public boolean createPrivateGame(Player creator, Player target, double amount) {
        if (!checkFunds(creator, amount)) {
            return false;
        }
        CoinFlipConfig cfg = plugin.config();
        BetBook.CreateResult result = book.create(creator.getUniqueId(), GameType.PRIVATE, target.getUniqueId(), amount,
                System.currentTimeMillis(), cfg.ui().privateExpireSeconds() * 1000L, cfg.limits().oneActivePerPlayer());
        if (!reportCreateFailure(creator, result, target)) {
            return false;
        }
        messages.send(creator, "private-sent",
                Placeholder.parsed("target", target.getName()),
                Placeholder.parsed("amount", format(amount)));
        messages.send(target, "private-received",
                Placeholder.parsed("sender", creator.getName()),
                Placeholder.parsed("amount", format(amount)));
        sendAcceptButtons(creator, target);
        playSound(target, cfg.ui().sounds().open());
        return true;
    }

    private boolean reportCreateFailure(Player creator, BetBook.CreateResult result, Player target) {
        switch (result.status()) {
            case OK -> {
                return true;
            }
            case ALREADY_ACTIVE -> messages.send(creator, "already-exists");
            case TARGET_BUSY -> messages.send(creator, "target-busy",
                    Placeholder.parsed("target", target == null ? "?" : target.getName()));
            case SELF -> messages.send(creator, "self-accept");
            default -> messages.send(creator, "insufficient-funds");
        }
        return false;
    }

    // ------------------------------------------------------------------ accept

    public boolean acceptPublic(Player acceptor, UUID gameId) {
        Optional<CoinFlipGame> game = book.get(gameId);
        if (game.isEmpty() || game.get().type() != GameType.PUBLIC) {
            messages.send(acceptor, "not-found");
            return false;
        }
        return accept(acceptor, game.get());
    }

    public boolean acceptPrivate(Player acceptor, Player creator) {
        Optional<CoinFlipGame> game = book.byTarget(acceptor.getUniqueId());
        if (game.isEmpty() || !game.get().creator().equals(creator.getUniqueId())) {
            messages.send(acceptor, "not-found");
            return false;
        }
        return accept(acceptor, game.get());
    }

    private boolean accept(Player acceptor, CoinFlipGame game) {
        Player creator = Bukkit.getPlayer(game.creator());
        if (creator == null) {
            // Shouldn't happen (quitting cancels), but never let anyone play against an absent creator.
            book.cancel(game.id()).ifPresent(r -> notifyEnded(r, Ending.CREATOR_QUIT));
            messages.send(acceptor, "not-found");
            return false;
        }
        if (!checkFunds(acceptor, game.amount())) {
            return false;
        }
        BetBook.Outcome outcome = book.accept(acceptor.getUniqueId(), game.id(), System.currentTimeMillis());
        switch (outcome.status()) {
            case OK -> {
                announceResult(outcome, creator, acceptor);
                return true;
            }
            case SELF -> messages.send(acceptor, "self-accept");
            case INSUFFICIENT_FUNDS -> messages.send(acceptor, "insufficient-funds");
            case EXPIRED -> {
                messages.send(acceptor, "not-found");
                if (outcome.refund() != null) {
                    notifyEnded(outcome.refund(), Ending.EXPIRED);
                }
            }
            case PAYOUT_FAILED -> {
                messages.send(creator, "payout-failed");
                messages.send(acceptor, "payout-failed");
                messages.notifyStaffRaw("notify-error",
                        Placeholder.parsed("message", "Could not pay out coinflip " + game.id() + "; stakes refunded."));
            }
            default -> messages.send(acceptor, "not-found");
        }
        return false;
    }

    private void announceResult(BetBook.Outcome outcome, Player creator, Player acceptor) {
        CoinFlipGame game = outcome.game();
        Player winner = outcome.winner().equals(creator.getUniqueId()) ? creator : acceptor;
        Player loser = winner == creator ? acceptor : creator;
        double winnings = outcome.payout().winnings();
        double tax = outcome.payout().taxAmount();

        messages.send(creator, "accepted-by",
                Placeholder.parsed("amount", format(game.amount())),
                Placeholder.parsed("other", acceptor.getName()));
        messages.send(acceptor, "accepted",
                Placeholder.parsed("amount", format(game.amount())),
                Placeholder.parsed("other", creator.getName()));
        messages.send(winner, "resolved-win",
                Placeholder.parsed("won", format(winnings)),
                Placeholder.parsed("tax", format(tax)));
        messages.send(loser, "resolved-lose", Placeholder.parsed("lost", format(game.amount())));
        messages.broadcast("broadcast-result",
                Placeholder.parsed("w", winner.getName()),
                Placeholder.parsed("l", loser.getName()),
                Placeholder.parsed("pot", format(outcome.payout().totalPot())));
        playSound(acceptor, plugin.config().ui().sounds().accept());
        playSound(winner, plugin.config().ui().sounds().win());
        playSound(loser, plugin.config().ui().sounds().lose());
        if (!outcome.taxDelivered()) {
            messages.notifyStaffRaw("notify-error", Placeholder.parsed("message",
                    "Could not deposit " + format(tax) + " tax to " + plugin.config().tax().recipient()));
        }

        stats.recordResult(winner.getUniqueId(), loser.getUniqueId(), winnings - game.amount(), game.amount());
        plugin.getLogger().info("Coinflip " + game.id() + ": " + creator.getName() + " vs " + acceptor.getName()
                + " for " + game.amount() + " each, winner " + winner.getName()
                + ", paid " + winnings + ", tax " + tax);
    }

    // ------------------------------------------------------------------ cancel, deny, expire, quit

    public void denyPrivate(Player denier, Player creator) {
        Optional<CoinFlipGame> game = book.byTarget(denier.getUniqueId());
        if (game.isEmpty() || !game.get().creator().equals(creator.getUniqueId())) {
            messages.send(denier, "not-found");
            return;
        }
        book.cancel(game.get().id()).ifPresent(r -> notifyEnded(r, Ending.DENIED));
    }

    public void cancelOwn(Player player) {
        Optional<CoinFlipGame> game = book.byCreator(player.getUniqueId());
        if (game.isEmpty()) {
            messages.send(player, "not-found");
            return;
        }
        book.cancel(game.get().id()).ifPresent(r -> notifyEnded(r, Ending.CANCELED));
    }

    public boolean forceCancel(CoinFlipGame game) {
        Optional<BetBook.Refund> refund = book.cancel(game.id());
        refund.ifPresent(r -> notifyEnded(r, Ending.CANCELED));
        return refund.isPresent();
    }

    private void tickExpiry() {
        for (BetBook.Refund refund : book.expire(System.currentTimeMillis())) {
            notifyEnded(refund, Ending.EXPIRED);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        book.byCreator(id).flatMap(g -> book.cancel(g.id())).ifPresent(r -> notifyEnded(r, Ending.CREATOR_QUIT));
        book.byTarget(id).flatMap(g -> book.cancel(g.id())).ifPresent(r -> notifyEnded(r, Ending.TARGET_QUIT));
    }

    private void notifyEnded(BetBook.Refund refund, Ending ending) {
        CoinFlipGame game = refund.game();
        Player creator = Bukkit.getPlayer(game.creator());
        String amount = format(game.amount());
        String creatorName = name(game.creator());
        String targetName = game.target().map(this::name).orElse("?");

        if (creator != null) {
            if (!refund.refunded()) {
                messages.send(creator, "refund-pending", Placeholder.parsed("amount", amount));
            } else {
                switch (ending) {
                    case EXPIRED -> {
                        messages.send(creator, "expired", Placeholder.parsed("amount", amount));
                        playSound(creator, plugin.config().ui().sounds().lose());
                    }
                    case DENIED -> messages.send(creator, "denied",
                            Placeholder.parsed("target", targetName), Placeholder.parsed("amount", amount));
                    default -> messages.send(creator, "canceled", Placeholder.parsed("amount", amount));
                }
            }
        }
        game.target().map(Bukkit::getPlayer).ifPresent(target -> {
            switch (ending) {
                case EXPIRED -> messages.send(target, "challenge-expired",
                        Placeholder.parsed("sender", creatorName), Placeholder.parsed("amount", amount));
                case DENIED -> messages.send(target, "challenge-declined", Placeholder.parsed("sender", creatorName));
                case TARGET_QUIT -> { /* they left */ }
                default -> messages.send(target, "challenge-canceled",
                        Placeholder.parsed("sender", creatorName), Placeholder.parsed("amount", amount));
            }
        });
    }

    // ------------------------------------------------------------------ helpers

    /** Has the money and stays within the balance-percent limit. Sends the reason if not. */
    private boolean checkFunds(Player player, double amount) {
        if (!economy.has(player, amount)) {
            messages.send(player, "insufficient-funds");
            return false;
        }
        if (player.hasPermission("coinflip.bypass.minmax")) {
            return true;
        }
        CoinFlipConfig.EconomySettings settings = plugin.config().economy();
        double maxAllowed = BetUtil.maxBalanceBet(economy.balance(player), settings);
        if (amount <= maxAllowed) {
            return true;
        }
        messages.send(player, "balance-limit",
                Placeholder.parsed("percent", economy.formatNumber(settings.maxBalancePercent())),
                Placeholder.parsed("max", format(maxAllowed)));
        return false;
    }

    private void sendAcceptButtons(Player creator, Player target) {
        String base = "/cf " + creator.getName() + " ";
        Component accept = Component.text("[ACCEPT]", NamedTextColor.GREEN)
                .hoverEvent(HoverEvent.showText(Component.text("Click to accept", NamedTextColor.GREEN)))
                .clickEvent(ClickEvent.runCommand(base + "accept"));
        Component deny = Component.text("[DENY]", NamedTextColor.RED)
                .hoverEvent(HoverEvent.showText(Component.text("Click to deny", NamedTextColor.RED)))
                .clickEvent(ClickEvent.runCommand(base + "deny"));
        messages.player(target).sendMessage(accept.append(Component.space()).append(deny));
    }

    private void broadcastCreatedGame(Player creator, double amount) {
        CoinFlipConfig.BroadcastSettings broadcast = plugin.config().broadcast();
        if (!broadcast.enabled() || broadcast.message() == null || broadcast.message().isBlank()) {
            return;
        }
        String amountText = format(amount);
        String resolved = broadcast.message().replace("%player%", creator.getName()).replace("%amount%", amountText);
        messages.broadcast(messages.component("prefix").append(messages.parse(resolved,
                Placeholder.parsed("player", creator.getName()),
                Placeholder.parsed("amount", amountText))));
    }

    private void playSound(Player player, Key key) {
        if (key != null) {
            messages.player(player).playSound(Sound.sound(key, Sound.Source.MASTER, 1f, 1f));
        }
    }

    private String name(UUID id) {
        OfflinePlayer p = Bukkit.getOfflinePlayer(id);
        return p.getName() != null ? p.getName() : id.toString().substring(0, 8);
    }

    private String format(double amount) {
        return economy.formatNumber(amount);
    }
}
