package de.galacticfy.galacticfyChat.punish;

import de.galacticfy.galacticfyChat.db.DatabaseManager;

import java.sql.*;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.logging.Logger;

public class PunishmentReader {

    public static class Punishment {
        public final int id;
        public final String reason;
        public final String staff;
        public final Instant expiresAt;

        public Punishment(int id, String reason, String staff, Instant expiresAt) {
            this.id = id;
            this.reason = reason;
            this.staff = staff;
            this.expiresAt = expiresAt;
        }
    }

    private final DatabaseManager db;
    private final Logger logger;

    public PunishmentReader(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Holt einen aktiven Mute (type = 'MUTE') für die UUID.
     * Wenn abgelaufen → null (und Eintrag wird in der DB deaktiviert).
     */
    public Punishment getActiveMute(UUID uuid) {
        if (uuid == null) return null;

        try (Connection con = db.getConnection()) {

            String sql = "SELECT * FROM gf_punishments " +
                    "WHERE uuid = ? AND type = 'MUTE' AND active = 1 " +
                    "ORDER BY id DESC LIMIT 1";

            try (PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());

                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return null;

                    int id = rs.getInt("id");
                    String reason = rs.getString("reason");
                    String staff  = rs.getString("staff");
                    Timestamp exp = rs.getTimestamp("expires_at");
                    Instant expiresAt = (exp != null ? exp.toInstant() : null);

                    // abgelaufen?
                    if (expiresAt != null && expiresAt.toEpochMilli() <= System.currentTimeMillis()) {
                        deactivateById(con, id);
                        return null;
                    }

                    return new Punishment(id, reason, staff, expiresAt);
                }
            }

        } catch (SQLException e) {
            logger.severe("[GalacticfyChat] Fehler beim Lesen des aktiven Mutes: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void deactivateById(Connection con, int id) {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE gf_punishments SET active = 0 WHERE id = ?"
        )) {
            ps.setInt(1, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[GalacticfyChat] Konnte abgelaufenen Mute id=" + id + " nicht deaktivieren: " + e.getMessage());
        }
    }

    public String formatRemaining(Punishment p) {
        if (p == null || p.expiresAt == null) {
            return "permanent";
        }
        long ms = p.expiresAt.toEpochMilli() - System.currentTimeMillis();
        if (ms <= 0) return "0s";

        long totalSeconds = ms / 1000L;
        long days    = totalSeconds / 86400;
        long hours   = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)    sb.append(days).append("d ");
        if (hours > 0)   sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.isEmpty()) sb.append(seconds).append("s");

        return sb.toString().trim();
    }
}
