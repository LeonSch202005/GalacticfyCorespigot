package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zählt gelaufene Blöcke und schickt sie als WALK an den Proxy.
 *
 * - Nur horizontale Bewegung (X/Z)
 * - Kleine Bewegungen (Jitter) werden ignoriert
 * - Es wird gepuffert, bis mind. 1 Block zusammenkommt
 * - Fliegen, Elytra-Gleiten und Reiten zählen NICHT
 * - Elytra-Gleiten wird komplett blockiert
 */
public class QuestWalkListener implements Listener {

    private final QuestEventSender questEventSender;

    // Puffer pro Spieler (Teilstrecken)
    private final Map<UUID, Double> distanceBuffer = new HashMap<>();

    public QuestWalkListener(QuestEventSender questEventSender) {
        this.questEventSender = questEventSender;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (!e.getFrom().getWorld().equals(e.getTo().getWorld())) return;

        Player p = e.getPlayer();

        // Fliegen / Elytra / Fahrzeuge → NICHT zählen
        if (p.isFlying() || p.isGliding() || p.getVehicle() != null) {
            return;
        }

        double dx = e.getTo().getX() - e.getFrom().getX();
        double dz = e.getTo().getZ() - e.getFrom().getZ();

        double distSquared = dx * dx + dz * dz;
        // ganz kleine Bewegungen ignorieren (Head-Rotation etc.)
        if (distSquared < 0.01) {
            return;
        }

        double dist = Math.sqrt(distSquared);

        UUID uuid = p.getUniqueId();
        double buffer = distanceBuffer.getOrDefault(uuid, 0.0);
        buffer += dist;

        long fullBlocks = (long) Math.floor(buffer);
        if (fullBlocks <= 0) {
            distanceBuffer.put(uuid, buffer);
            return;
        }

        // Rest im Buffer behalten
        buffer -= fullBlocks;
        distanceBuffer.put(uuid, buffer);

        // An den Proxy schicken
        questEventSender.sendStat(p, "WALK", fullBlocks);
    }

    /**
     * Elytra komplett blocken.
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onToggleGlide(EntityToggleGlideEvent e) {
        if (!(e.getEntity() instanceof Player p)) return;

        // Nur eingreifen, wenn der Spieler GLEITEN will (true)
        if (!e.isGliding()) return;

        e.setCancelled(true);
        p.sendMessage("§cElytra ist auf diesem Server deaktiviert.");
    }
}
