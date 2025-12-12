package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.spawn.BackService;
import de.galacticfy.galacticfyChat.spawn.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

public class LobbyJoinSpawnListener implements Listener {

    private final Plugin plugin;
    private final SpawnService spawnService;
    private final BackService backService;
    private final Logger logger;

    public LobbyJoinSpawnListener(Plugin plugin, SpawnService spawnService, BackService backService) {
        this.plugin = plugin;
        this.spawnService = spawnService;
        this.backService = backService;
        this.logger = plugin.getLogger();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Location loc = spawnService.getSpawnLocation();
            if (loc == null) {
                logger.warning("[LobbyJoinSpawn] Kein Spawn gesetzt – Spieler bleibt an Join-Position.");
                return;
            }

            // IMPORTANT: Dieser Teleport soll KEIN Back-Punkt werden
            backService.suppressNextTeleportSave(player.getUniqueId());

            boolean ok = player.teleport(loc);
            logger.info("[LobbyJoinSpawn] Teleport " + player.getName() +
                    " -> " + loc.getWorld().getName() + " "
                    + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ()
                    + " | success=" + ok);
        }, 2L);
    }
}
