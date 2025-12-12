package de.galacticfy.galacticfyChat.command;

import de.galacticfy.galacticfyChat.spawn.SpawnService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SetSpawnCommand implements CommandExecutor {

    private final SpawnService spawnService;

    public SetSpawnCommand(SpawnService spawnService) {
        this.spawnService = spawnService;
    }

    private String prefix() {
        return "§8[§bGalacticfy§8] §r";
    }

    // -----------------------------
    // Permission-Helfer (wie bei /gamemode)
    // -----------------------------

    private boolean isStar(CommandSender sender) {
        // Spieler mit '*' oder galacticfy.* dürfen immer
        return sender.hasPermission("*") || sender.hasPermission("galacticfy.*");
    }

    private boolean hasSetSpawnPermission(CommandSender sender) {
        return isStar(sender)
                || sender.hasPermission("galacticfy.setspawn")
                || sender.hasPermission("galacticfy.admin");
    }


    // -----------------------------
    // onCommand
    // -----------------------------

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cDiesen Befehl können nur Spieler ausführen.");
            return true;
        }

        if (!hasSetSpawnPermission(sender)) {
            sender.sendMessage(prefix() + "§cDazu hast du keine Berechtigung.");
            return true;
        }

        Location loc = player.getLocation();
        spawnService.setSpawnLocation(loc);

        player.sendMessage(prefix() + "§aSpawn für diesen Server wurde auf deine aktuelle Position gesetzt.");
        return true;
    }
}
