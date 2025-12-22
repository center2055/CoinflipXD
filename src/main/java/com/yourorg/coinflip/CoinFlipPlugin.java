package com.yourorg.coinflip;

import com.yourorg.coinflip.command.CoinFlipCommand;
import com.yourorg.coinflip.config.ConfigService;
import com.yourorg.coinflip.config.CoinFlipConfig;
import com.yourorg.coinflip.economy.EconomyService;
import com.yourorg.coinflip.game.GameService;
import com.yourorg.coinflip.gui.GuiService;
import com.yourorg.coinflip.messages.MessageService;
import com.yourorg.coinflip.stats.StatsService;
import com.yourorg.coinflip.util.GeyserUtil;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class CoinFlipPlugin extends JavaPlugin {

    private static CoinFlipPlugin instance;

    private BukkitAudiences audiences;
    private ConfigService configService;
    private MessageService messageService;
    private EconomyService economyService;
    private StatsService statsService;
    private GameService gameService;
    private GuiService guiService;
    private CoinFlipCommand commandExecutor;
    private GeyserUtil geyserUtil;

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

        this.geyserUtil = new GeyserUtil(this);
        this.gameService = new GameService(this);
        this.guiService = new GuiService(this);

        gameService.start();
        guiService.registerListeners();

        PluginCommand command = getCommand("cf");
        if (command != null) {
            this.commandExecutor = new CoinFlipCommand(this);
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        } else {
            getLogger().severe("Command 'cf' was not found in plugin.yml; commands will not work correctly.");
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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if ("cf".equalsIgnoreCase(command.getName()) && commandExecutor != null) {
            return commandExecutor.onCommand(sender, command, label, args);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if ("cf".equalsIgnoreCase(command.getName()) && commandExecutor != null) {
            return commandExecutor.onTabComplete(sender, command, alias, args);
        }
        return super.onTabComplete(sender, command, alias, args);
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

    public GeyserUtil geyserUtil() {
        return geyserUtil;
    }
}

