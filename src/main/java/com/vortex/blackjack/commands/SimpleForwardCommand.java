package com.vortex.blackjack.commands;

import com.vortex.blackjack.BlackjackPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

/**
 * Simple command that forwards to the main plugin
 */
public class SimpleForwardCommand extends BlackjackCommand {
    private final String action;
    
    public SimpleForwardCommand(BlackjackPlugin plugin, String action) {
        super(plugin);
        this.action = action;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!isPlayer(sender)) {
            sendPlayerOnlyMessage(sender);
            return true;
        }
        
        // Forward to main plugin with action prepended
        String[] newArgs = new String[args.length + 1];
        newArgs[0] = action;
        System.arraycopy(args, 0, newArgs, 1, args.length);
        
        return plugin.onCommand(sender, command, label, newArgs);
    }
}
