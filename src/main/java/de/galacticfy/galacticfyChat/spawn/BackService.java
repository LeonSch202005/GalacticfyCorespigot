package de.galacticfy.galacticfyChat.spawn;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.sql.*;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BackService {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;

    private final int warmupSeconds;
    private final int cooldownSeconds;
    private final long dbDebounceMs;

    // Cache
    private final Map<UUID, Location> lastLocations = new ConcurrentHashMap<>();

    // Cooldown/Warmup
    private final Map<UUID, Long> cooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<UUID, BukkitTask> warmupTasks = new ConcurrentHashMap<>();

    // DB debounce
    private final Map<UUID, Long> lastDbWriteMs = new ConcurrentHashMap<>();

    // suppress: genau 1 Teleport-Event überspringen (Join-Spawn, /spawn etc.)
    private final Map<UUID, Boolean> suppressNextTeleportSave = new ConcurrentHashMap<>();

    public BackService(Plugin plugin,
                       DatabaseManager databaseManager,
                       String serverIdIgnored,     // bleibt drin, damit dein Call passt (kannst du später nutzen)
                       int warmupSeconds,
                       int cooldownSeconds,
                       long dbDebounceMs) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.warmupSeconds = warmupSeconds;
        this.cooldownSeconds = cooldownSeconds;
        this.dbDebounceMs = dbDebounceMs;

        initTable();
    }

    private String prefix() {
        return "§8[§bGalacticfy§8] §r";
    }

    // =========================================================
    // Suppress API
    // =========================================================

    /** Beim nächsten TeleportEvent dieses Spielers wird kein Back gespeichert (nur 1x). */
    public void suppressNextTeleportSave(UUID uuid) {
        if (uuid != null) suppressNextTeleportSave.put(uuid, true);
    }

    /** true => Listener soll speichern überspringen und Flag direkt entfernen. */
    public boolean consumeSuppressNextTeleportSave(UUID uuid) {
        if (uuid == null) return false;
        Boolean b = suppressNextTeleportSave.remove(uuid);
        return b != null && b;
    }

    // =========================================================
    // Back-Punkt speichern
    // =========================================================

    public void setBackLocation(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return;

        Location safe = loc.clone();
        UUID uuid = player.getUniqueId();

        lastLocations.put(uuid, safe);

        // DB debounce (Performance)
        long now = System.currentTimeMillis();
        long lastWrite = lastDbWriteMs.getOrDefault(uuid, 0L);
        if (dbDebounceMs > 0 && (now - lastWrite) < dbDebounceMs) {
            return;
        }
        lastDbWriteMs.put(uuid, now);

        saveToDatabase(uuid, safe);
    }

    // =========================================================
    // /back Warmup + Cooldown
    // =========================================================

    public boolean startBack(Player player) {
        if (player == null) return false;
        UUID uuid = player.getUniqueId();

        if (warmupTasks.containsKey(uuid)) {
            player.sendMessage(prefix() + "§cDu teleportierst bereits...");
            return true;
        }

        long now = System.currentTimeMillis();
        long until = cooldownUntilMs.getOrDefault(uuid, 0L);
        if (until > now) {
            long left = Math.max(1, (until - now + 999) / 1000);
            player.sendMessage(prefix() + "§cBitte warte noch §e" + left + "s §cbevor du /back erneut nutzt.");
            return true;
        }

        Location target = lastLocations.get(uuid);
        if (target == null) {
            target = loadFromDatabase(uuid);
            if (target != null) lastLocations.put(uuid, target);
        }

        if (target == null || target.getWorld() == null) {
            player.sendMessage(prefix() + "§cDu hast keinen gespeicherten Back-Punkt.");
            return true;
        }

        Location startLoc = player.getLocation().clone();
        final Location finalTarget = target.clone();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            int secondsLeft = warmupSeconds;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancelWarmup(uuid);
                    return;
                }

                if (movedTooMuch(startLoc, player.getLocation())) {
                    player.sendActionBar(Component.text("§cTeleport abgebrochen (bewegt)"));
                    player.sendMessage(prefix() + "§cTeleport abgebrochen, weil du dich bewegt hast.");
                    cancelWarmup(uuid);
                    return;
                }

                if (secondsLeft > 0) {
                    player.sendActionBar(Component.text("§e/back §7Teleport in §a" + secondsLeft + "§7..."));
                    secondsLeft--;
                    return;
                }

                cancelWarmup(uuid);

                World w = Bukkit.getWorld(finalTarget.getWorld().getName());
                if (w == null) {
                    player.sendMessage(prefix() + "§cWelt für Back-Punkt existiert nicht (mehr).");
                    return;
                }

                Location tpLoc = finalTarget.clone();
                tpLoc.setWorld(w);

                // damit Teleport-Listener NICHT wieder Back überschreibt
                suppressNextTeleportSave(uuid);

                boolean ok = player.teleport(tpLoc);
                if (ok) {
                    player.sendMessage(prefix() + "§aDu wurdest zurück teleportiert.");
                    cooldownUntilMs.put(uuid, System.currentTimeMillis() + (cooldownSeconds * 1000L));
                } else {
                    player.sendMessage(prefix() + "§cTeleport fehlgeschlagen.");
                }
            }
        }, 0L, 20L);

        warmupTasks.put(uuid, task);
        return true;
    }

    /** Alias, falls irgendwo back(player) verwendet wird */
    public boolean back(Player player) {
        return startBack(player);
    }

    public void shutdown() {
        for (BukkitTask t : warmupTasks.values()) {
            try { t.cancel(); } catch (Exception ignored) {}
        }
        warmupTasks.clear();
    }

    private boolean movedTooMuch(Location a, Location b) {
        if (a == null || b == null) return true;
        if (a.getWorld() == null || b.getWorld() == null) return true;
        if (!a.getWorld().getUID().equals(b.getWorld().getUID())) return true;

        double dx = a.getX() - b.getX();
        double dy = a.getY() - b.getY();
        double dz = a.getZ() - b.getZ();
        return (dx * dx + dy * dy + dz * dz) > (0.2 * 0.2);
    }

    private void cancelWarmup(UUID uuid) {
        BukkitTask t = warmupTasks.remove(uuid);
        if (t != null) {
            try { t.cancel(); } catch (Exception ignored) {}
        }
    }

    // =========================================================
    // DB
    // =========================================================

    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS gf_back (
                    uuid        VARCHAR(36) PRIMARY KEY,
                    world       VARCHAR(64) NOT NULL,
                    x           DOUBLE      NOT NULL,
                    y           DOUBLE      NOT NULL,
                    z           DOUBLE      NOT NULL,
                    yaw         FLOAT       NOT NULL,
                    pitch       FLOAT       NOT NULL,
                    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """;
        try (Connection con = databaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("[BackService] Tabelle gf_back konnte nicht erstellt werden: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void saveToDatabase(UUID uuid, Location loc) {
        String sql = """
                REPLACE INTO gf_back (uuid, world, x, y, z, yaw, pitch, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """;
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.setString(2, loc.getWorld().getName());
            ps.setDouble(3, loc.getX());
            ps.setDouble(4, loc.getY());
            ps.setDouble(5, loc.getZ());
            ps.setFloat(6, loc.getYaw());
            ps.setFloat(7, loc.getPitch());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("[BackService] Fehler beim Speichern: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Location loadFromDatabase(UUID uuid) {
        String sql = """
                SELECT world, x, y, z, yaw, pitch
                FROM gf_back
                WHERE uuid = ?
                """;
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) return null;

                return new Location(
                        world,
                        rs.getDouble("x"),
                        rs.getDouble("y"),
                        rs.getDouble("z"),
                        rs.getFloat("yaw"),
                        rs.getFloat("pitch")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[BackService] Fehler beim Laden: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    // =========================================================
// Clear Back (bei Join)
// =========================================================

    /** Löscht Back-Punkt aus Cache + DB (z.B. bei Join). */
    public void clearBackLocation(UUID uuid) {
        if (uuid == null) return;

        lastLocations.remove(uuid);

        String sql = "DELETE FROM gf_back WHERE uuid = ?";
        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());
            ps.executeUpdate();

        } catch (SQLException e) {
            plugin.getLogger().warning("[BackService] Fehler beim Löschen des Back-Punkts: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Convenience: Player-Version */
    public void clearBackLocation(Player player) {
        if (player == null) return;
        clearBackLocation(player.getUniqueId());
    }

}
