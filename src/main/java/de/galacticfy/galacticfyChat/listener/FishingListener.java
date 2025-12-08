package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Zählt gefangene Fische für das Quest-System.
 *
 * Sendet:
 *   FISH -> QuestService.handleFishCaught(...)
 */
public class FishingListener implements Listener {

    private final QuestEventSender questEventSender;

    public FishingListener(QuestEventSender questEventSender) {
        this.questEventSender = questEventSender;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        // Nur wenn wirklich etwas gefangen wurde
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH) {
            return;
        }

        Player player = event.getPlayer();

        if (!(event.getCaught() instanceof Item itemEntity)) {
            // z.B. XP, Boot o.ä. -> ignorieren
            return;
        }

        ItemStack stack = itemEntity.getItemStack();
        int amount = stack.getAmount();
        if (amount <= 0) {
            amount = 1;
        }

        // Ein Stat-Eintrag "FISH" mit Anzahl der Items
        questEventSender.sendStat(player, "FISH", amount);
    }
}
