package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * In-game betting menu shown when sitting at a Blackjack table or pressing Space.
 * Offers preset chips, custom SignGUI bet entry, and a wait/decide later button.
 */
public class BettingGUI implements InventoryHolder {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final Player player;
    private final ConfigManager configManager;
    private final Inventory inventory;

    private static final int[] CHIP_VALUES = {10, 25, 50, 100, 250, 500};
    private static final Material[] CHIP_MATERIALS = {
            Material.SUNFLOWER,
            Material.IRON_INGOT,
            Material.COPPER_INGOT,
            Material.GOLD_INGOT,
            Material.DIAMOND,
            Material.EMERALD
    };

    public BettingGUI(BlackjackPlugin plugin, BlackjackTable table, Player player) {
        this.plugin = plugin;
        this.table = table;
        this.player = player;
        this.configManager = plugin.getConfigManager();
        this.inventory = Bukkit.createInventory(this, 27, configManager.getMessage("betting-gui.title"));
        buildInventory();
    }

    private void buildInventory() {
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        int minBet = table.getSettings().getMinBet(configManager);
        int maxBet = table.getSettings().getMaxBet(configManager);
        Integer currentBet = plugin.getPlayerBets().get(player);
        double balance = plugin.getEconomyProvider().getBalance(player.getUniqueId()).doubleValue();

        // Slot 4: Info Header
        List<String> infoLore = new ArrayList<>();
        infoLore.add(configManager.formatMessage("betting-gui.info-item.lore-min-bet", "min_bet", minBet));
        infoLore.add(configManager.formatMessage("betting-gui.info-item.lore-max-bet", "max_bet", maxBet));
        infoLore.add(configManager.formatMessage("betting-gui.info-item.lore-balance", "balance", String.format("%.2f", balance)));
        infoLore.add("");
        if (currentBet != null && currentBet > 0) {
            infoLore.add(configManager.formatMessage("betting-gui.info-item.lore-current-bet", "current_bet", currentBet));
            infoLore.add(configManager.getMessage("betting-gui.info-item.lore-change-bet"));
        } else {
            infoLore.add(configManager.getMessage("betting-gui.info-item.lore-no-bet"));
            infoLore.add(configManager.getMessage("betting-gui.info-item.lore-select-chip-1"));
            infoLore.add(configManager.getMessage("betting-gui.info-item.lore-select-chip-2"));
        }
        infoLore.add("");
        infoLore.add(configManager.getMessage("betting-gui.info-item.lore-footer-1"));
        infoLore.add(configManager.getMessage("betting-gui.info-item.lore-footer-2"));

        ItemStack infoItem = createItem(Material.NETHER_STAR, configManager.getMessage("betting-gui.info-item.name"), infoLore);
        inventory.setItem(4, infoItem);

        // Slots 10..15: Preset Chips
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            int value = CHIP_VALUES[i];
            Material mat = CHIP_MATERIALS[i];

            List<String> chipLore = new ArrayList<>();
            chipLore.add(configManager.formatMessage("betting-gui.chip.lore-value", "amount", value));
            if (value < minBet) {
                chipLore.add(configManager.formatMessage("betting-gui.chip.lore-min-limit", "min_bet", minBet));
            } else if (value > maxBet) {
                chipLore.add(configManager.formatMessage("betting-gui.chip.lore-max-limit", "max_bet", maxBet));
            } else {
                chipLore.add(configManager.formatMessage("betting-gui.chip.lore-click", "amount", value));
            }
            inventory.setItem(10 + i, createItem(mat, configManager.formatMessage("betting-gui.chip.name", "amount", value), chipLore));
        }

        // Slot 16: Custom Bet (SignGUI)
        List<String> customLore = new ArrayList<>();
        customLore.add(configManager.getMessage("betting-gui.custom-bet.lore-1"));
        customLore.add(configManager.getMessage("betting-gui.custom-bet.lore-2"));
        customLore.add("");
        customLore.add(configManager.getMessage("betting-gui.custom-bet.lore-click"));
        inventory.setItem(16, createItem(Material.OAK_SIGN, configManager.getMessage("betting-gui.custom-bet.name"), customLore));

        // Slot 22: Wait / Later
        List<String> waitLore = new ArrayList<>();
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-1"));
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-2"));
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-3"));
        waitLore.add("");
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-warn-1"));
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-warn-2"));
        waitLore.add(configManager.formatMessage("betting-gui.wait-button.lore-warn-3", "min_bet", minBet));
        waitLore.add("");
        waitLore.add(configManager.getMessage("betting-gui.wait-button.lore-click"));
        inventory.setItem(22, createItem(Material.CLOCK, configManager.getMessage("betting-gui.wait-button.name"), waitLore));
    }

    public void open() {
        if (table.isBettingLocked() || table.isGameInProgress() || table.hasCardsBeenDealt()) {
            player.sendMessage(configManager.getMessage("betting-gui.messages.game-in-progress"));
            return;
        }
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (table.isBettingLocked() || table.isGameInProgress() || table.hasCardsBeenDealt()) {
            player.sendMessage(configManager.getMessage("betting-gui.messages.game-in-progress"));
            player.closeInventory();
            return;
        }

        // Clicked a preset chip (slots 10..15)
        if (slot >= 10 && slot <= 15) {
            int index = slot - 10;
            int chipVal = CHIP_VALUES[index];
            applyBet(chipVal);
            return;
        }

        // Clicked Custom Bet (slot 16)
        if (slot == 16) {
            player.closeInventory();
            openCustomBetSign();
            return;
        }

        // Clicked Wait (slot 22)
        if (slot == 22) {
            player.closeInventory();
            player.sendMessage(configManager.getMessage("betting-gui.messages.waiting"));
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    public void applyBet(int amount) {
        if (table.isBettingLocked() || table.isGameInProgress() || table.hasCardsBeenDealt()) {
            player.sendMessage(configManager.getMessage("betting-gui.messages.game-in-progress"));
            player.closeInventory();
            return;
        }

        int minBet = table.getSettings().getMinBet(configManager);
        int maxBet = table.getSettings().getMaxBet(configManager);

        if (amount < minBet) {
            player.sendMessage(configManager.formatMessage("betting-gui.messages.min-bet-error", "min_bet", minBet));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (amount > maxBet) {
            player.sendMessage(configManager.formatMessage("betting-gui.messages.max-bet-error", "max_bet", maxBet));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Integer currentBet = plugin.getPlayerBets().get(player);
        int currentVal = (currentBet != null) ? currentBet : 0;

        // If player already placed a bet, calculate difference
        double balance = plugin.getEconomyProvider().getBalance(player.getUniqueId()).doubleValue();
        int needed = amount - currentVal;

        if (needed > 0 && balance < needed) {
            player.sendMessage(configManager.formatMessage("betting-gui.messages.insufficient-funds", "amount", amount, "needed", needed));
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        // Adjust economy
        if (currentVal > 0) {
            plugin.getEconomyProvider().add(player.getUniqueId(), BigDecimal.valueOf(currentVal));
        }
        plugin.getEconomyProvider().subtract(player.getUniqueId(), BigDecimal.valueOf(amount));

        // Save bet
        plugin.getPlayerBets().put(player, amount);
        plugin.getPlayerPersistentBets().put(player, amount);
        table.setPlayerRoundBet(player, amount);

        player.sendMessage(configManager.formatMessage("betting-gui.messages.bet-placed", "amount", amount));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        player.closeInventory();
    }

    private void openCustomBetSign() {
        player.sendMessage(configManager.getMessage("betting-gui.messages.custom-bet-prompt"));
        SignGUI.open(plugin, player, lines -> {
            if (!player.isOnline()) return;

            String input = "";
            for (String line : lines) {
                String clean = ChatColor.stripColor(line).trim();
                if (!clean.isEmpty()) {
                    input = clean;
                    break;
                }
            }

            // Strip any currency signs or letters
            input = input.replaceAll("[^0-9]", "");
            if (input.isEmpty()) {
                player.sendMessage(configManager.getMessage("betting-gui.messages.invalid-number"));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            try {
                int amount = Integer.parseInt(input);
                applyBet(amount);
            } catch (NumberFormatException e) {
                player.sendMessage(configManager.formatMessage("betting-gui.messages.invalid-amount", "input", input));
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        });
    }

    private ItemStack createItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore != null) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
