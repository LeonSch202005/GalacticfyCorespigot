package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.spawn.BackService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class BackResetOnJoinListener implements Listener {

    private final BackService backService;

    public BackResetOnJoinListener(BackService backService) {
        this.backService = backService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // 1) alter Back-Punkt weg
        backService.clearBackLocation(e.getPlayer());

        // 2) nächster Teleport (Join->Spawn) soll NICHT als Back gespeichert werden
        backService.suppressNextTeleportSave(e.getPlayer().getUniqueId());
    }
}
