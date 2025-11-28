package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import de.galacticfy.galacticfyChat.npc.NpcManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class JoinQuitListener implements Listener {

    private final GalacticfyChat plugin;
    private final NpcManager npcManager;

    // Damit NPCs nur EINMAL nachgeladen werden
    private boolean npcSpawnedOnce = false;

    public JoinQuitListener() {
        this.plugin = GalacticfyChat.getInstance();
        this.npcManager = plugin.getNpcManager();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        // Keine Join-Meldung
        event.setJoinMessage(null);

        // Nametag + Scoreboard
        plugin.getRankService().updateNametag(event.getPlayer());
        plugin.getScoreboardService().updateBoard(event.getPlayer());

        // ===============================
        //  NPCs sicher nachladen
        // ===============================
        if (!npcSpawnedOnce) {
            npcSpawnedOnce = true;

            // NPCs neu laden und LookTask starten
            npcManager.loadAndSpawnAll();
            npcManager.startLookTask();

            plugin.getLogger().info("[GalacticfyChat] NPCs wurden beim ersten Join neu geladen.");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Keine Quit-Nachricht
        event.setQuitMessage(null);

        // Nametag aufräumen
        plugin.getRankService().clearNametag(event.getPlayer());

        // Scoreboard entfernen
        plugin.getScoreboardService().removeBoard(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        // Keine Todesnachricht
        event.deathMessage(null);
    }
}
