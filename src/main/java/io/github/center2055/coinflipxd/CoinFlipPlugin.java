package io.github.center2055.coinflipxd;

import io.github.center2055.coinflipxd.command.CoinFlipCommand;
import io.github.center2055.coinflipxd.config.CoinFlipConfig;
import io.github.center2055.coinflipxd.config.ConfigService;
import io.github.center2055.coinflipxd.config.PlayerSettingsService;
import io.github.center2055.coinflipxd.economy.EconomyService;
import io.github.center2055.coinflipxd.game.BetBook;
import io.github.center2055.coinflipxd.game.GameService;
import io.github.center2055.coinflipxd.gui.GuiService;
import io.github.center2055.coinflipxd.messages.MessageService;
import io.github.center2055.coinflipxd.stats.StatsService;
import io.github.center2055.coinflipxd.storage.Database;
import io.github.center2055.coinflipxd.storage.EscrowStore;
import io.github.center2055.coinflipxd.util.GeyserUtil;
import io.github.center2055.coinflipxd.util.Scheduling;
import net.kyori.adventure.platform.bukkit.BukkitAudiences;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.logging.Level;

public final class CoinFlipPlugin extends JavaPlugin {

    private BukkitAudiences audiences;
    private ConfigService configService;
    private MessageService messageService;
    private PlayerSettingsService playerSettingsService;
    private EconomyService economyService;
    private Database database;
    private EscrowStore escrow;
    private StatsService statsService;
    private GameService gameService;
    private GuiService guiService;
    private GeyserUtil geyserUtil;
    private volatile CoinFlipConfig config;
    private volatile boolean ready;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configService = new ConfigService(this);
        config = configService.load();
        messageService = new MessageService(this);
        playerSettingsService = new PlayerSettingsService(this);

        database = new Database(getDataFolder().toPath().resolve("data.db"), getLogger());
        try {
            database.init();
        } catch (SQLException e) {
            getLogger().log(Level.SEVERE, "Could not open plugins/" + getName() + "/data.db. Disabling.", e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        escrow = new EscrowStore(database);
        statsService = new StatsService(database, getLogger());
        economyService = new EconomyService(getLogger());
        BetBook book = new BetBook(economyService, escrow, new SecureRandom(), this::payoutRules, getLogger());
        geyserUtil = new GeyserUtil(this);
        gameService = new GameService(this, book);
        guiService = new GuiService(this);

        if (economyService.setup()) {
            finishStartup();
        } else {
            // Some economy plugins register with Vault after us. Every plugin is enabled by the first tick.
            Scheduling.nextTick(this, () -> {
                if (economyService.setup()) {
                    finishStartup();
                } else {
                    getLogger().severe("No Vault economy found. Install Vault and an economy plugin such as EssentialsX. Disabling.");
                    getServer().getPluginManager().disablePlugin(this);
                }
            });
        }
    }

    private void finishStartup() {
        int refunded = BetBook.recover(escrow, economyService, getLogger());
        if (refunded > 0) {
            getLogger().info("Refunded " + refunded + " stake(s) from coinflips that were open when the server last stopped.");
        }
        gameService.start();
        guiService.registerListeners();
        PluginCommand command = getCommand("cf");
        if (command != null) {
            CoinFlipCommand executor = new CoinFlipCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }
        ready = true;
        getLogger().info("Using economy: " + economyService.providerName()
                + (Scheduling.FOLIA ? " (Folia)" : "") + (geyserUtil.isAvailable() ? ", Floodgate forms enabled" : ""));
    }

    @Override
    public void onDisable() {
        if (ready && gameService != null) {
            gameService.shutdown();
        }
        if (database != null) {
            database.shutdown();
        }
        synchronized (this) {
            if (audiences != null) {
                audiences.close();
                audiences = null;
            }
        }
        ready = false;
    }

    private BetBook.PayoutRules payoutRules() {
        CoinFlipConfig c = config;
        return new BetBook.PayoutRules(c.tax().enabled(), c.tax().percent(), c.tax().recipientId(),
                c.economy().requireWholeNumbers());
    }

    public boolean isReady() {
        return ready;
    }

    /**
     * Adventure bridge for servers without native Adventure (Spigot, Bukkit). Created on first use, so on Paper,
     * where players are audiences already, the bundled adventure-platform code never runs.
     */
    public synchronized BukkitAudiences audiences() {
        if (audiences == null) {
            audiences = BukkitAudiences.create(this);
        }
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

    public PlayerSettingsService playerSettingsService() {
        return playerSettingsService;
    }
}
