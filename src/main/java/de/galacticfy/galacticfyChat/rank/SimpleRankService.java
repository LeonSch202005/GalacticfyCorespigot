package de.galacticfy.galacticfyChat.rank;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.sql.*;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SimpleRankService {

    private final DatabaseManager db;
    private final Logger logger;

    private final String defaultRoleName = "spieler";

    private final Map<String, SimpleRole> roleByName = new ConcurrentHashMap<>();
    private final Map<UUID, SimpleRole> userRoleCache = new ConcurrentHashMap<>();

    public SimpleRankService(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
        reloadAllRoles();
    }

    // =========================================================
    // Reload
    // =========================================================

    public void reload() {
        logger.info("[GalacticfyChat] Reload der Rollen-Caches gestartet...");
        roleByName.clear();
        userRoleCache.clear();
        reloadAllRoles();
        refreshAllOnline();
        logger.info("[GalacticfyChat] Reload der Rollen-Caches abgeschlossen.");
    }

    private void reloadAllRoles() {
        roleByName.clear();
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM gf_roles");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                SimpleRole role = mapRole(rs);
                roleByName.put(role.name.toLowerCase(Locale.ROOT), role);
            }
            logger.info("[GalacticfyChat] " + roleByName.size() + " Rollen geladen.");
        } catch (SQLException e) {
            logger.severe("[GalacticfyChat] Fehler beim Laden der Rollen: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private SimpleRole mapRole(ResultSet rs) throws SQLException {
        return new SimpleRole(
                rs.getInt("id"),
                rs.getString("name"),
                rs.getString("display_name"),
                rs.getString("color_hex"),
                rs.getString("prefix"),
                rs.getString("suffix"),
                rs.getBoolean("is_staff"),
                rs.getBoolean("maintenance_bypass"),
                rs.getInt("join_priority")
        );
    }

    private SimpleRole getRoleByName(String name) {
        if (name == null || name.isBlank()) return null;
        String key = name.toLowerCase(Locale.ROOT);

        SimpleRole cached = roleByName.get(key);
        if (cached != null) return cached;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement("SELECT * FROM gf_roles WHERE name = ?")) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    SimpleRole role = mapRole(rs);
                    roleByName.put(key, role);
                    return role;
                }
            }
        } catch (SQLException e) {
            logger.severe("[GalacticfyChat] Fehler beim Nachladen der Rolle " + name + ": " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    // =========================================================
    // User → Rolle (gf_user_roles)
    // =========================================================

    private SimpleRole getRoleFor(UUID uuid) {
        SimpleRole cached = userRoleCache.get(uuid);
        if (cached != null) return cached;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT u.name, u.role_id, u.expires_at, r.* " +
                             "FROM gf_user_roles u JOIN gf_roles r ON u.role_id = r.id " +
                             "WHERE u.uuid = ?"
             )) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("expires_at");
                    if (ts != null && ts.toInstant().isBefore(Instant.now())) {
                        SimpleRole def = getRoleByName(defaultRoleName);
                        userRoleCache.put(uuid, def);
                        return def;
                    }

                    SimpleRole role = mapRole(rs);
                    userRoleCache.put(uuid, role);
                    return role;
                }
            }
        } catch (SQLException e) {
            logger.severe("[GalacticfyChat] Fehler beim Laden der User-Rolle: " + e.getMessage());
            e.printStackTrace();
        }

        SimpleRole def = getRoleByName(defaultRoleName);
        userRoleCache.put(uuid, def);
        return def;
    }

    // =========================================================
    // Chat-DisplayName
    // =========================================================

    public Component getDisplayName(Player player) {
        SimpleRole role = getRoleFor(player.getUniqueId());
        String name = player.getName();

        // Name IMMER grau (&7)
        Component nameComp = Component.text(name, NamedTextColor.GRAY);

        // Prefix aus DB (mit &-Farbcodes) – Fallback: &7Spieler
        String prefixRaw = (role != null && role.prefix != null && !role.prefix.isBlank())
                ? role.prefix.trim()
                : "&7Spieler";

        Component rankComp = LegacyComponentSerializer.legacySection()
                .deserialize(prefixRaw.replace('&', '§'));

        // Stern in dunkelgrau
        Component symbolComp = Component.text(" ✦ ", NamedTextColor.DARK_GRAY);

        return Component.empty()
                .append(rankComp)
                .append(symbolComp)
                .append(nameComp);
    }


    // Für Scoreboard/Sidebar: &-Codes zurückgeben
    public String getLegacyRankPrefix(Player player) {
        SimpleRole role = getRoleFor(player.getUniqueId());

        String raw = (role != null && role.prefix != null && !role.prefix.isBlank())
                ? role.prefix
                : "&7Spieler";

        return raw; // "&4Inhaber" etc.
    }

    // =========================================================
    // Nametag / Tablist via Teams
    // =========================================================

    public void updateNametag(Player target) {
        SimpleRole role = getRoleFor(target.getUniqueId());

        String prefixRaw = (role != null && role.prefix != null && !role.prefix.isBlank())
                ? role.prefix.trim()
                : "&7Spieler";

        String rank = ChatColor.translateAlternateColorCodes('&', prefixRaw);
        String fullPrefix = rank + ChatColor.DARK_GRAY + " ✦ " + ChatColor.GRAY;

        int priority = (role != null) ? role.joinPriority : 0;
        int order = 99 - Math.max(Math.min(priority, 99), 0);

        String teamName = String.format("gf%02d_%d", order, role != null ? role.id : 0);
        if (teamName.length() > 16) teamName = teamName.substring(0, 16);

        for (Player viewer : Bukkit.getOnlinePlayers()) {

            Scoreboard board = viewer.getScoreboard();
            if (board == Bukkit.getScoreboardManager().getMainScoreboard()) {
                board = Bukkit.getScoreboardManager().getNewScoreboard();
                viewer.setScoreboard(board);
            }

            Team team = board.getTeam(teamName);
            if (team == null)
                team = board.registerNewTeam(teamName);

            team.setPrefix(fullPrefix);
            team.setColor(ChatColor.GRAY);

            team.addEntry(target.getName());
        }
    }



    public void clearNametag(Player player) {
        Scoreboard board = player.getScoreboard();
        for (Team t : board.getTeams()) {
            t.removeEntry(player.getName());
        }
    }


    /**
     * Wird vom Auto-Refresh aufgerufen:
     *  - Rank-Cache leeren (neue Ränge aus DB)
     *  - Nametag / Tablist aller Spieler aktualisieren
     */
    public void refreshAllOnline() {
        userRoleCache.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateNametag(p);
        }
    }
}
