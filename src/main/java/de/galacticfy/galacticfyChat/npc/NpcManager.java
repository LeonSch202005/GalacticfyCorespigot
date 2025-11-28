package de.galacticfy.galacticfyChat.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.*;

public class NpcManager {

    private final Plugin plugin;
    private final NpcRepository repo;
    private final String serverName;

    private static final EntityType DEFAULT_NPC_ENTITY_TYPE = EntityType.VILLAGER;

    private final Map<Integer, Entity> spawned = new HashMap<>();
    private final Map<Integer, Npc> cache = new HashMap<>();
    private final Map<Integer, List<ArmorStand>> holograms = new HashMap<>();

    private BukkitTask lookTask;

    public NpcManager(Plugin plugin, NpcRepository repo, String serverName) {
        this.plugin = plugin;
        this.repo = repo;
        this.serverName = serverName;
    }

    // =========================================================
    //  Laden / Spawnen
    // =========================================================
    public void loadAndSpawnAll() {
        despawnAll();
        cache.clear();

        List<Npc> npcs = repo.findByServer(serverName);
        for (Npc npc : npcs) {
            cache.put(npc.getId(), npc);
            cleanupAroundNpc(npc);
            spawnNpc(npc);
        }

        plugin.getLogger().info("[NPC] Loaded " + npcs.size() + " NPCs für Server " + serverName);
    }

    private void cleanupAroundNpc(Npc npc) {
        Location loc = npc.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        double r = 1.5;
        for (Entity e : loc.getWorld().getNearbyEntities(loc, r, r, r)) {
            if (e instanceof ArmorStand || e.getType() == DEFAULT_NPC_ENTITY_TYPE) {
                e.remove();
            }
        }
    }

    public void despawnAll() {
        for (Entity e : spawned.values()) {
            e.remove();
        }
        spawned.clear();

        for (List<ArmorStand> list : holograms.values()) {
            for (ArmorStand as : list) {
                as.remove();
            }
        }
        holograms.clear();
    }

    public Entity spawnNpc(Npc npc) {
        Location loc = npc.toLocation();
        if (loc == null || loc.getWorld() == null) {
            plugin.getLogger().warning("[NPC] World " + npc.getWorldName() + " nicht geladen für NPC " + npc.getId());
            return null;
        }

        Entity old = spawned.get(npc.getId());
        if (old != null) {
            old.remove();
        }
        clearHolograms(npc.getId());

        LivingEntity body = (LivingEntity) loc.getWorld().spawnEntity(loc, DEFAULT_NPC_ENTITY_TYPE);

        body.setCustomNameVisible(false);
        body.setCustomName(null);
        body.setAI(false);
        body.setGravity(false);
        body.setCollidable(false);
        body.setInvulnerable(true);
        body.setRemoveWhenFarAway(false);
        try {
            body.setPersistent(false);
        } catch (NoSuchMethodError ignored) {
        }

        applyPlayerSkinIfPossible(body, npc.getName());

        spawned.put(npc.getId(), body);

        spawnHolograms(npc, body.getLocation());

        return body;
    }

    private void applyPlayerSkinIfPossible(LivingEntity entity, String skinName) {
        if (skinName == null || skinName.isBlank()) return;
        if (Bukkit.getPluginManager().getPlugin("LibsDisguises") == null) return;

        try {
            Class<?> disguiseApiClass = Class.forName("me.libraryaddict.disguise.DisguiseAPI");
            Class<?> playerDisguiseClass = Class.forName("me.libraryaddict.disguise.disguisetypes.PlayerDisguise");
            Class<?> disguiseBaseClass = Class.forName("me.libraryaddict.disguise.disguisetypes.Disguise");

            Constructor<?> ctor = playerDisguiseClass.getConstructor(String.class);
            Object playerDisguise = ctor.newInstance(skinName);

            // Name vom Disguise ausblenden, wenn möglich
            try {
                Method setNameVisible = playerDisguiseClass.getMethod("setNameVisible", boolean.class);
                setNameVisible.invoke(playerDisguise, false);
            } catch (NoSuchMethodException ignored) {}

            Method disguiseToAll = disguiseApiClass.getMethod("disguiseToAll", Entity.class, disguiseBaseClass);
            disguiseToAll.invoke(null, entity, playerDisguise);

            plugin.getLogger().info("[NPC] LibsDisguises: Spieler-Skin '" + skinName + "' auf NPC angewendet.");
        } catch (Exception e) {
            plugin.getLogger().warning("[NPC] Fehler beim Setzen des Spieler-Skins: " + e.getMessage());
        }
    }

    private void spawnHolograms(Npc npc, Location baseLoc) {
        clearHolograms(npc.getId());

        List<String> lines = getLinesForNpc(npc);
        if (lines.isEmpty()) return;

        List<ArmorStand> list = new ArrayList<>();

        double baseOffset = 2.3;
        double step = 0.28;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            double yOffset = baseOffset + (lines.size() - 1 - i) * step;

            Location lineLoc = baseLoc.clone().add(0, yOffset, 0);
            ArmorStand holo = baseLoc.getWorld().spawn(lineLoc, ArmorStand.class, s -> {
                s.setMarker(true);
                s.setGravity(false);
                s.setInvisible(true);
                s.setCustomNameVisible(true);
                s.setCustomName(line);
                s.setSmall(true);
                s.setBasePlate(false);
                s.setArms(false);
                try {
                    s.setPersistent(false);
                } catch (NoSuchMethodError ignored) {
                }
            });

            list.add(holo);
        }

        holograms.put(npc.getId(), list);
    }

    private void clearHolograms(int npcId) {
        List<ArmorStand> list = holograms.remove(npcId);
        if (list == null) return;
        for (ArmorStand as : list) as.remove();
    }

    private List<String> getLinesForNpc(Npc npc) {
        List<String> lines = new ArrayList<>();
        String type = npc.getType() != null ? npc.getType().toUpperCase() : "";

        lines.add("§b§lGalacticfy");

        switch (type) {
            case "CB_SELECTOR", "CITYBUILD_SELECTOR", "CITYBUILD" -> {
                lines.add("§a§lCitybuild");
                lines.add("§7Rechtsklick für §aCB-1");
            }
            case "SKYBLOCK_SELECTOR", "SKYBLOCK" -> {
                lines.add("§3§lSkyblock");
                lines.add("§7Rechtsklick für §3SB-1");
            }
            case "SERVER_SELECTOR" -> {
                lines.add("§b§lServer-Selector");
                lines.add("§7Rechtsklick für Auswahl");
            }
            case "INFO" -> {
                lines.add("§e§lInfo");
                lines.add("§7Rechtsklick für Informationen");
            }
            default -> {
                lines.add("§7NPC");
                lines.add("§fRechtsklick");
            }
        }

        return lines;
    }

    // =========================================================
    //  CRUD
    // =========================================================
    public Npc createNpcAtPlayer(Player player, String name, String type, String targetServer) {
        Location loc = player.getLocation();

        Npc npc = new Npc(
                0,
                serverName,
                name,
                loc.getWorld().getName(),
                loc.getX(),
                loc.getY(),
                loc.getZ(),
                loc.getYaw(),
                loc.getPitch(),
                type,
                targetServer,
                null
        );

        Npc saved = repo.insert(npc);
        cache.put(saved.getId(), saved);
        spawnNpc(saved);
        return saved;
    }

    public void despawnNpc(int id) {
        Entity e = spawned.remove(id);
        if (e != null) e.remove();
        clearHolograms(id);
        cache.remove(id);
    }

    public Npc getNpcByEntity(Entity e) {
        for (var entry : spawned.entrySet()) {
            if (entry.getValue().getUniqueId().equals(e.getUniqueId())) {
                return cache.get(entry.getKey());
            }
        }
        return null;
    }

    public Map<Integer, Npc> getCache() {
        return cache;
    }

    public boolean moveNpc(int id, Location newLoc) {
        Npc old = cache.get(id);
        if (old == null) return false;

        String worldName = newLoc.getWorld() != null
                ? newLoc.getWorld().getName()
                : old.getWorldName();

        repo.updateLocation(
                id,
                worldName,
                newLoc.getX(),
                newLoc.getY(),
                newLoc.getZ(),
                newLoc.getYaw(),
                newLoc.getPitch()
        );

        Npc updated = new Npc(
                old.getId(),
                old.getServerName(),
                old.getName(),
                worldName,
                newLoc.getX(),
                newLoc.getY(),
                newLoc.getZ(),
                newLoc.getYaw(),
                newLoc.getPitch(),
                old.getType(),
                old.getTargetServer(),
                old.getSkinUuid()
        );

        cache.put(id, updated);
        spawnNpc(updated);
        return true;
    }

    public boolean renameNpc(int id, String newName) {
        Npc old = cache.get(id);
        if (old == null) return false;

        repo.updateName(id, newName);

        Npc updated = new Npc(
                old.getId(),
                old.getServerName(),
                newName,
                old.getWorldName(),
                old.getX(),
                old.getY(),
                old.getZ(),
                old.getYaw(),
                old.getPitch(),
                old.getType(),
                old.getTargetServer(),
                old.getSkinUuid()
        );

        cache.put(id, updated);
        spawnNpc(updated);
        return true;
    }

    public boolean retargetNpc(int id, String newType, String newTargetServer) {
        Npc old = cache.get(id);
        if (old == null) return false;

        repo.updateTypeAndTarget(id, newType, newTargetServer);

        Npc updated = new Npc(
                old.getId(),
                old.getServerName(),
                old.getName(),
                old.getWorldName(),
                old.getX(),
                old.getY(),
                old.getZ(),
                old.getYaw(),
                old.getPitch(),
                newType,
                newTargetServer,
                old.getSkinUuid()
        );

        cache.put(id, updated);
        spawnNpc(updated);
        return true;
    }

    // =========================================================
    //  Auto-Look (schnell, ohne Delay)
    // =========================================================
    public void startLookTask() {
        stopLookTask();

        lookTask = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> {
                    try {
                        if (spawned.isEmpty()) return;

                        for (Entity e : spawned.values()) {
                            if (!(e instanceof LivingEntity living)) continue;

                            Location npcLoc = living.getLocation();
                            Player nearest = getNearestPlayer(npcLoc, 8.0);
                            if (nearest == null) continue;

                            Location eye = nearest.getEyeLocation();
                            double dx = eye.getX() - npcLoc.getX();
                            double dy = eye.getY() - (npcLoc.getY() + 1.6);
                            double dz = eye.getZ() - npcLoc.getZ();

                            double distXZ = Math.sqrt(dx * dx + dz * dz);
                            if (distXZ < 0.001) continue;

                            float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                            float pitch = (float) Math.toDegrees(-Math.atan2(dy, distXZ));

                            try {
                                // versuch nur Rotation zu setzen
                                living.setRotation(yaw, pitch);
                            } catch (NoSuchMethodError err) {
                                // fallback: Teleport mit neuer Rotation
                                npcLoc.setYaw(yaw);
                                npcLoc.setPitch(pitch);
                                living.teleport(npcLoc);
                            }
                        }
                    } catch (Exception ex) {
                        plugin.getLogger().warning("[NPC] Fehler im LookTask: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                },
                1L,   // Start nach 1 Tick
                1L    // JEDEN Tick
        );
    }

    public void stopLookTask() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
    }

    private Player getNearestPlayer(Location loc, double radius) {
        Player closest = null;
        double closestDistSq = radius * radius;

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.getWorld().equals(loc.getWorld())) continue;

            double distSq = p.getLocation().distanceSquared(loc);
            if (distSq <= closestDistSq) {
                closestDistSq = distSq;
                closest = p;
            }
        }
        return closest;
    }
}
