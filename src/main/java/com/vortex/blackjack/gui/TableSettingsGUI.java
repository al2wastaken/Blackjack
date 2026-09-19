package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.croupier.CroupierSkin;
import com.vortex.blackjack.table.BlackjackTable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin configuration GUI opened by Shift + Right-clicking a Blackjack Table or Croupier.
 * Allows customizing Croupier skin, min/max bets, countdown seconds, and table felt material.
 */
public class TableSettingsGUI implements InventoryHolder {

    private final BlackjackPlugin plugin;
    private final BlackjackTable table;
    private final Player player;
    private final Inventory inventory;

    private static final String[] PRESET_SKIN_KEYS = {"classic", "lady", "mafia", "casual"};
    private static final String[] PRESET_SKIN_NAMES = {"Klasik Smokin", "Leydi Krupiye", "Mafya Babası", "Günlük Krupiye"};
    private static final int[] COUNTDOWN_OPTIONS = {5, 10, 15, 20, 30};
    private static final Material[] FELT_MATERIALS = {
            Material.GREEN_WOOL,
            Material.RED_WOOL,
            Material.BLUE_WOOL,
            Material.BLACK_WOOL,
            Material.PURPLE_WOOL,
            Material.LIME_WOOL,
            Material.CYAN_WOOL
    };
    private static final String[] FELT_NAMES = {"Yeşil", "Kırmızı", "Mavi", "Siyah", "Mor", "Açık Yeşil", "Camgöbeği"};

    public TableSettingsGUI(BlackjackPlugin plugin, BlackjackTable table, Player player) {
        this.plugin = plugin;
        this.table = table;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, "§8Masa ve Krupiye Ayarları");
        buildInventory();
    }

    public void buildInventory() {
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        // Slot 10: Croupier Skin
        String currentSkin = table.getSettings().getCroupierSkin();
        String skinDisplay = getSkinDisplayName(currentSkin);
        List<String> skinLore = new ArrayList<>();
        skinLore.add("§7Mevcut Skin: §e" + skinDisplay);
        skinLore.add("");
        skinLore.add("§aSol Tık: §7Sonraki hazır skine geç");
        skinLore.add("§cSağ Tık: §7Önceki hazır skine geç");
        inventory.setItem(10, createItem(Material.PLAYER_HEAD, "§6§lKrupiye Skini", skinLore));

        // Slot 12: Min Bet
        int minBet = table.getSettings().getMinBet(plugin.getConfigManager());
        List<String> minLore = new ArrayList<>();
        minLore.add("§7Mevcut Minimum: §e" + minBet + "₺");
        minLore.add("");
        minLore.add("§aSol Tık: §7+10₺ artır");
        minLore.add("§cSağ Tık: §7-10₺ azalt");
        minLore.add("§eShift + Sol Tık: §7+50₺ artır");
        minLore.add("§eShift + Sağ Tık: §7-50₺ azalt");
        inventory.setItem(12, createItem(Material.GOLD_NUGGET, "§e§lMinimum Bahis", minLore));

        // Slot 13: Max Bet
        int maxBet = table.getSettings().getMaxBet(plugin.getConfigManager());
        List<String> maxLore = new ArrayList<>();
        maxLore.add("§7Mevcut Maksimum: §e" + maxBet + "₺");
        maxLore.add("");
        maxLore.add("§aSol Tık: §7+100₺ artır");
        maxLore.add("§cSağ Tık: §7-100₺ azalt");
        maxLore.add("§eShift + Sol Tık: §7+500₺ artır");
        maxLore.add("§eShift + Sağ Tık: §7-500₺ azalt");
        inventory.setItem(13, createItem(Material.GOLD_INGOT, "§6§lMaksimum Bahis", maxLore));

        // Slot 14: Countdown Duration
        int countdown = table.getSettings().getCountdownSeconds();
        List<String> countLore = new ArrayList<>();
        countLore.add("§7Mevcut Süre: §e" + countdown + " saniye");
        countLore.add("");
        countLore.add("§aSol Tık: §7Süreyi değiştir (5, 10, 15, 20, 30 sn)");
        inventory.setItem(14, createItem(Material.CLOCK, "§b§lGeri Sayım Süresi", countLore));

        // Slot 16: Table Felt Material / Color
        Material currentFelt = table.getSettings().getFeltMaterial();
        String feltDisplay = getFeltDisplayName(currentFelt);
        List<String> feltLore = new ArrayList<>();
        feltLore.add("§7Mevcut Renk: §e" + feltDisplay);
        feltLore.add("");
        feltLore.add("§aSol Tık: §7Sonraki kumaş rengine geç");
        feltLore.add("§cSağ Tık: §7Önceki kumaş rengine geç");
        inventory.setItem(16, createItem(currentFelt, "§a§lMasa Örtüsü Kumaşı", feltLore));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();
        ClickType click = event.getClick();

        // 1. Croupier Skin (Slot 10)
        if (slot == 10) {
            String current = table.getSettings().getCroupierSkin();
            int idx = findSkinIndex(current);
            if (click.isLeftClick()) {
                idx = (idx + 1) % PRESET_SKIN_KEYS.length;
            } else if (click.isRightClick()) {
                idx = (idx - 1 + PRESET_SKIN_KEYS.length) % PRESET_SKIN_KEYS.length;
            }
            String newKey = PRESET_SKIN_KEYS[idx];
            String newTexture = CroupierSkin.getPresetOrDefault(newKey);
            table.getSettings().setCroupierSkin(newKey);
            if (table.getCroupierNPC() != null) {
                table.getCroupierNPC().setSkinTexture(newTexture);
            }
            saveTable();
            buildInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // 2. Min Bet (Slot 12)
        if (slot == 12) {
            int current = table.getSettings().getMinBet(plugin.getConfigManager());
            int step = click.isShiftClick() ? 50 : 10;
            if (click.isLeftClick()) {
                current += step;
            } else if (click.isRightClick()) {
                current = Math.max(1, current - step);
            }
            int max = table.getSettings().getMaxBet(plugin.getConfigManager());
            if (current > max) current = max;

            table.getSettings().setMinBet(current);
            saveTable();
            buildInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // 3. Max Bet (Slot 13)
        if (slot == 13) {
            int current = table.getSettings().getMaxBet(plugin.getConfigManager());
            int step = click.isShiftClick() ? 500 : 100;
            if (click.isLeftClick()) {
                current += step;
            } else if (click.isRightClick()) {
                current = Math.max(table.getSettings().getMinBet(plugin.getConfigManager()), current - step);
            }
            table.getSettings().setMaxBet(current);
            saveTable();
            buildInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // 4. Countdown (Slot 14)
        if (slot == 14) {
            int current = table.getSettings().getCountdownSeconds();
            int idx = 0;
            for (int i = 0; i < COUNTDOWN_OPTIONS.length; i++) {
                if (COUNTDOWN_OPTIONS[i] == current) {
                    idx = i;
                    break;
                }
            }
            idx = (idx + 1) % COUNTDOWN_OPTIONS.length;
            table.getSettings().setCountdownSeconds(COUNTDOWN_OPTIONS[idx]);
            saveTable();
            buildInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            return;
        }

        // 5. Felt Color (Slot 16)
        if (slot == 16) {
            Material current = table.getSettings().getFeltMaterial();
            int idx = 0;
            for (int i = 0; i < FELT_MATERIALS.length; i++) {
                if (FELT_MATERIALS[i] == current) {
                    idx = i;
                    break;
                }
            }
            if (click.isLeftClick()) {
                idx = (idx + 1) % FELT_MATERIALS.length;
            } else if (click.isRightClick()) {
                idx = (idx - 1 + FELT_MATERIALS.length) % FELT_MATERIALS.length;
            }
            Material newFelt = FELT_MATERIALS[idx];
            table.getSettings().setFeltMaterial(newFelt);
            table.updateFeltMaterial(newFelt);
            saveTable();
            buildInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    private void saveTable() {
        plugin.getTableManager().saveTable(table);
    }

    private int findSkinIndex(String key) {
        if (key == null) return 0;
        for (int i = 0; i < PRESET_SKIN_KEYS.length; i++) {
            if (PRESET_SKIN_KEYS[i].equalsIgnoreCase(key)) return i;
        }
        return 0;
    }

    private String getSkinDisplayName(String key) {
        int idx = findSkinIndex(key);
        return PRESET_SKIN_NAMES[idx];
    }

    private String getFeltDisplayName(Material mat) {
        for (int i = 0; i < FELT_MATERIALS.length; i++) {
            if (FELT_MATERIALS[i] == mat) return FELT_NAMES[i];
        }
        return mat.name();
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
