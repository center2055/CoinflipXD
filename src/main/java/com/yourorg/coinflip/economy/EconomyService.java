package com.yourorg.coinflip.economy;

import com.yourorg.coinflip.CoinFlipPlugin;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.util.Objects;

public final class EconomyService {

    private final CoinFlipPlugin plugin;
    private Economy economy;

    public EconomyService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean setupEconomy() {
        if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    public boolean hasBalance(Player player, double amount) {
        Objects.requireNonNull(economy, "Economy provider not set");
        return economy.has(player, amount);
    }

    public boolean hasBalance(OfflinePlayer player, double amount) {
        Objects.requireNonNull(economy, "Economy provider not set");
        return economy.has(player, amount);
    }

    public boolean withdraw(OfflinePlayer player, double amount) {
        Objects.requireNonNull(economy, "Economy provider not set");
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    public boolean deposit(OfflinePlayer player, double amount) {
        Objects.requireNonNull(economy, "Economy provider not set");
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    public String formatCurrency(double amount) {
        Objects.requireNonNull(economy, "Economy provider not set");
        return economy.format(amount);
    }

    public String formatNumber(double amount) {
        DecimalFormat format = new DecimalFormat("#,##0.##");
        format.setGroupingUsed(true);
        return format.format(amount);
    }

    public Economy economy() {
        return economy;
    }

    public OfflinePlayer offlinePlayer(String name) {
        return Bukkit.getOfflinePlayer(name);
    }
}

