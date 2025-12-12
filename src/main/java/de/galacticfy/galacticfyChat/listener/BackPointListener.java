package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.spawn.BackService;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class BackPointListener implements Listener {

    private final BackService backService;

    public BackPointListener(BackService backService) {
        this.backService = backService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent e) {
        Player p = e.getPlayer();

        // Join-Spawn / Back-TP / Spawn-TP -> genau einmal überspringen
        if (backService.consumeSuppressNextTeleportSave(p.getUniqueId())) {
            return;
        }

        Location from = e.getFrom();
        if (from == null || from.getWorld() == null) return;

        Location to = e.getTo();
        if (to != null && to.getWorld() != null && sameBlock(from, to)) return;

        // Backpunkt = Position VOR dem TP
        backService.setBackLocation(p, from);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent e) {
        Player p = e.getEntity();
        Location deathLoc = p.getLocation();
        if (deathLoc == null || deathLoc.getWorld() == null) return;

        backService.setBackLocation(p, deathLoc);
    }

    private boolean sameBlock(Location a, Location b) {
        if (a.getWorld() == null || b.getWorld() == null) return false;
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) return false;
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ();
    }
}
