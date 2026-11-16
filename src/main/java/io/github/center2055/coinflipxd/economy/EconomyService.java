package io.github.center2055.coinflipxd.economy;

import io.github.center2055.coinflipxd.game.BetBook;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Vault economy access. Also the {@link BetBook.Bank} the bet logic moves money with. */
public final class EconomyService implements BetBook.Bank {

    private final Logger logger;
    private volatile Economy economy;
    private volatile Plugin provider;

    public EconomyService(Logger logger) {
        this.logger = logger;
    }

    /** Looks up the Vault economy. Economy plugins may register late, so this is retried on the first tick. */
    public boolean setup() {
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        this.provider = rsp.getPlugin();
        return true;
    }

    public boolean isReady() {
        return economy != null;
    }

    /** True while the plugin behind the economy is still enabled (false during late shutdown). */
    public boolean isUsable() {
        return economy != null && (provider == null || provider.isEnabled());
    }

    public String providerName() {
        return economy == null ? "none" : economy.getName();
    }

    public boolean has(OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public double balance(OfflinePlayer player) {
        return economy.getBalance(player);
    }

    @Override
    public boolean withdraw(UUID player, double amount) {
        return transact(player, amount, false);
    }

    @Override
    public boolean deposit(UUID player, double amount) {
        return transact(player, amount, true);
    }

    private boolean transact(UUID playerId, double amount, boolean deposit) {
        Economy eco = economy;
        if (eco == null) {
            return false;
        }
        try {
            OfflinePlayer player = Bukkit.getOfflinePlayer(playerId); // by UUID: never a web lookup
            if (!deposit && !eco.has(player, amount)) {
                return false;
            }
            EconomyResponse response = deposit ? eco.depositPlayer(player, amount) : eco.withdrawPlayer(player, amount);
            return response != null && response.transactionSuccess();
        } catch (RuntimeException e) {
            logger.log(Level.SEVERE, (deposit ? "Deposit" : "Withdrawal") + " of " + amount + " for " + playerId
                    + " failed in " + providerName(), e);
            return false;
        }
    }

    public String formatCurrency(double amount) {
        Economy eco = economy;
        try {
            return eco != null ? eco.format(amount) : formatNumber(amount);
        } catch (RuntimeException e) {
            return formatNumber(amount);
        }
    }

    public String formatNumber(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        format.setGroupingUsed(true);
        return format.format(amount);
    }
}
