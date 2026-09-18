package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.chair.BlackjackChair;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.listener.TableInteractListener;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.List;

/**
 * On-screen Confirmation GUI shown when a player sitting at a table with an active bet
 * attempts to dismount (Shift) or leave the table.
 *
 * All messages, item names, and lores are configurable via messages.yml.
 */
public class LeaveConfirmGUI implements InventoryHolder {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final Player player;
    private final int betAmount;
    private final ConfigManager configManager;
    private final Inventory inventory;

    private boolean actionTaken = false;

    public LeaveConfirmGUI(BlackjackPlugin plugin, BlackjackTable table, Player player, int betAmount) {
        this.plugin = plugin;
        this.table = table;
        this.player = player;
        this.betAmount = betAmount;
        this.configManager = plugin.getConfigManager();

        String title = configManager.formatMessage("confirm-leave-gui.title", "amount", betAmount);
        if (title.length() > 32) {
            title = configManager.getMessage("confirm-leave-gui.title-short");
        }
        this.inventory = Bukkit.createInventory(this, 9, title);
        setupItems();
    }

    private void setupItems() {
        List<String> defaultConfirmLore = Arrays.asList(
                "&7Sandalyeden kalkmayı onaylayın.",
                "",
                "&c&l⚠ UYARI: &e$%amount%",
                "&ctutarındaki bahsiniz &nİADE EDİLMEYECEK!",
                "&4Paranız masada kalır ve yanar.",
                "",
                "&eTıkla: Masadan ayrıl (Bahis yanar)"
        );
        List<String> confirmLore = configManager.formatMessageList("confirm-leave-gui.confirm-button.lore", defaultConfirmLore, "amount", betAmount);
        ItemStack confirmItem = createItem(
                Material.LIME_CONCRETE,
                configManager.getMessage("confirm-leave-gui.confirm-button.name"),
                confirmLore
        );

        List<String> defaultWarningLore = Arrays.asList(
                "&7Şu anki bahsiniz: &e$%amount%",
                "&7Masa: &f%table%",
                "",
                "&cKalkarsanız bu para masada kalır!",
                "&eAyrılmak için yeşile, masada kalmak için kırmızıya tıklayın."
        );
        List<String> warningLore = configManager.formatMessageList("confirm-leave-gui.warning-item.lore", defaultWarningLore, "amount", betAmount, "table", table.getTableId());
        ItemStack warningItem = createItem(
                Material.BARRIER,
                configManager.getMessage("confirm-leave-gui.warning-item.name"),
                warningLore
        );

        List<String> defaultCancelLore = Arrays.asList(
                "&7Sandalyede kalmaya devam et.",
                "",
                "&aBahsiniz (&e$%amount%&a) korunur.",
                "",
                "&eTıkla: Masada kal"
        );
        List<String> cancelLore = configManager.formatMessageList("confirm-leave-gui.cancel-button.lore", defaultCancelLore, "amount", betAmount);
        ItemStack cancelItem = createItem(
                Material.RED_CONCRETE,
                configManager.getMessage("confirm-leave-gui.cancel-button.name"),
                cancelLore
        );

        for (int i = 0; i < 4; i++) {
            inventory.setItem(i, confirmItem);
        }
        inventory.setItem(4, warningItem);
        for (int i = 5; i < 9; i++) {
            inventory.setItem(i, cancelItem);
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open() {
        player.openInventory(inventory);
        player.sendTitle(
                configManager.formatMessage("confirm-leave-gui.open-title", "amount", betAmount),
                configManager.formatMessage("confirm-leave-gui.open-subtitle", "amount", betAmount),
                5, 45, 10
        );
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
        player.sendMessage(configManager.formatMessage("confirm-leave-gui.open-warn-message", "amount", betAmount));
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (actionTaken) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < 4) {
            actionTaken = true;
            player.closeInventory();
            confirmLeave();
        } else if (rawSlot >= 5 && rawSlot < 9) {
            actionTaken = true;
            player.closeInventory();
            stayAtTable();
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (!actionTaken) {
            actionTaken = true;
            stayAtTable();
        }
    }

    private void confirmLeave() {
        TableInteractListener.markConfirmedLeave(player.getUniqueId());

        String reason = configManager.getLeaveReason("forfeited");
        table.removePlayer(player, reason, true);
        plugin.getPlayerPersistentBets().remove(player);

        player.sendTitle(
                configManager.formatMessage("confirm-leave-gui.forfeit-title", "amount", betAmount),
                configManager.formatMessage("confirm-leave-gui.forfeit-subtitle", "amount", betAmount),
                5, 40, 10
        );
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
    }

    private void stayAtTable() {
        BlackjackChair chair = table.getChairForPlayer(player);
        if (chair != null && !chair.isOccupied()) {
            chair.sit(player);
        }

        player.sendMessage(configManager.formatMessage("confirm-leave-gui.stay-message", "amount", betAmount));
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Player getPlayer() {
        return player;
    }

    public int getBetAmount() {
        return betAmount;
    }
}
