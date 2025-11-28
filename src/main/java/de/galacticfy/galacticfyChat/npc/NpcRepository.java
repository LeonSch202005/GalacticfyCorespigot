package de.galacticfy.galacticfyChat.npc;

import de.galacticfy.galacticfyChat.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NpcRepository {

    public record NpcData(
            String id,
            String displayName,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch,
            List<String> lines
    ) {}

    private final DatabaseManager db;
    private final Logger logger;

    public NpcRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    /**
     * Holt alle NPCs für einen bestimmten Servernamen (z.B. "Lobby-1").
     */
    public List<NpcData> findByServer(String serverName) {
        List<NpcData> list = new ArrayList<>();

        String sql =
                "SELECT id, display_name, world, x, y, z, yaw, pitch, `lines` " +
                        "FROM gf_npcs " +
                        "WHERE LOWER(server_name) = LOWER(?)";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, serverName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String id = rs.getString("id");
                    String displayName = rs.getString("display_name");
                    String world = rs.getString("world");
                    double x = rs.getDouble("x");
                    double y = rs.getDouble("y");
                    double z = rs.getDouble("z");
                    float yaw = rs.getFloat("yaw");
                    float pitch = rs.getFloat("pitch");

                    String linesRaw = rs.getString("lines");
                    List<String> lines = new ArrayList<>();
                    if (linesRaw != null && !linesRaw.isBlank()) {
                        for (String s : linesRaw.split("\n")) {
                            if (!s.isBlank()) {
                                lines.add(s);
                            }
                        }
                    }

                    list.add(new NpcData(
                            id,
                            displayName,
                            world,
                            x, y, z,
                            yaw, pitch,
                            lines
                    ));
                }
            }

        } catch (SQLException e) {
            logger.severe("[GalacticfyChat] Fehler beim Laden der NPCs für Server "
                    + serverName + ": " + e.getMessage());
            e.printStackTrace();
        }

        return list;
    }

    /**
     * Position speichern oder updaten.
     *  - KEIN Duplicate-Fehler mehr
     *  - INSERT wenn neu, UPDATE wenn vorhanden
     */
    public boolean upsertPosition(
            String id,
            String serverName,
            String world,
            double x,
            double y,
            double z,
            float yaw,
            float pitch
    ) {
        try (Connection con = db.getConnection()) {

            // Prüfen ob es die ID schon gibt
            boolean exists;
            try (PreparedStatement check = con.prepareStatement(
                    "SELECT 1 FROM gf_npcs WHERE id = ? LIMIT 1"
            )) {
                check.setString(1, id);
                try (ResultSet rs = check.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // UPDATE
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE gf_npcs SET " +
                                "server_name = ?, world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ? " +
                                "WHERE id = ?"
                )) {
                    ps.setString(1, serverName);
                    ps.setString(2, world);
                    ps.setDouble(3, x);
                    ps.setDouble(4, y);
                    ps.setDouble(5, z);
                    ps.setFloat(6, yaw);
                    ps.setFloat(7, pitch);
                    ps.setString(8, id);

                    ps.executeUpdate();
                }

            } else {
                // INSERT
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO gf_npcs " +
                                "(id, server_name, world, x, y, z, yaw, pitch, created_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW())"
                )) {
                    ps.setString(1, id);
                    ps.setString(2, serverName);
                    ps.setString(3, world);
                    ps.setDouble(4, x);
                    ps.setDouble(5, y);
                    ps.setDouble(6, z);
                    ps.setFloat(7, yaw);
                    ps.setFloat(8, pitch);

                    ps.executeUpdate();
                }
            }

            return true;

        } catch (SQLException ex) {
            logger.severe("[GalacticfyChat] Fehler bei upsertPosition(id=" + id +
                    ", server=" + serverName + "): " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }
}
