package de.galacticfy.galacticfyChat.npc;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NpcPosCommand implements CommandExecutor {

    private final GalacticfyChat plugin;
    private final NpcRepository repository;
    private final NpcManager npcManager;
    private final String serverName;

    public NpcPosCommand(GalacticfyChat plugin,
                         NpcRepository repository,
                         NpcManager npcManager,
                         String serverName) {
        this.plugin = plugin;
        this.repository = repository;
        this.npcManager = npcManager;
        this.serverName = serverName;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur ingame-Spieler können diesen Befehl verwenden.");
            return true;
        }

        if (!player.hasPermission("galacticfy.npc.pos")) {
            player.sendMessage("§cDazu hast du keine Berechtigung.");
            return true;
        }

        if (args.length < 2 || !args[0].equalsIgnoreCase("set")) {
            player.sendMessage("§eBenutzung: §b/npcpos set <id>");
            return true;
        }

        String id = args[1];

        Location loc = player.getLocation();
        String world = loc.getWorld().getName();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();
        float yaw = loc.getYaw();
        float pitch = loc.getPitch();

        boolean ok = repository.upsertPosition(
                id,
                serverName,
                world,
                x, y, z,
                yaw, pitch
        );

        if (ok) {
            player.sendMessage("§aPosition für NPC §e" + id +
                    " §aauf diesem Server gespeichert.");

            // NPCs neu laden (damit Änderung sofort sichtbar ist)
            npcManager.despawnAll();
            npcManager.loadAndSpawnAll();
        } else {
            player.sendMessage("§cFehler beim Speichern der NPC-Position.");
        }

        return true;
    }
}
