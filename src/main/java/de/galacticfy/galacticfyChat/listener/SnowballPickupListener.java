package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Weihnachts-Event:
 * zählt eingesammelte Schneebälle und schickt sie als XMAS_SNOWBALLS an den Proxy.
 */
public class SnowballPickupListener implements Listener {

    private final QuestEventSender questEventSender;

    public SnowballPickupListener(QuestEventSender questEventSender) {
        this.questEventSender = questEventSender;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPickup(EntityPickupItemEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player p)) return;

        ItemStack stack = e.getItem().getItemStack();
        if (stack == null || stack.getType() != Material.SNOWBALL) return;

        int amount = stack.getAmount();
        if (amount <= 0) return;

        // Stat an Velocity senden
        questEventSender.sendStat(p, "XMAS_SNOWBALLS", amount);
    }
}
