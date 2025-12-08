package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceExtractEvent;

public class FurnaceSmeltListener implements Listener {

    private final QuestEventSender questEventSender;

    public FurnaceSmeltListener(QuestEventSender sender) {
        this.questEventSender = sender;
    }

    // Nur dieses Event benutzen!
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(FurnaceExtractEvent e) {
        Player p = e.getPlayer();
        int amount = e.getItemAmount();
        if (amount <= 0) return;

        Material m = e.getItemType();

        // Erz-Schmelzen
        if (isOreResult(m)) {
            questEventSender.sendStat(p, "SMELT_ORES", amount);
            return;
        }

        // Essen-Schmelzen
        if (isFood(m)) {
            questEventSender.sendStat(p, "SMELT_FOOD", amount);
        }
    }

    private boolean isOreResult(Material m) {
        return switch (m) {
            case IRON_INGOT,
                 GOLD_INGOT,
                 COPPER_INGOT,
                 NETHERITE_SCRAP -> true;
            default -> false;
        };
    }

    private boolean isFood(Material m) {
        return m.isEdible();
    }
}
