package com.yourorg.coinflip;

import com.yourorg.coinflip.command.CoinFlipCommand;
import com.yourorg.coinflip.config.ConfigService;
import com.yourorg.coinflip.config.CoinFlipConfig;
import com.yourorg.coinflip.economy.EconomyService;
import com.yourorg.coinflip.game.GameService;
import com.yourorg.coinflip.gui.GuiService;
import com.yourorg.coinflip.messages.MessageService;
import com.yourorg.coinflip.stats.StatsService;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CoinFlipPlugin extends JavaPlugin {

    private static CoinFlipPlugin instance;

    private BukkitAudiences audiences;
    private ConfigService configService;
    private MessageService messageService;
    private EconomyService economyService;
    private StatsService statsService;
    private GameService gameService;
    private GuiService guiService;

    private CoinFlipConfig config;

    public static CoinFlipPlugin getInstance() {
        return instance;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        saveResource("messages.yml", false);

        this.audiences = BukkitAudiences.create(this);
        this.configService = new ConfigService(this);
        this.config = configService.load();
        this.messageService = new MessageService(this);
        this.economyService = new EconomyService(this);
        if (!economyService.setupEconomy()) {
            getLogger().severe("Vault dependency was not found or no economy provider detected. Disabling plugin.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        this.statsService = new StatsService(this);
        statsService.init();

        this.gameService = new GameService(this);
        this.guiService = new GuiService(this);

        gameService.start();
        guiService.registerListeners();

        PluginCommand command = getCommand("cf");
        if (command != null) {
            CoinFlipCommand executor = new CoinFlipCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
    }

    @Override
    public void onDisable() {
        if (gameService != null) {
            gameService.shutdown();
        }
        if (statsService != null) {
            statsService.shutdown();
        }
        if (audiences != null) {
            audiences.close();
            audiences = null;
        }
        instance = null;
    }

    public BukkitAudiences audiences() {
        return audiences;
    }

    public ConfigService configService() {
        return configService;
    }

    public CoinFlipConfig config() {
        return config;
    }

    public void setConfig(CoinFlipConfig config) {
        this.config = config;
    }

    public MessageService messageService() {
        return messageService;
    }

    public EconomyService economyService() {
        return economyService;
    }

    public StatsService statsService() {
        return statsService;
    }

    public GameService gameService() {
        return gameService;
    }

    public GuiService guiService() {
        return guiService;
    }
}

