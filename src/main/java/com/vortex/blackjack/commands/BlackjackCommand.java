package com.vortex.blackjack.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Base class for blackjack commands with common functionality
 */
public abstract class BlackjackCommand implements CommandExecutor, TabCompleter {
    protected final com.vortex.blackjack.BlackjackPlugin plugin;

    protected BlackjackCommand(com.vortex.blackjack.BlackjackPlugin plugin) {
        this.plugin = plugin;
    }
    
    protected boolean isPlayer(CommandSender sender) {
        return sender instanceof Player;
    }
    
    protected Player getPlayer(CommandSender sender) {
        if (sender instanceof Player) {
            return (Player) sender;
        }
        return null;
    }
    
    protected void sendPlayerOnlyMessage(CommandSender sender) {
        if (plugin != null && plugin.getConfigManager() != null) {
            sender.sendMessage(plugin.getConfigManager().getMessage("player-only-command"));
        } else {
            sender.sendMessage(org.bukkit.ChatColor.RED + "Only players can use this command.");
        }
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList(); // Override in subclasses if needed
    }
    
    /**
     * Helper method for tab completion with string matching
     */
    protected List<String> filterCompletions(List<String> completions, String partial) {
        List<String> result = new ArrayList<>();
        StringUtil.copyPartialMatches(partial, completions, result);
        Collections.sort(result);
        return result;
    }
}
