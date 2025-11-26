package com.yourorg.coinflip.gui;

import com.yourorg.coinflip.CoinFlipPlugin;
import com.yourorg.coinflip.config.CoinFlipConfig;
import com.yourorg.coinflip.game.CoinFlipGame;
import com.yourorg.coinflip.game.GameService;
import com.yourorg.coinflip.game.GameType;
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
        Bukkit.getScheduler().runTask(plugin, () -> {
            BrowserInventory holder = new BrowserInventory(page);
            Inventory inventory = Bukkit.createInventory(holder, holder.size(), Component.text("CoinFlip Browser"));
            holder.populate(inventory, player);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    public void openCreateConfirm(Player player, double amount) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConfirmCreateInventory holder = new ConfirmCreateInventory(amount);
            Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Confirm CoinFlip"));
            holder.populate(inventory, player);
            player.openInventory(inventory);
            playSound(player, plugin.config().ui().sounds().open());
        });
    }

    public void openAcceptConfirm(Player player, CoinFlipGame game) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            ConfirmAcceptInventory holder = new ConfirmAcceptInventory(game.id());
            Inventory inventory = Bukkit.createInventory(holder, 27, Component.text("Accept CoinFlip"));
            holder.populate(inventory, player, game);
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
                meta.displayName(Component.text("No active coinflips", NamedTextColor.GRAY));
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
            closeMeta.displayName(Component.text("Close", NamedTextColor.RED));
            close.setItemMeta(closeMeta);
            inventory.setItem(size - 5, close);

            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prev.getItemMeta();
                prevMeta.displayName(Component.text("Previous Page", NamedTextColor.YELLOW));
                prev.setItemMeta(prevMeta);
                inventory.setItem(size - 6, prev);
            }

            if (end < games.size()) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                nextMeta.displayName(Component.text("Next Page", NamedTextColor.YELLOW));
                next.setItemMeta(nextMeta);
                inventory.setItem(size - 4, next);
            }
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
            meta.displayName(Component.text(creatorName, NamedTextColor.AQUA));

            long remaining = game.expiresAt() - System.currentTimeMillis();
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Creator: " + creatorName, NamedTextColor.GRAY));
            lore.add(Component.text("Amount: " + plugin.economyService().economy().format(game.amount()), NamedTextColor.GREEN));
            lore.add(Component.text("Time Left: " + TimeUtil.formatSecondsRemaining(remaining), NamedTextColor.GOLD));
            meta.lore(lore);
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

            int index = start + slot;
            if (index >= games.size() || index >= end) {
                return;
            }
            CoinFlipGame game = games.get(index);
            plugin.guiService().openAcceptConfirm(player, game);
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
            confirmMeta.displayName(Component.text("Confirm", NamedTextColor.GREEN));
            confirmMeta.lore(List.of(
                    Component.text("Create public coinflip for " + plugin.economyService().economy().format(amount), NamedTextColor.GRAY)
            ));
            confirm.setItemMeta(confirmMeta);
            inventory.setItem(11, confirm);

            ItemStack cancel = new ItemStack(Material.RED_WOOL);
            ItemMeta cancelMeta = cancel.getItemMeta();
            cancelMeta.displayName(Component.text("Cancel", NamedTextColor.RED));
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
            acceptMeta.displayName(Component.text("Accept", NamedTextColor.GREEN));
            acceptMeta.lore(List.of(
                    Component.text("Accept coinflip vs " + creatorName, NamedTextColor.GRAY),
                    Component.text("Amount: " + plugin.economyService().economy().format(game.amount()), NamedTextColor.GREEN)
            ));
            accept.setItemMeta(acceptMeta);
            inventory.setItem(11, accept);

            ItemStack back = new ItemStack(Material.RED_WOOL);
            ItemMeta backMeta = back.getItemMeta();
            backMeta.displayName(Component.text("Back", NamedTextColor.RED));
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
}

