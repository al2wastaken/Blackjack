package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.util.VersionChecker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Handle blackjack version output for checking plugin version.
 */
public class VersionCommand extends BlackjackCommand {
    private final VersionChecker versionChecker;
    
    public VersionCommand(BlackjackPlugin plugin, VersionChecker versionChecker) {
        this.versionChecker = versionChecker;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("blackjack.admin")) {
            sender.sendMessage("§cBu komutu kullanmak için yetkiniz yok.");
            return true;
        }
        
        sender.sendMessage("§6§l▬▬▬ BLACKJACK EKLENTİ SÜRÜM BİLGİSİ ▬▬▬");
        sender.sendMessage("");
        sender.sendMessage("§fEklenti: §aBlackjack");
        sender.sendMessage("§fGeliştirici: §bDefectiveVortex");
        sender.sendMessage("§fMevcut Sürüm: §a" + versionChecker.getCurrentVersion());
        
        if (versionChecker.getLatestVersion() != null) {
            sender.sendMessage("§fEn Son Sürüm: §a" + versionChecker.getLatestVersion());
        }
        
        sender.sendMessage("");
        
        if (versionChecker.isOutdated()) {
            sender.sendMessage("§c⚠ GÜNCELLEME MEVCUT!");
            sender.sendMessage("§bİndir: §9https://github.com/DefectiveVortex/Blackjack/releases/latest");
        } else {
            sender.sendMessage("§a✓ GÜNCEL!");
        }
        
        sender.sendMessage("");
        sender.sendMessage("§7GitHub: §9https://github.com/DefectiveVortex/Blackjack");
        
        return true;
    }
}
