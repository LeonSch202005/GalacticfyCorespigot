package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.GalacticfyChat;
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
        // Keine Join-Nachricht
        event.setJoinMessage(null);

        // Nametag + Scoreboard direkt setzen
        plugin.getRankService().updateNametag(event.getPlayer());
        plugin.getScoreboardService().updateBoard(event.getPlayer());
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
