package de.galacticfy.galacticfyChat.quest;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.HashSet;
import java.util.Set;

public class SugarCaneTracker {

    private final Set<String> placedSugarCane = new HashSet<>();

    private String key(Location loc) {
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    public void markPlaced(Block block) {
        placedSugarCane.add(key(block.getLocation()));
    }

    public void unmark(Block block) {
        placedSugarCane.remove(key(block.getLocation()));
    }

    public boolean wasPlaced(Block block) {
        return placedSugarCane.contains(key(block.getLocation()));
    }
}
