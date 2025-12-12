package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.spawn.BackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.entity.Player;

public class BackListener implements Listener {

    private final BackService backService;

    public BackListener(BackService backService) {
        this.backService = backService;
    }

    /**
     * Back-Punkt bei Tod setzen
     */
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        backService.setBackLocation(player, player.getLocation());
    }

    /**
     * Back-Punkt bei absichtlichem Teleport setzen
     * (NICHT bei Join / Plugin-Spawn!)
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();

        // ❌ Join-Spawn ignorieren
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.PLUGIN) {
            return;
        }

        // ❌ World-Load / Login ignorieren
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            return;
        }

        // ✅ Back-Punkt = Position VOR dem Teleport
        backService.setBackLocation(player, event.getFrom());
    }
}
