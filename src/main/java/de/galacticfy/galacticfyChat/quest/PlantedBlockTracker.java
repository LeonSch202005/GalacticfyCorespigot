package de.galacticfy.galacticfyChat.quest;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Merkt sich von Spielern platzierte Pflanzen-Blöcke
 * (Zuckerrohr, Kaktus, Bambus, Pilze, ...),
 * damit wir sie beim Abbauen nicht für Quests zählen.
 */
public class PlantedBlockTracker {

    // thread-safe Set für Block-Keys
    private final Set<String> placedBlocks = ConcurrentHashMap.newKeySet();

    private String key(Location loc) {
        if (loc.getWorld() == null) return "null;0;0;0";
        return loc.getWorld().getName()
                + ";" + loc.getBlockX()
                + ";" + loc.getBlockY()
                + ";" + loc.getBlockZ();
    }

    public void markPlaced(Block block) {
        placedBlocks.add(key(block.getLocation()));
    }

    public void unmark(Block block) {
        placedBlocks.remove(key(block.getLocation()));
    }

    public boolean wasPlaced(Block block) {
        return placedBlocks.contains(key(block.getLocation()));
    }
}
