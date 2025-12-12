package de.galacticfy.galacticfyChat.command;

import de.galacticfy.galacticfyChat.spawn.BackService;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class BackCommand implements CommandExecutor {

    private final BackService backService;
    private final String serverId;

    public BackCommand(BackService backService, String serverId) {
        this.backService = backService;
        this.serverId = (serverId == null ? "" : serverId.toLowerCase());
    }

    private String prefix() { return "§8[§bGalacticfy§8] §r"; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cNur ingame.");
            return true;
        }

        if (serverId.startsWith("lobby")) {
            player.sendMessage(prefix() + "§c/back ist in der Lobby deaktiviert.");
            return true;
        }

        backService.startBack(player);
        return true;
    }
}
