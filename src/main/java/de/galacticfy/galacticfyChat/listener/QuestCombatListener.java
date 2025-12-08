package de.galacticfy.galacticfyChat.listener;

import de.galacticfy.galacticfyChat.quest.QuestEventSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

/**
 * Zählt:
 *  - feindliche Mobs (MOB)
 *  - Zombies (ZOMBIE)
 *  - Creeper (CREEPER)
 *  - PvP-Kills (PVP_KILL)
 *  - Tode (DEATH)
 *
 * und schickt sie über QuestEventSender an den Proxy.
 */
public class QuestCombatListener implements Listener {

    private final QuestEventSender questEventSender;

    public QuestCombatListener(QuestEventSender questEventSender) {
        this.questEventSender = questEventSender;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();
        if (killer == null) return;

        EntityType type = entity.getType();

        // PvP-Kill
        if (type == EntityType.PLAYER) {
            questEventSender.sendStat(killer, "PVP_KILL", 1);
            return;
        }

        // Nur feindliche Mobs zählen
        if (entity instanceof Monster) {
            // Generelle Mob-Quest
            questEventSender.sendStat(killer, "MOB", 1);

            // Spezielle Zombie-Quests
            switch (type) {
                case ZOMBIE, ZOMBIE_VILLAGER, DROWNED, HUSK -> {
                    questEventSender.sendStat(killer, "ZOMBIE", 1);
                }
                case CREEPER -> {
                    questEventSender.sendStat(killer, "CREEPER", 1);
                }
                default -> {
                    // andere Monster -> nur MOB
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player dead = event.getEntity();
        // Death-Stat (für No-Death-Quests etc.)
        questEventSender.sendStat(dead, "DEATH", 1);
    }
}
