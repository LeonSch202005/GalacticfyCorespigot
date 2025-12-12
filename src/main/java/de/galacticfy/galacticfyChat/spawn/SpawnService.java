package de.galacticfy.galacticfyChat.spawn;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.sql.*;

/**
 * Speichert den Spawn pro Server in MariaDB.
 *
 * Tabelle:
 *   gf_spawns(
 *      server_name VARCHAR(64) PRIMARY KEY,
 *      world       VARCHAR(64) NOT NULL,
 *      x           DOUBLE      NOT NULL,
 *      y           DOUBLE      NOT NULL,
 *      z           DOUBLE      NOT NULL,
 *      yaw         FLOAT       NOT NULL,
 *      pitch       FLOAT       NOT NULL
 *   )
 */
public class SpawnService {

    private final Plugin plugin;
    private final DatabaseManager databaseManager;
    private final String serverName;   // so wie GalacticfyChat ihn liefert

    private Location spawnLocation; // Cache

    public SpawnService(Plugin plugin,
                        DatabaseManager databaseManager,
                        String serverName) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.serverName = serverName;

        initTable();
        loadSpawnFromDatabase();
    }

    // =====================================================================
    // Öffentliche API
    // =====================================================================

    /**
     * Gibt die aktuelle Spawn-Location für diesen Server zurück.
     * Fallback: Default-World-Spawn.
     */
    public Location getSpawnLocation() {
        if (spawnLocation == null) {
            World w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w != null) {
                return w.getSpawnLocation();
            }
            return null;
        }
        return spawnLocation.clone();
    }

    /**
     * Setzt den Spawn und speichert ihn in der DB.
     */
    public void setSpawnLocation(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return;
        }
        this.spawnLocation = loc.clone();
        saveSpawnToDatabase();
    }

    /**
     * Teleportiert einen Spieler zum Spawn.
     *
     * @param player Spieler
     * @param sendMessage true = Erfolg/Fehler-Nachricht senden
     * @return true, wenn Teleport versucht wurde
     */
    public boolean teleportToSpawn(Player player, boolean sendMessage) {
        Location loc = getSpawnLocation();
        if (loc == null) {
            if (sendMessage) {
                player.sendMessage("§cEs ist kein Spawn für diesen Server gesetzt.");
            }
            return false;
        }

        boolean ok = player.teleport(loc);
        if (sendMessage) {
            if (ok) {
                player.sendMessage("§aDu wurdest zum Spawn teleportiert.");
            } else {
                player.sendMessage("§cTeleport zum Spawn ist fehlgeschlagen.");
            }
        }
        return ok;
    }

    // =====================================================================
    // DB-Handling
    // =====================================================================

    private void initTable() {
        String sql = """
                CREATE TABLE IF NOT EXISTS gf_spawns (
                    server_name VARCHAR(64) PRIMARY KEY,
                    world       VARCHAR(64) NOT NULL,
                    x           DOUBLE      NOT NULL,
                    y           DOUBLE      NOT NULL,
                    z           DOUBLE      NOT NULL,
                    yaw         FLOAT       NOT NULL,
                    pitch       FLOAT       NOT NULL
                )
                """;

        try (Connection con = databaseManager.getConnection();
             Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("[SpawnService] Tabelle gf_spawns konnte nicht erstellt werden: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void loadSpawnFromDatabase() {
        String sql = """
                SELECT world, x, y, z, yaw, pitch
                FROM gf_spawns
                WHERE server_name = ?
                """;

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, serverName);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    plugin.getLogger().info("[SpawnService] Kein Spawn-Eintrag für Server '" + serverName + "'. Benutze Default-Spawn.");
                    spawnLocation = null;
                    return;
                }

                String worldName = rs.getString("world");
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    plugin.getLogger().warning("[SpawnService] Welt '" + worldName + "' existiert nicht (server=" + serverName + ").");
                    spawnLocation = null;
                    return;
                }

                double x = rs.getDouble("x");
                double y = rs.getDouble("y");
                double z = rs.getDouble("z");
                float yaw = rs.getFloat("yaw");
                float pitch = rs.getFloat("pitch");

                spawnLocation = new Location(world, x, y, z, yaw, pitch);
                plugin.getLogger().info("[SpawnService] Spawn aus DB geladen für Server '" + serverName +
                        "' @ " + worldName + " (" + x + ", " + y + ", " + z + ", yaw=" + yaw + ", pitch=" + pitch + ").");
            }

        } catch (SQLException e) {
            plugin.getLogger().warning("[SpawnService] Fehler beim Laden des Spawns: " + e.getMessage());
            e.printStackTrace();
            spawnLocation = null;
        }
    }

    private void saveSpawnToDatabase() {
        if (spawnLocation == null || spawnLocation.getWorld() == null) {
            return;
        }

        String sql = """
                REPLACE INTO gf_spawns (server_name, world, x, y, z, yaw, pitch)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, serverName);
            ps.setString(2, spawnLocation.getWorld().getName());
            ps.setDouble(3, spawnLocation.getX());
            ps.setDouble(4, spawnLocation.getY());
            ps.setDouble(5, spawnLocation.getZ());
            ps.setFloat(6, spawnLocation.getYaw());
            ps.setFloat(7, spawnLocation.getPitch());

            ps.executeUpdate();
            plugin.getLogger().info("[SpawnService] Spawn in DB gespeichert für '" + serverName +
                    "' @ " + spawnLocation.getWorld().getName() +
                    " (" + spawnLocation.getX() + ", " + spawnLocation.getY() + ", " + spawnLocation.getZ() +
                    ", yaw=" + spawnLocation.getYaw() + ", pitch=" + spawnLocation.getPitch() + ").");

        } catch (SQLException e) {
            plugin.getLogger().warning("[SpawnService] Fehler beim Speichern des Spawns: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
