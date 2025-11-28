package de.galacticfy.galacticfyChat.npc;

import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

public class NpcProtectionListener implements Listener {

    private final NpcManager manager;

    public NpcProtectionListener(NpcManager manager) {
        this.manager = manager;
    }

    // Kein Schaden für NPCs
    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        if (manager.getNpcByEntity(ent) != null) {
            e.setCancelled(true);
        }
    }

    // Kein Loot / Exp beim "Tod" (sollte eh nicht passieren wegen Damage-Cancel)
    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Entity ent = e.getEntity();
        if (manager.getNpcByEntity(ent) != null) {
            e.getDrops().clear();
            e.setDroppedExp(0);
        }
    }

    // Kein Verbrennen
    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (manager.getNpcByEntity(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }

    // Kein Hit / Knockback
    @EventHandler
    public void onDamageByEntity(EntityDamageByEntityEvent e) {
        if (manager.getNpcByEntity(e.getEntity()) != null) {
            e.setCancelled(true);
        }
    }
}
