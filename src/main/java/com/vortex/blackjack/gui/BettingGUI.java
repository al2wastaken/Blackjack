package com.vortex.blackjack.gui;

import com.vortex.blackjack.BlackjackPlugin;
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
        this.inventory = Bukkit.createInventory(this, 27, "§8Kumarhane Bahis Menüsü");
        buildInventory();
    }

    private void buildInventory() {
        ItemStack bg = createItem(Material.GRAY_STAINED_GLASS_PANE, "§7", null);
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, bg);
        }

        int minBet = table.getSettings().getMinBet(plugin.getConfigManager());
        int maxBet = table.getSettings().getMaxBet(plugin.getConfigManager());
        Integer currentBet = plugin.getPlayerBets().get(player);
        double balance = plugin.getEconomyProvider().getBalance(player.getUniqueId()).doubleValue();

        // Slot 4: Info Header
        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Masa Minimum: §f" + minBet + "₺");
        infoLore.add("§7Masa Maksimum: §f" + maxBet + "₺");
        infoLore.add("§7Bakiyeniz: §a" + String.format("%.2f", balance) + "₺");
        infoLore.add("");
        if (currentBet != null && currentBet > 0) {
            infoLore.add("§aŞu Anki Bahsiniz: §e" + currentBet + "₺");
            infoLore.add("§7(Yeni bir çip seçerek bahsinizi değiştirebilirsiniz)");
        } else {
            infoLore.add("§cHenüz bahis koymadınız.");
            infoLore.add("§7Aşağıdaki çiplerden birini seçin veya");
            infoLore.add("§7özel bir miktar belirleyin.");
        }
        infoLore.add("");
        infoLore.add("§8Kartlar dağıtılmadan önce [Space] ile");
        infoLore.add("§8bu menüyü tekrar açabilirsiniz.");

        ItemStack infoItem = createItem(Material.NETHER_STAR, "§6§lMasa ve Bahis Bilgisi", infoLore);
        inventory.setItem(4, infoItem);

        // Slots 10..15: Preset Chips
        for (int i = 0; i < CHIP_VALUES.length; i++) {
            int value = CHIP_VALUES[i];
            Material mat = CHIP_MATERIALS[i];

            List<String> chipLore = new ArrayList<>();
            chipLore.add("§7Değer: §a" + value + "₺");
            if (value < minBet) {
                chipLore.add("§c(Masa minimumu " + minBet + "₺)");
            } else if (value > maxBet) {
                chipLore.add("§c(Masa maksimumu " + maxBet + "₺)");
            } else {
                chipLore.add("§eTıkla: Bahis olarak " + value + "₺ yatır");
            }
            inventory.setItem(10 + i, createItem(mat, "§e§l" + value + "₺ Çip", chipLore));
        }

        // Slot 16: Custom Bet (SignGUI)
        List<String> customLore = new ArrayList<>();
        customLore.add("§7Kendi istediğiniz bahis miktarını");
        customLore.add("§7tabela arayüzüne yazmak için tıklayın.");
        customLore.add("");
        customLore.add("§eTıkla: Özel miktar gir");
        inventory.setItem(16, createItem(Material.OAK_SIGN, "§b§lÖzel Bahis (Custom Bet)", customLore));

        // Slot 22: Wait / Later
        List<String> waitLore = new ArrayList<>();
        waitLore.add("§7Şimdilik bahis koymadan masada bekleyin.");
        waitLore.add("§7Geri sayım bitmeden önce §e[Space] §7tuşuna");
        waitLore.add("§7basarak istediğiniz an bahis yapabilirsiniz.");
        waitLore.add("");
        waitLore.add("§c⚠ Not: Geri sayım bittiğinde hala bahis");
        waitLore.add("§cyapmadıysanız otomatik olarak minimum");
        waitLore.add("§cbahis (§e" + minBet + "₺§c) alınacaktır.");
        waitLore.add("");
        waitLore.add("§eTıkla: Menüyü kapat ve bekle");
        inventory.setItem(22, createItem(Material.CLOCK, "§e§lBekle (Sonra Belirle)", waitLore));
    }

    public void open() {
        player.openInventory(inventory);
    }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        int slot = event.getRawSlot();

        if (table.isGameInProgress()) {
            player.sendMessage("§cOyun başladıktan sonra bahis değiştirilemez!");
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
            player.sendMessage("§eMasada bekliyorsunuz. Geri sayım bitmeden önce §a[Space] §etuşuna basarak bahsinizi belirleyebilirsiniz.");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }
    }

    public void applyBet(int amount) {
        int minBet = table.getSettings().getMinBet(plugin.getConfigManager());
        int maxBet = table.getSettings().getMaxBet(plugin.getConfigManager());

        if (amount < minBet) {
            player.sendMessage("§cBu masada minimum bahis " + minBet + "₺!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        if (amount > maxBet) {
            player.sendMessage("§cBu masada maksimum bahis " + maxBet + "₺!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        Integer currentBet = plugin.getPlayerBets().get(player);
        int currentVal = (currentBet != null) ? currentBet : 0;

        // If player already placed a bet, calculate difference
        double balance = plugin.getEconomyProvider().getBalance(player.getUniqueId()).doubleValue();
        int needed = amount - currentVal;

        if (needed > 0 && balance < needed) {
            player.sendMessage("§cYetersiz bakiye! " + amount + "₺ yatırmak için " + needed + "₺ daha gereklidir.");
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

        player.sendMessage("§aBahsiniz §e" + amount + "₺ §aolarak yatırıldı! Kartların dağıtılması bekleniyor...");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
        player.closeInventory();
    }

    private void openCustomBetSign() {
        player.sendMessage("§eLütfen açılan tabelaya bahis miktarını yazın ve Tamam'a basın...");
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
                player.sendMessage("§cGeçersiz sayı girdiniz! Bahis ayarlanamadı.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            try {
                int amount = Integer.parseInt(input);
                applyBet(amount);
            } catch (NumberFormatException e) {
                player.sendMessage("§cGeçersiz miktar: " + input);
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
