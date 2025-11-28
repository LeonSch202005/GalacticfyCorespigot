package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final GalacticfyChat plugin;

    public JoinQuitListener() {
        this.plugin = GalacticfyChat.getInstance();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();

        // Keine Join-Nachricht
        event.setJoinMessage(null);

        // Nametag + Scoreboard direkt setzen
        plugin.getRankService().updateNametag(p);
        plugin.getScoreboardService().updateBoard(p);

        // Kleines Welcome-Title + Sound

        // NPC-Fix: wenn dies der erste Spieler auf dem Server ist,
        // nach kurzer Zeit NPCs neu laden + spawnen.
        if (Bukkit.getOnlinePlayers().size() == 1) {
            Bukkit.getScheduler().runTaskLater(
                    plugin,
                    () -> {
                        if (plugin.getNpcManager() != null) {
                            plugin.getLogger().info("[GalacticfyChat] Erster Spieler join -> NPCs neu laden.");
                            plugin.getNpcManager().despawnAll();
                            plugin.getNpcManager().loadAndSpawnAll();
                        }
                    },
                    20L // 1 Sekunde Delay
            );
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keine Quit-Nachricht
        event.setQuitMessage(null);

        // Nametag / Teams aufräumen
        plugin.getRankService().clearNametag(event.getPlayer());
        // Scoreboard entfernen
        plugin.getScoreboardService().removeBoard(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Keine Todesnachricht
        event.deathMessage(null);
        // bei älteren Spigot-Versionen:
        // event.setDeathMessage(null);
    }
}
