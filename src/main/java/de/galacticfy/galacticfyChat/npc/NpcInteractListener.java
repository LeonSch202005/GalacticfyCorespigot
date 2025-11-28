package de.galacticfy.galacticfyChat.npc;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class NpcInteractListener implements Listener {

    private final NpcManager manager;

    // Klick-Cooldown pro Spieler
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private static final long CLICK_COOLDOWN_MS = 500L;

    public NpcInteractListener(NpcManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent e) {
        Player p = e.getPlayer();
        Entity clicked = e.getRightClicked();

        // Nur auf Entities reagieren, die wir als NPC kennen
        Npc npc = manager.getNpcByEntity(clicked);
        if (npc == null) return;

        e.setCancelled(true);

        UUID uuid = p.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastClick.get(uuid);
        if (last != null && (now - last) < CLICK_COOLDOWN_MS) {
            return;
        }
        lastClick.put(uuid, now);

        String type = npc.getType() != null ? npc.getType().toUpperCase() : "";

        if ("SERVER_SELECTOR".equals(type)
                || type.endsWith("_SELECTOR")
                || type.equals("CITYBUILD")
                || type.equals("SKYBLOCK")) {

            NpcConnectGui.open(p, npc);
            return;
        }

        if ("INFO".equals(type)) {
            p.sendMessage("§8§m---------------------------");
            p.sendMessage("§b" + npc.getName());
            p.sendMessage("§7Das ist ein Info-NPC.");
            p.sendMessage("§7Hier könntest du später Infos / Regeln anzeigen.");
            p.sendMessage("§8§m---------------------------");
            return;
        }

        p.sendMessage("§7Du hast §f" + npc.getName() + " §7angeklickt.");
    }
}
