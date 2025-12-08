package de.galacticfy.galacticfyChat.quest;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Sendet Quest-Statistiken und Community-Events
 * vom Spigot-Server zum Velocity-Proxy.
 *
 * Channels:
 *   - "galacticfy:queststats"    → normale Player-Quests (STONE, ORE, WOOD ...)g
 *   - "galacticfy:quest_events"  → Community-Quests (global)
 *
 * Player-Stat-Format:
 *   TYPE|UUID|NAME|AMOUNT
 *
 * Community-Event-Format:
 *   UUID|NAME|TYPE|AMOUNT
 */
public class QuestEventSender {

    private static final String STATS_CHANNEL     = "galacticfy:queststats";
    private static final String COMMUNITY_CHANNEL = "galacticfy:quest_events";

    private final Plugin plugin;

    public QuestEventSender(Plugin plugin) {
        this.plugin = plugin;

        // Outgoing-Channels registrieren
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, STATS_CHANNEL);
        Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, COMMUNITY_CHANNEL);
    }

    // ========================================================================
    // NORMALER QUEST-STAT → NUR FÜR PLAYER-QUESTS
    // ========================================================================

    /**
     * Sendet einen normalen Quest-Stat wie "STONE", "ORE", "WOOD" usw.
     * Nur an das QuestService-Stat-System (Velocity).
     */
    public void sendStat(Player player, String type, long amount) {
        if (player == null || type == null || amount <= 0) return;

        String payload = type
                + "|" + player.getUniqueId()
                + "|" + player.getName()
                + "|" + amount;

        player.sendPluginMessage(plugin, STATS_CHANNEL,
                payload.getBytes(StandardCharsets.UTF_8));
    }

    // ========================================================================
    // COMMUNITY-QUEST-EVENTS
    // ========================================================================

    /**
     * Bequemer Aufruf: Community-Event mit Player.
     */
    public void sendCommunityEvent(Player player, String eventType, long amount) {
        if (player == null || eventType == null || amount <= 0) return;
        sendCommunityEvent(player.getUniqueId(), player.getName(), eventType, amount);
    }

    /**
     * Sendet globales Community-Quest-Event an Velocity.
     */
    public void sendCommunityEvent(UUID uuid, String name, String eventType, long amount) {
        if (uuid == null || name == null || eventType == null || amount <= 0) return;

        Player p = Bukkit.getPlayer(uuid);
        if (p == null) return; // Spieler offline → keine Channel-Sessions

        String payload = uuid
                + "|" + name
                + "|" + eventType
                + "|" + amount;

        p.sendPluginMessage(plugin, COMMUNITY_CHANNEL,
                payload.getBytes(StandardCharsets.UTF_8));
    }
}
