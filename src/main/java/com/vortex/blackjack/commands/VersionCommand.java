package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import com.vortex.blackjack.config.ConfigManager;
import com.vortex.blackjack.util.VersionChecker;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Handle blackjack version output for checking plugin version.
 */
public class VersionCommand extends BlackjackCommand {
    private final VersionChecker versionChecker;
    
    public VersionCommand(BlackjackPlugin plugin, VersionChecker versionChecker) {
        super(plugin);
        this.versionChecker = versionChecker;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        ConfigManager cfg = plugin.getConfigManager();

        if (!sender.hasPermission("blackjack.admin")) {
            sender.sendMessage(cfg.getMessage("version-info.no-permission"));
            return true;
        }
        
        sender.sendMessage(cfg.getMessage("version-info.header"));
        sender.sendMessage("");
        sender.sendMessage(cfg.getMessage("version-info.plugin"));
        sender.sendMessage(cfg.getMessage("version-info.developer"));
        sender.sendMessage(cfg.formatMessage("version-info.current-version", "version", versionChecker.getCurrentVersion()));
        
        if (versionChecker.getLatestVersion() != null) {
            sender.sendMessage(cfg.formatMessage("version-info.latest-version", "version", versionChecker.getLatestVersion()));
        }
        
        sender.sendMessage("");
        
        if (versionChecker.isOutdated()) {
            sender.sendMessage(cfg.getMessage("version-info.update-available"));
            sender.sendMessage(cfg.formatMessage("version-info.download-link", "url", "https://github.com/" + versionChecker.getGitHubRepo() + "/releases/latest"));
        } else {
            sender.sendMessage(cfg.getMessage("version-info.up-to-date"));
        }
        
        sender.sendMessage("");
        sender.sendMessage(cfg.formatMessage("version-info.github", "url", "https://github.com/" + versionChecker.getGitHubRepo()));
        
        return true;
    }
}
