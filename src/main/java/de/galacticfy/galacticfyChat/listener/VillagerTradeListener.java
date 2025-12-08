package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantInventory;
import org.bukkit.inventory.MerchantRecipe;

/**
 * Zählt Villager-Trades:
 *  - normaler Klick auf den Ergebnis-Slot: 1 Trade
 *  - Shift-Klick auf den Ergebnis-Slot: so viele Trades wie mit den
 *    aktuell eingelegten Items im Villager-GUI möglich sind
 */
public class VillagerTradeListener implements Listener {

    private final QuestEventSender questEventSender;

    public VillagerTradeListener(QuestEventSender sender) {
        this.questEventSender = sender;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVillagerTrade(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        InventoryView view = e.getView();
        if (!(view.getTopInventory() instanceof MerchantInventory merchantInv)) return;

        // Ergebnis-Slot im MerchantInventory ist 2 (0/1 Input, 2 Output)
        if (e.getRawSlot() != 2) return;

        InventoryAction action = e.getAction();

        // Nur Aktionen, bei denen wirklich etwas aus dem Ergebnis-Slot genommen wird
        switch (action) {
            case PICKUP_ALL:
            case PICKUP_SOME:
            case PICKUP_HALF:
            case PICKUP_ONE:
            case MOVE_TO_OTHER_INVENTORY: // Shift-Klick
                break;
            default:
                return;
        }

        MerchantRecipe recipe = merchantInv.getSelectedRecipe();
        if (recipe == null) {
            // Fallback: zähle wenigstens 1 Trade
            questEventSender.sendStat(p, "TRADE", 1);
            return;
        }

        int tradesPossible = calculatePossibleTrades(merchantInv, recipe);
        if (tradesPossible <= 0) {
            return;
        }

        int tradesToCount;

        // Shift-Klick (MOVE_TO_OTHER_INVENTORY) → alle möglichen Trades
        if (action == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            tradesToCount = tradesPossible;
        } else {
            // normaler Klick → nur 1 Trade
            tradesToCount = 1;
        }

        if (tradesToCount > 0) {
            questEventSender.sendStat(p, "TRADE", tradesToCount);
        }
    }

    /**
     * Berechnet, wie viele Trades mit den aktuell im MerchantInventory
     * eingelegten Items **maximal** möglich wären.
     *
     * Das entspricht dem Verhalten von Shift-Klick:
     *  - begrenzt durch vorhandene Items in Slot 0/1
     *  - begrenzt durch recipe.getMaxUses() - recipe.getUses()
     */
    private int calculatePossibleTrades(MerchantInventory inv, MerchantRecipe recipe) {
        // Kosten aus dem Rezept
        ItemStack cost1 = null;
        ItemStack cost2 = null;

        if (!recipe.getIngredients().isEmpty()) {
            cost1 = recipe.getIngredients().get(0);
            if (recipe.getIngredients().size() > 1) {
                cost2 = recipe.getIngredients().get(1);
            }
        }

        int max = Integer.MAX_VALUE;

        // Slot 0 & 1 im MerchantInventory sind die Input-Slots
        ItemStack slot0 = inv.getItem(0);
        ItemStack slot1 = inv.getItem(1);

        // Für die Berechnung zählen wir, wie viele vollständige "Kosten-Pakete"
        // in den Slots liegen.

        if (cost1 != null) {
            int count1 = countPossibleFromSlot(slot0, cost1);
            max = Math.min(max, count1);
        }

        if (cost2 != null) {
            int count2 = countPossibleFromSlot(slot1, cost2);
            max = Math.min(max, count2);
        }

        // Wenn es nur eine Zutat gibt, cost2==null → zweite Begrenzung ignorieren
        // MaxUses vom Villager berücksichtigen
        int usesLeft = recipe.getMaxUses() - recipe.getUses();
        max = Math.min(max, usesLeft);

        if (max == Integer.MAX_VALUE) {
            // Kein sinnvolles Limit berechnet -> sicherheitshalber 0
            return 0;
        }

        return Math.max(0, max);
    }

    /**
     * Wie viele vollständige Pakete von "cost" können aus dem Slot bedient werden?
     * Beispiel:
     *  - cost = 10 Papier
     *  - slot enthält 53 Papier
     *  -> 53 / 10 = 5 Trades (ganzzahlig)
     */
    private int countPossibleFromSlot(ItemStack slotItem, ItemStack cost) {
        if (cost == null || cost.getAmount() <= 0) return Integer.MAX_VALUE;
        if (slotItem == null) return 0;
        if (!slotItem.isSimilar(cost)) return 0;

        return slotItem.getAmount() / cost.getAmount();
    }
}
