package com.yourorg.coinflip.gui;

import com.yourorg.coinflip.CoinFlipPlugin;
import com.yourorg.coinflip.config.CoinFlipConfig;
import com.yourorg.coinflip.game.CoinFlipGame;
import com.yourorg.coinflip.game.GameService;
import com.yourorg.coinflip.game.GameType;
import com.yourorg.coinflip.util.InventoryUtil;
import com.yourorg.coinflip.util.ItemMetaUtil;
import com.yourorg.coinflip.util.HelpUtil;
import com.yourorg.coinflip.util.TimeUtil;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public final class GuiService implements Listener {

    private final CoinFlipPlugin plugin;
    private final GameService gameService;

    public GuiService(CoinFlipPlugin plugin) {
        this.plugin = plugin;
        this.gameService = plugin.gameService();
    }

    public void registerListeners() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openBrowser(Player player) {
        openBrowser(player, 0);
    }

    public void openBrowser(Player player, int page) {
        if (plugin.geyserUtil().isBedrockPlayer(player) && openBrowserForm(player, page)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            BrowserInventory holder = new BrowserInventory(page);
            Inventory inventory = InventoryUtil.createInventory(holder, holder.size(), Component.text("CoinFlip Browser"));
            holder.populate(inventory, player);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    public void openCreateConfirm(Player player, double amount) {
        if (plugin.geyserUtil().isBedrockPlayer(player) && openCreateConfirmForm(player, amount)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConfirmCreateInventory holder = new ConfirmCreateInventory(amount);
            Inventory inventory = InventoryUtil.createInventory(holder, 27, Component.text("Confirm CoinFlip"));
            holder.populate(inventory, player);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    public void openAcceptConfirm(Player player, CoinFlipGame game) {
        if (plugin.geyserUtil().isBedrockPlayer(player) && openAcceptConfirmForm(player, game)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConfirmAcceptInventory holder = new ConfirmAcceptInventory(game.id());
            Inventory inventory = InventoryUtil.createInventory(holder, 27, Component.text("Accept CoinFlip"));
            holder.populate(inventory, player, game);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    public void openHelp(Player player, int returnPage) {
        if (plugin.geyserUtil().isBedrockPlayer(player) && openHelpForm(player, returnPage)) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            HelpInventory holder = new HelpInventory(returnPage, isAdmin(player));
            Inventory inventory = InventoryUtil.createInventory(holder, holder.size(), Component.text("CoinFlip Help"));
            holder.populate(inventory);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!(holder instanceof CoinFlipInventory gui)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getCurrentItem() == null) {
            return;
        }
        if (event.getClickedInventory() == event.getView().getBottomInventory()) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        gui.onClick(player, event.getSlot(), event.getClick(), event.getCurrentItem());
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof CoinFlipInventory gui) {
            gui.onClose((Player) event.getPlayer());
        }
    }

    private void playSound(Player player, org.bukkit.Sound sound) {
        Key key = Key.key(sound.getKey().getNamespace(), sound.getKey().getKey());
        plugin.audiences().player(player).playSound(Sound.sound(key, Sound.Source.MASTER, 1f, 1f));
    }

    private boolean openBrowserForm(Player player, int page) {
        List<CoinFlipGame> games = gameService.listPublicGames();
        CoinFlipConfig.BrowserSettings browser = plugin.config().ui().browser();
        int perPage = Math.max(1, browser.itemsPerPage());
        int start = page * perPage;
        int end = Math.min(start + perPage, games.size());

        List<CoinFlipGame> pageGames = new ArrayList<>();
        List<String> buttons = new ArrayList<>();
        String content;
        if (start >= games.size()) {
            content = "No active coinflips.";
        } else {
            content = "Select a coinflip to accept.";
            for (int i = start; i < end; i++) {
                CoinFlipGame game = games.get(i);
                pageGames.add(game);
                String creatorName = resolvePlayerName(game.creator());
                String amount = plugin.economyService().formatCurrency(game.amount());
                String remaining = TimeUtil.formatSecondsRemaining(game.expiresAt() - System.currentTimeMillis());
                buttons.add(creatorName + " - " + amount + " (" + remaining + ")");
            }
        }

        int prevIndex = -1;
        int helpIndex;
        int nextIndex = -1;
        if (page > 0) {
            prevIndex = buttons.size();
            buttons.add("Previous Page");
        }
        helpIndex = buttons.size();
        buttons.add("Help");
        int refreshIndex = buttons.size();
        buttons.add("Refresh");
        if (end < games.size()) {
            nextIndex = buttons.size();
            buttons.add("Next Page");
        }
        final int prevIndexFinal = prevIndex;
        final int helpIndexFinal = helpIndex;
        final int nextIndexFinal = nextIndex;
        final int refreshIndexFinal = refreshIndex;

        return plugin.geyserUtil().sendSimpleForm(player.getUniqueId(),
                "CoinFlip Browser",
                content,
                buttons,
                index -> {
                    if (!player.isOnline()) {
                        return;
                    }
                    if (index < pageGames.size()) {
                        openAcceptConfirm(player, pageGames.get(index));
                        return;
                    }
                    if (index == prevIndexFinal) {
                        openBrowser(player, page - 1);
                        return;
                    }
                    if (index == nextIndexFinal) {
                        openBrowser(player, page + 1);
                        return;
                    }
                    if (index == helpIndexFinal) {
                        openHelp(player, page);
                        return;
                    }
                    if (index == refreshIndexFinal) {
                        openBrowser(player, page);
                    }
                });
    }

    private boolean openCreateConfirmForm(Player player, double amount) {
        String amountText = plugin.economyService().formatCurrency(amount);
        return plugin.geyserUtil().sendModalForm(player.getUniqueId(),
                "Confirm CoinFlip",
                "Create a public coinflip for " + amountText + "?",
                "Confirm",
                "Cancel",
                index -> {
                    if (index == 0) {
                        if (gameService.createPublicGame(player, amount)) {
                            playSound(player, plugin.config().ui().sounds().open());
                        }
                    }
                });
    }

    private boolean openAcceptConfirmForm(Player player, CoinFlipGame game) {
        String creatorName = resolvePlayerName(game.creator());
        String amountText = plugin.economyService().formatCurrency(game.amount());
        return plugin.geyserUtil().sendModalForm(player.getUniqueId(),
                "Accept CoinFlip",
                "Accept coinflip vs " + creatorName + " for " + amountText + "?",
                "Accept",
                "Back",
                index -> {
                    if (index == 0) {
                        if (gameService.acceptPublic(player, game.id())) {
                            playSound(player, plugin.config().ui().sounds().accept());
                        }
                        return;
                    }
                    if (index == 1) {
                        openBrowser(player, 0);
                    }
                });
    }

    private boolean openHelpForm(Player player, int returnPage) {
        List<String> lines = HelpUtil.lines(isAdmin(player));
        String content = String.join("\n", lines);
        return plugin.geyserUtil().sendSimpleForm(player.getUniqueId(),
                "CoinFlip Help",
                content,
                List.of("Back"),
                index -> {
                    if (index == 0) {
                        openBrowser(player, returnPage);
                    }
                });
    }

    private String resolvePlayerName(UUID playerId) {
        OfflinePlayer creator = Bukkit.getOfflinePlayer(playerId);
        return creator.getName() != null ? creator.getName() : "Unknown";
    }

    private interface CoinFlipInventory extends InventoryHolder {
        void onClick(Player player, int slot, ClickType click, ItemStack item);

        default void onClose(Player player) {
        }
    }

    private final class BrowserInventory implements CoinFlipInventory {

        private final int page;

        BrowserInventory(int page) {
            this.page = Math.max(page, 0);
        }

        int size() {
            int rows = plugin.config().ui().browser().rows();
            return rows * 9;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        void populate(Inventory inventory, Player viewer) {
            inventory.clear();
            List<CoinFlipGame> games = gameService.listPublicGames();
            CoinFlipConfig.BrowserSettings browser = plugin.config().ui().browser();
            int perPage = Math.max(1, browser.itemsPerPage());
            int start = page * perPage;
            int end = Math.min(start + perPage, games.size());

            if (start >= games.size()) {
                ItemStack info = new ItemStack(Material.PAPER);
                ItemMeta meta = info.getItemMeta();
                ItemMetaUtil.displayName(meta, Component.text("No active coinflips", NamedTextColor.GRAY));
                info.setItemMeta(meta);
                inventory.setItem(13, info);
            } else {
                for (int index = start, slot = 0; index < end; index++, slot++) {
                    CoinFlipGame game = games.get(index);
                    ItemStack item = createListingItem(game);
                    inventory.setItem(slot, item);
                }
            }

            int size = size();
            ItemStack close = new ItemStack(Material.BARRIER);
            ItemMeta closeMeta = close.getItemMeta();
            ItemMetaUtil.displayName(closeMeta, Component.text("Close", NamedTextColor.RED));
            close.setItemMeta(closeMeta);
            inventory.setItem(size - 5, close);

            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prev.getItemMeta();
                ItemMetaUtil.displayName(prevMeta, Component.text("Previous Page", NamedTextColor.YELLOW));
                prev.setItemMeta(prevMeta);
                inventory.setItem(size - 6, prev);
            }

            if (end < games.size()) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                ItemMetaUtil.displayName(nextMeta, Component.text("Next Page", NamedTextColor.YELLOW));
                next.setItemMeta(nextMeta);
                inventory.setItem(size - 4, next);
            }

            ItemStack help = new ItemStack(Material.BOOK);
            ItemMeta helpMeta = help.getItemMeta();
            ItemMetaUtil.displayName(helpMeta, Component.text("Help", NamedTextColor.AQUA));
            ItemMetaUtil.lore(helpMeta, List.of(Component.text("View /cf commands", NamedTextColor.GRAY)));
            help.setItemMeta(helpMeta);
            inventory.setItem(size - 3, help);
        }

        private ItemStack createListingItem(CoinFlipGame game) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = skull.getItemMeta();
            OfflinePlayer creator = Bukkit.getOfflinePlayer(game.creator());
            String creatorName = creator.getName() != null ? creator.getName() : "Unknown";
            if (meta instanceof SkullMeta skullMeta && game.type() == GameType.PUBLIC) {
                skullMeta.setOwningPlayer(creator);
                meta = skullMeta;
            }
            ItemMetaUtil.displayName(meta, Component.text(creatorName, NamedTextColor.AQUA));

            long remaining = game.expiresAt() - System.currentTimeMillis();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Creator: " + creatorName, NamedTextColor.GRAY));
            lore.add(Component.text("Amount: " + plugin.economyService().economy().format(game.amount()), NamedTextColor.GREEN));
            lore.add(Component.text("Time Left: " + TimeUtil.formatSecondsRemaining(remaining), NamedTextColor.GOLD));
            ItemMetaUtil.lore(meta, lore);
            skull.setItemMeta(meta);
            return skull;
        }

        @Override
        public void onClick(Player player, int slot, ClickType click, ItemStack item) {
            CoinFlipConfig.BrowserSettings browser = plugin.config().ui().browser();
            int perPage = Math.max(1, browser.itemsPerPage());
            int size = size();
            int start = page * perPage;
            List<CoinFlipGame> games = gameService.listPublicGames();
            int end = Math.min(start + perPage, games.size());

            if (slot == size - 5) {
                player.closeInventory();
                return;
            }

            if (slot == size - 6 && page > 0) {
                openBrowser(player, page - 1);
                return;
            }

            if (slot == size - 4 && end < games.size()) {
                openBrowser(player, page + 1);
                return;
            }

            if (slot == size - 3) {
                openHelp(player, page);
                return;
            }

            int index = start + slot;
            if (index >= games.size() || index >= end) {
                return;
            }
            CoinFlipGame game = games.get(index);
            plugin.guiService().openAcceptConfirm(player, game);
        }
    }

    private final class HelpInventory implements CoinFlipInventory {

        private final int returnPage;
        private final boolean showAdmin;

        HelpInventory(int returnPage, boolean showAdmin) {
            this.returnPage = Math.max(returnPage, 0);
            this.showAdmin = showAdmin;
        }

        int size() {
            return 27;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        void populate(Inventory inventory) {
            inventory.clear();
            List<HelpUtil.HelpEntry> entries = HelpUtil.entries(showAdmin);
            int maxSlot = size() - 9;
            int slot = 0;
            for (HelpUtil.HelpEntry entry : entries) {
                if (slot >= maxSlot) {
                    break;
                }
                ItemStack item = new ItemStack(Material.PAPER);
                ItemMeta meta = item.getItemMeta();
                ItemMetaUtil.displayName(meta, Component.text(entry.command(), NamedTextColor.YELLOW));
                ItemMetaUtil.lore(meta, List.of(Component.text(entry.description(), NamedTextColor.GRAY)));
                item.setItemMeta(meta);
                inventory.setItem(slot, item);
                slot++;
            }

            ItemStack back = new ItemStack(Material.ARROW);
            ItemMeta backMeta = back.getItemMeta();
            ItemMetaUtil.displayName(backMeta, Component.text("Back", NamedTextColor.RED));
            back.setItemMeta(backMeta);
            inventory.setItem(size() - 5, back);
        }

        @Override
        public void onClick(Player player, int slot, ClickType click, ItemStack item) {
            if (slot == size() - 5) {
                openBrowser(player, returnPage);
            }
        }
    }

    private final class ConfirmCreateInventory implements CoinFlipInventory {

        private final double amount;

        ConfirmCreateInventory(double amount) {
            this.amount = amount;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        void populate(Inventory inventory, Player viewer) {
            ItemStack confirm = new ItemStack(Material.LIME_WOOL);
            ItemMeta confirmMeta = confirm.getItemMeta();
            ItemMetaUtil.displayName(confirmMeta, Component.text("Confirm", NamedTextColor.GREEN));
            ItemMetaUtil.lore(confirmMeta, List.of(
                    Component.text("Create public coinflip for " + plugin.economyService().economy().format(amount), NamedTextColor.GRAY)
            ));
            confirm.setItemMeta(confirmMeta);
            inventory.setItem(11, confirm);

            ItemStack cancel = new ItemStack(Material.RED_WOOL);
            ItemMeta cancelMeta = cancel.getItemMeta();
            ItemMetaUtil.displayName(cancelMeta, Component.text("Cancel", NamedTextColor.RED));
            cancel.setItemMeta(cancelMeta);
            inventory.setItem(15, cancel);
        }

        @Override
        public void onClick(Player player, int slot, ClickType click, ItemStack item) {
            if (slot == 11) {
                if (gameService.createPublicGame(player, amount)) {
                    player.closeInventory();
                }
                return;
            }
            if (slot == 15) {
                player.closeInventory();
            }
        }
    }

    private final class ConfirmAcceptInventory implements CoinFlipInventory {

        private final UUID gameId;

        ConfirmAcceptInventory(UUID gameId) {
            this.gameId = gameId;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }

        void populate(Inventory inventory, Player viewer, CoinFlipGame game) {
            String creatorName = Bukkit.getOfflinePlayer(game.creator()).getName();
            if (creatorName == null) {
                creatorName = "Unknown";
            }
            ItemStack accept = new ItemStack(Material.LIME_WOOL);
            ItemMeta acceptMeta = accept.getItemMeta();
            ItemMetaUtil.displayName(acceptMeta, Component.text("Accept", NamedTextColor.GREEN));
            ItemMetaUtil.lore(acceptMeta, List.of(
                    Component.text("Accept coinflip vs " + creatorName, NamedTextColor.GRAY),
                    Component.text("Amount: " + plugin.economyService().economy().format(game.amount()), NamedTextColor.GREEN)
            ));
            accept.setItemMeta(acceptMeta);
            inventory.setItem(11, accept);

            ItemStack back = new ItemStack(Material.RED_WOOL);
            ItemMeta backMeta = back.getItemMeta();
            ItemMetaUtil.displayName(backMeta, Component.text("Back", NamedTextColor.RED));
            back.setItemMeta(backMeta);
            inventory.setItem(15, back);
        }

        @Override
        public void onClick(Player player, int slot, ClickType click, ItemStack item) {
            if (slot == 11) {
                if (gameService.acceptPublic(player, gameId)) {
                    playSound(player, plugin.config().ui().sounds().accept());
                    player.closeInventory();
                }
                return;
            }
            if (slot == 15) {
                plugin.guiService().openBrowser(player, 0);
            }
        }
    }

    private boolean isAdmin(Player player) {
        return player.isOp() || player.hasPermission("coinflip.admin");
    }
}

