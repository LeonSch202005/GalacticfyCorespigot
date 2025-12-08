package de.galacticfy.galacticfyChat.quest;

import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.EnumSet;
import java.util.Set;

/**
 * Schickt Stats an Velocity + schützt pflanzbare Farmblöcke
 * (Zuckerrohr, Kaktus, Bambus, Pilze, ...) vor Abuse.
 *
 * Außerdem zählen alle relevanten Erze – auch im Nether –
 * als ORE für die Quest "Abbauen: X Erze".
 *
 * Wichtig:
 *  - Felder (Weizen, Karotten, Kartoffeln, Rote Bete) => CROPS
 *  - Zuckerrohr (natürlich gewachsen, nicht selbst gepflanzt) => SUGARCANE
 */
public class QuestStatsListener implements Listener {

    private final QuestEventSender questEventSender;
    private final PlantedBlockTracker plantedTracker = new PlantedBlockTracker();

    // Alle Pflanzen, die wir vor Abuse schützen wollen
    private static final Set<Material> TRACKED_PLANT_BLOCKS = EnumSet.of(
            Material.SUGAR_CANE,
            Material.CACTUS,
            Material.BAMBOO,
            Material.BROWN_MUSHROOM,
            Material.RED_MUSHROOM
            // ggf. erweitern: KELP, KELP_PLANT, BAMBOO_SAPLING, ...
    );

    public QuestStatsListener(QuestEventSender sender) {
        this.questEventSender = sender;
    }

    // ============================
    // Block Place -> merken
    // ============================
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlace(BlockPlaceEvent e) {
        Material type = e.getBlock().getType();

        if (TRACKED_PLANT_BLOCKS.contains(type)) {
            plantedTracker.markPlaced(e.getBlock());
        }
    }

    // ============================
    // Block Break -> Stats + Abuse-Schutz
    // ============================
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block block = e.getBlock();
        Material m = block.getType();

        // ------------------------------------------------
        // PFLANZEN: Zuckerrohr / Kaktus / Bambus / Pilze
        // ------------------------------------------------
        if (TRACKED_PLANT_BLOCKS.contains(m)) {

            int amount = 0;

            // Säulenpflanzen: Zuckerrohr / Kaktus / Bambus
            if (m == Material.SUGAR_CANE || m == Material.CACTUS || m == Material.BAMBOO) {

                Block current = block;
                while (current.getType() == m) {

                    if (plantedTracker.wasPlaced(current)) {
                        // selbst gesetzter Block -> zählt nicht
                        plantedTracker.unmark(current);
                    } else {
                        amount++;
                    }

                    current = current.getRelative(BlockFace.UP);
                }
            } else {
                // Pilze o.ä. -> Einzelblock
                if (plantedTracker.wasPlaced(block)) {
                    plantedTracker.unmark(block);
                } else {
                    amount = 1;
                }
            }

            // Nichts Natürliches dabei → keinen Stat schicken
            if (amount <= 0) {
                return;
            }

            // Zuckerrohr → eigener Stat "SUGARCANE"
            if (m == Material.SUGAR_CANE) {
                questEventSender.sendStat(p, "SUGARCANE", amount);
            }
            // Kaktus, Bambus, Pilze aktuell keine eigenen Quests → nichts senden
            return;
        }

        // ======================================================
        // MINING / BLOCKS / NORMALE FELD-CROPS
        // ======================================================
        switch (m) {

            // -------- STONE --------
            case STONE, COBBLESTONE, DEEPSLATE, COBBLED_DEEPSLATE -> {
                questEventSender.sendStat(p, "STONE", 1);
            }

            // -------- ORES (inkl. NETHER) --------
            case COAL_ORE, DEEPSLATE_COAL_ORE,
                 IRON_ORE, DEEPSLATE_IRON_ORE,
                 COPPER_ORE, DEEPSLATE_COPPER_ORE,
                 GOLD_ORE, DEEPSLATE_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE,
                 LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE,
                 // Nether-Erze:
                 NETHER_QUARTZ_ORE,
                 NETHER_GOLD_ORE,
                 ANCIENT_DEBRIS,
                 GILDED_BLACKSTONE -> {
                questEventSender.sendStat(p, "ORE", 1);
            }

            // -------- DIRT --------
            case DIRT, GRASS_BLOCK, PODZOL, ROOTED_DIRT, COARSE_DIRT -> {
                questEventSender.sendStat(p, "DIRT", 1);
            }

            // -------- GRAVEL --------
            case GRAVEL -> {
                questEventSender.sendStat(p, "GRAVEL", 1);
            }

            // -------- SAND --------
            case SAND, RED_SAND -> {
                questEventSender.sendStat(p, "SAND_BREAK", 1);
            }

            // -------- NETHERRACK --------
            case NETHERRACK -> {
                questEventSender.sendStat(p, "NETHERRACK", 1);
            }

            // -------- WOOD / LOGS --------
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG,
                 ACACIA_LOG, DARK_OAK_LOG,
                 MANGROVE_LOG, CHERRY_LOG -> {
                questEventSender.sendStat(p, "WOOD", 1);
            }

            // -------- FELDPFLANZEN (Ageable auf Farmland) --------
            case WHEAT, CARROTS, POTATOES, BEETROOTS -> {
                if (block.getBlockData() instanceof Ageable age &&
                        age.getAge() == age.getMaximumAge()) {

                    Block below = block.getRelative(BlockFace.DOWN);
                    if (below.getType() == Material.FARMLAND) {
                        questEventSender.sendStat(p, "CROPS", 1);
                    }
                }
            }

            default -> {
                // nichts senden
            }
        }
    }
}
