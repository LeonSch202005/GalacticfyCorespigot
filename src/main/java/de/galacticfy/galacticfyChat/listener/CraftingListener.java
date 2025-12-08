package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Sammelt alle Crafting-Quests:
 *  - CRAFT_TOOLS
 *  - CRAFT_BLOCKS
 *  - CRAFT_TORCHES
 *  - CRAFT_BREAD
 *  - CRAFT_GEAR
 */
public class CraftingListener implements Listener {

    private final QuestEventSender questEventSender;

    public CraftingListener(QuestEventSender sender) {
        this.questEventSender = sender;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCraft(CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        ItemStack result = e.getRecipe().getResult();
        Material m = result.getType();
        int amount = result.getAmount();

        // Shift-Klick? Dann multiplizieren wir korrekt
        int craftCount = amount;

        if (e.isShiftClick()) {
            // maximale Anzahl berechnen
            int max = Integer.MAX_VALUE;
            for (ItemStack item : e.getInventory().getMatrix()) {
                if (item != null && item.getAmount() > 0) {
                    max = Math.min(max, item.getAmount());
                }
            }
            craftCount = max * amount;
        }

        // ============================
        // TOOLS
        // ============================
        if (isTool(m)) {
            questEventSender.sendStat(p, "CRAFT_TOOLS", craftCount);
        }

        // ============================
        // GEAR (Rüstung + Waffen)
        // ============================
        if (isGear(m)) {
            questEventSender.sendStat(p, "CRAFT_GEAR", craftCount);
        }

        // ============================
        // TORCHES
        // ============================
        if (m == Material.TORCH) {
            questEventSender.sendStat(p, "CRAFT_TORCHES", craftCount);
        }

        // ============================
        // BREAD
        // ============================
        if (m == Material.BREAD) {
            questEventSender.sendStat(p, "CRAFT_BREAD", craftCount);
        }

        // ============================
        // BUILDING BLOCKS (z. B. Steinziegel)
        // ============================
        if (isBuildingBlock(m)) {
            questEventSender.sendStat(p, "CRAFT_BLOCKS", craftCount);
        }
    }

    private boolean isTool(Material m) {
        return switch (m) {
            case WOODEN_PICKAXE, WOODEN_AXE, WOODEN_SHOVEL, WOODEN_SWORD,
                 STONE_PICKAXE, STONE_AXE, STONE_SHOVEL, STONE_SWORD,
                 IRON_PICKAXE, IRON_AXE, IRON_SHOVEL, IRON_SWORD,
                 GOLDEN_PICKAXE, GOLDEN_AXE, GOLDEN_SHOVEL, GOLDEN_SWORD,
                 DIAMOND_PICKAXE, DIAMOND_AXE, DIAMOND_SHOVEL, DIAMOND_SWORD,
                 NETHERITE_PICKAXE, NETHERITE_AXE, NETHERITE_SHOVEL, NETHERITE_SWORD -> true;
            default -> false;
        };
    }

    private boolean isGear(Material m) {
        return switch (m) {
            case LEATHER_HELMET, LEATHER_CHESTPLATE, LEATHER_LEGGINGS, LEATHER_BOOTS,
                 IRON_HELMET, IRON_CHESTPLATE, IRON_LEGGINGS, IRON_BOOTS,
                 DIAMOND_HELMET, DIAMOND_CHESTPLATE, DIAMOND_LEGGINGS, DIAMOND_BOOTS,
                 NETHERITE_HELMET, NETHERITE_CHESTPLATE, NETHERITE_LEGGINGS, NETHERITE_BOOTS,
                 SHIELD, BOW, CROSSBOW, TRIDENT -> true;
            default -> false;
        };
    }

    private boolean isBuildingBlock(Material m) {
        return switch (m) {
            case STONE_BRICKS, BRICKS, DEEPSLATE_BRICKS, COBBLESTONE_WALL,
                 STONE_BRICK_WALL, BRICK_WALL, STONE_STAIRS, BRICK_STAIRS,
                 STONE_BRICK_STAIRS, COBBLESTONE_STAIRS, CUT_COPPER -> true;
            default -> false;
        };
    }
}
