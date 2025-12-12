package de.galacticfy.galacticfyChat.command;

import de.galacticfy.galacticfyChat.spawn.BackService;
import de.galacticfy.galacticfyChat.spawn.SpawnService;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class SpawnCommand implements CommandExecutor {

    private final SpawnService spawnService;
    private final BackService backService;

    private String prefix() { return "§8[§bGalacticfy§8] §r"; }

    public SpawnCommand(SpawnService spawnService, BackService backService) {
        this.spawnService = spawnService;
        this.backService = backService;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage(prefix() + "§cDiesen Befehl kannst du nur ingame ausführen.");
            return true;
        }

        Location spawn = spawnService.getSpawnLocation();
        if (spawn == null) {
            player.sendMessage(prefix() + "§cEs ist kein Spawn für diesen Server gesetzt.");
            return true;
        }

        // Back-Punkt = Position VOR dem Teleport
        backService.setBackLocation(player, player.getLocation());

        // Teleport-Event soll NICHT nochmal als Back-Punkt gespeichert werden (nur 1x unterdrücken)
        backService.suppressNextTeleportSave(player.getUniqueId());

        boolean ok = player.teleport(spawn);
        if (ok) {
            player.sendMessage(prefix() + "§aDu wurdest zum Spawn teleportiert.");
        } else {
            player.sendMessage(prefix() + "§cTeleport zum Spawn ist fehlgeschlagen.");
        }

        return true;
    }
}
