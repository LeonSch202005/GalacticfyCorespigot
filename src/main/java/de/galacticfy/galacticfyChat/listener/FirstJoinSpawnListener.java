package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.spawn.SpawnService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.logging.Logger;

public class FirstJoinSpawnListener implements Listener {

    private final SpawnService spawnService;
    private final Logger logger;

    public FirstJoinSpawnListener(SpawnService spawnService, Logger logger) {
        this.spawnService = spawnService;
        this.logger = logger;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Nur beim allerersten Join
        if (player.hasPlayedBefore()) {
            return;
        }

        boolean ok = spawnService.teleportToSpawn(player, false);
        if (!ok) {
            logger.fine("[FirstJoinSpawnListener] Kein Spawn für diesen Citybuild-Server gesetzt.");
        }
    }
}
