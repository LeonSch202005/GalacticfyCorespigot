package de.galacticfy.galacticfyChat.npc;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import de.galacticfy.galacticfyChat.npc.NpcRepository.NpcData;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class NpcManager {

    private final Plugin plugin;
    private final NpcRepository repository;
    private final String serverName;
    private final Logger logger;

    // id -> NPC-Entity
    private final Map<String, LivingEntity> npcs = new HashMap<>();

    // Task für "NPC schaut Spieler an"
    private BukkitTask lookTask;

    public NpcManager(GalacticfyChat plugin,
                      NpcRepository repository,
                      String serverName) {
        this.plugin = plugin;
        this.repository = repository;
        this.serverName = serverName;
        this.logger = plugin.getLogger();
    }

    // --------------------------------------------------------------------
    // Laden + Spawnen aller NPCs für diesen Server
    // --------------------------------------------------------------------
    public void loadAndSpawnAll() {
        logger.info("[GalacticfyChat] NPC-Spawn: lade Einträge für Server '" + serverName + "'...");

        List<NpcData> list = repository.findByServer(serverName);
        int count = 0;

        for (NpcData data : list) {
            if (spawnNpc(data)) {
                count++;
            }
        }

        logger.info("[GalacticfyChat] " + count + " NPC(s) gespawnt.");

        // Startet/Neustartet den "look at player" Task
        startLookTask();
    }

    // --------------------------------------------------------------------
    // Einzelnen NPC spawnen
    // --------------------------------------------------------------------
    private boolean spawnNpc(NpcData data) {
        // world MUSS gesetzt sein
        if (data.world() == null || data.world().isBlank()) {
            logger.warning("[GalacticfyChat] NPC '" + data.id()
                    + "' hat keine Welt (world = null) und wird übersprungen.");
            return false;
        }

        World world = Bukkit.getWorld(data.world());
        if (world == null) {
            logger.warning("[GalacticfyChat] NPC '" + data.id()
                    + "': Welt '" + data.world() + "' existiert nicht, wird übersprungen.");
            return false;
        }

        // Koordinaten – in deiner DB sind das DOUBLE, daher hier primitive double
        double x = data.x();
        double y = data.y();
        double z = data.z();
        float yaw = data.yaw();
        float pitch = data.pitch();

        Location loc = new Location(world, x, y, z, yaw, pitch);

        // Villager als NPC
        Villager villager = world.spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setGravity(false);
            v.setCollidable(false);
            v.setSilent(true);
            v.setPersistent(true);

            String display = (data.displayName() != null && !data.displayName().isBlank())
                    ? data.displayName()
                    : data.id();

            String colored = ChatColor.translateAlternateColorCodes('&', display);
            v.setCustomName(colored);
            v.setCustomNameVisible(true);
        });

        npcs.put(data.id(), villager);
        return true;
    }

    // --------------------------------------------------------------------
    // NPCs "schauen" zum nächsten Spieler
    // --------------------------------------------------------------------
    private void startLookTask() {
        if (lookTask != null) {
            lookTask.cancel();
        }

        lookTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    try {
                        for (LivingEntity npc : npcs.values()) {
                            if (npc == null || npc.isDead()) continue;

                            // nächsten Spieler im selben World suchen
                            var world = npc.getWorld();
                            var players = world.getPlayers();
                            if (players.isEmpty()) continue;

                            var locNpc = npc.getLocation();
                            double bestDistSq = Double.MAX_VALUE;
                            Location target = null;

                            for (var p : players) {
                                double distSq = p.getLocation().distanceSquared(locNpc);
                                if (distSq < bestDistSq) {
                                    bestDistSq = distSq;
                                    target = p.getLocation();
                                }
                            }

                            if (target == null) continue;

                            // Yaw berechnen, damit NPC den Spieler anschaut
                            double dx = target.getX() - locNpc.getX();
                            double dz = target.getZ() - locNpc.getZ();
                            double dy = (target.getY() + 1.6) - (locNpc.getY() + 1.6);

                            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                            float pitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

                            Location newLoc = locNpc.clone();
                            newLoc.setYaw(yaw);
                            newLoc.setPitch(pitch);

                            npc.teleport(newLoc);
                        }
                    } catch (Exception ex) {
                        logger.warning("[GalacticfyChat] Fehler im NPC-LookTask: " + ex.getMessage());
                    }
                },
                20L,   // Start nach 1 Sekunde
                5L     // alle 5 Ticks (~0,25s)
        );
    }

    // --------------------------------------------------------------------
    // Alles despawnen
    // --------------------------------------------------------------------
    public void despawnAll() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }

        for (Entity e : npcs.values()) {
            if (e != null && !e.isDead()) {
                e.remove();
            }
        }
        npcs.clear();
        logger.info("[GalacticfyChat] Alle NPCs wurden despawnt.");
    }
}
