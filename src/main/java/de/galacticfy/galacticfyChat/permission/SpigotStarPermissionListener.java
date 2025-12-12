package de.galacticfy.galacticfyChat.permission;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.Map;

/**
 * Synct das Velocity-Ranksystem auf Spigot:
 *
 * - Wenn die Rolle eines Spielers in gf_role_permissions die Permission "*" hat,
 *   bekommt der Spieler auf diesem Spigot-Server "Star-Rechte":
 *      -> alle registrierten Bukkit-Permissions = TRUE
 *      -> plus ein paar Wildcards (minecraft.command.*, bukkit.command.* etc.)
 * - KEIN setOp(true) – alles läuft nur über PermissionAttachment.
 *
 * Dieses Plugin muss auf ALLEN Spigot-Servern laufen (Lobby-1, Citybuild-1, ...).
 */
public class SpigotStarPermissionListener implements Listener {

    private final DatabaseManager databaseManager;
    private final Logger logger;
    private final Plugin plugin;

    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();

    public SpigotStarPermissionListener(DatabaseManager databaseManager,
                                        Logger logger,
                                        Plugin plugin) {
        this.databaseManager = databaseManager;
        this.logger = logger;
        this.plugin = plugin;
    }

    // ============================================================
    // Events
    // ============================================================

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        syncStarPermissions(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        clearAttachment(player.getUniqueId());
    }

    // ============================================================
    // Hauptlogik
    // ============================================================

    /**
     * Prüft in der DB, ob die Rolle des Spielers "*" hat.
     */
    private boolean hasStarPermissionInDatabase(UUID uuid, String name) {
        String sql = """
                SELECT rp.permission
                FROM gf_user_roles ur
                JOIN gf_role_permissions rp ON rp.role_id = ur.role_id
                WHERE ur.uuid = ?
                  AND rp.permission = '*'
                LIMIT 1
                """;

        try (Connection con = databaseManager.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, uuid.toString());

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    logger.info("[SpigotStarPerm] " + name +
                            " hat in der DB '*' (RolePermission).");
                    return true;
                }
            }

        } catch (SQLException e) {
            logger.warning("[SpigotStarPerm] Fehler beim Prüfen von '*'-Permission für "
                    + name + ": " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    /**
     * Entfernt ein bestehendes Attachment (z.B. beim Quit oder Resync).
     */
    private void clearAttachment(UUID uuid) {
        PermissionAttachment attachment = attachments.remove(uuid);
        if (attachment == null) return;

        try {
            if (attachment.getPermissible() != null) {
                attachment.getPermissible().removeAttachment(attachment);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * Versucht, den Command-Tree für den Spieler neu zu senden (Paper 1.13+).
     */
    private void refreshPlayerCommands(Player player) {
        try {
            Method m = player.getClass().getMethod("updateCommands");
            m.setAccessible(true);
            m.invoke(player);
        } catch (NoSuchMethodException ignored) {
            // Spigot ohne updateCommands(): nichts zu tun
        } catch (Throwable t) {
            logger.fine("[SpigotStarPerm] updateCommands() konnte nicht ausgeführt werden: " + t.getMessage());
        }
    }

    /**
     * Sync für einen Spieler:
     *  - wenn er "*" in der DB hat -> alle Bukkit-Permissions setzen
     *  - sonst nichts tun
     */
    public void syncStarPermissions(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        // altes Attachment entfernen
        clearAttachment(uuid);

        if (!hasStarPermissionInDatabase(uuid, name)) {
            logger.info("[SpigotStarPerm] " + name + " hat KEIN '*' – kein Star-Sync auf "
                    + Bukkit.getServer().getName());
            return;
        }

        logger.info("[SpigotStarPerm] Vergib '*'-Rechte an " + name +
                " auf Server '" + Bukkit.getServer().getName() + "' (ohne OP).");

        PermissionAttachment attachment = player.addAttachment(plugin);
        attachments.put(uuid, attachment);

        // 1) '*' selbst
        attachment.setPermission("*", true);

        // 2) alle aktuell registrierten Bukkit-Permission-Nodes erlauben
        for (Permission perm : Bukkit.getPluginManager().getPermissions()) {
            attachment.setPermission(perm.getName(), true);
        }

        // 3) wichtige Wildcards (für TAB + Commands)
        attachment.setPermission("minecraft.command.*", true);
        attachment.setPermission("bukkit.command.*", true);
        attachment.setPermission("spigot.command.*", true);

        // Essentials (falls vorhanden)
        attachment.setPermission("essentials.*", true);
        attachment.setPermission("essentials.gamemode", true);
        attachment.setPermission("essentials.gamemode.creative", true);
        attachment.setPermission("essentials.gamemode.*", true);

        // eigene Plugins
        attachment.setPermission("galacticfy.*", true);
        attachment.setPermission("galacticfycore.*", true);
        attachment.setPermission("galacticfy.gamemode", true);
        attachment.setPermission("galacticfy.gamemode.self", true);
        attachment.setPermission("galacticfy.gamemode.others", true);

        // Rechte neu berechnen
        player.recalculatePermissions();

        // Command-Tree neu senden
        refreshPlayerCommands(player);
    }
}
