package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

/**
 * Zählt gebaute Schneegolems für das Weihnachtsevent.
 *
 * Schneegolem entsteht, wenn ein CARVED_PUMPKIN oder JACK_O_LANTERN
 * auf zwei SNOW_BLOCKs gestapelt wird.
 *
 * Sendet:
 *   EVENT_SNOWMAN -> QuestService.handleEventSnowman(...)
 */
public class XmasSnowmanListener implements Listener {

    private final QuestEventSender questEventSender;

    public XmasSnowmanListener(QuestEventSender questEventSender) {
        this.questEventSender = questEventSender;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPumpkinPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        Block placed = e.getBlockPlaced();
        Material type = placed.getType();

        // Nur Schnitzkürbis / Jack-O-Lantern relevant
        if (type != Material.CARVED_PUMPKIN && type != Material.JACK_O_LANTERN) {
            return;
        }

        // Direkt darunter müssen zwei SNOW_BLOCK stehen
        Block below1 = placed.getRelative(BlockFace.DOWN);
        Block below2 = below1.getRelative(BlockFace.DOWN);

        if (below1.getType() == Material.SNOW_BLOCK &&
                below2.getType() == Material.SNOW_BLOCK) {

            // Ein Schneegolem gebaut → Stat an Velocity schicken
            questEventSender.sendStat(p, "EVENT_SNOWMAN", 1L);
        }
    }
}
