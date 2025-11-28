package de.galacticfy.galacticfyChat.npc;

import de.galacticfy.galacticfyChat.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

public class NpcRepository {

    private final DatabaseManager db;
    private final Logger logger;

    public NpcRepository(DatabaseManager db, Logger logger) {
        this.db = db;
        this.logger = logger;
    }

    public List<Npc> findByServer(String serverName) {
        List<Npc> list = new ArrayList<>();

        String sql = "SELECT * FROM gf_npcs WHERE server_name = ?";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, serverName);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRow(rs));
                }
            }

        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei findByServer: " + e.getMessage());
        }

        return list;
    }

    public Npc insert(Npc npc) {
        String sql = """
            INSERT INTO gf_npcs
            (server_name, name, world, x, y, z, yaw, pitch, type, target_server, skin_uuid)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, npc.getServerName());
            ps.setString(2, npc.getName());
            ps.setString(3, npc.getWorldName());
            ps.setDouble(4, npc.getX());
            ps.setDouble(5, npc.getY());
            ps.setDouble(6, npc.getZ());
            ps.setFloat(7, npc.getYaw());
            ps.setFloat(8, npc.getPitch());
            ps.setString(9, npc.getType());
            ps.setString(10, npc.getTargetServer());
            ps.setString(11, npc.getSkinUuid());

            ps.executeUpdate();

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    return new Npc(
                            id,
                            npc.getServerName(),
                            npc.getName(),
                            npc.getWorldName(),
                            npc.getX(),
                            npc.getY(),
                            npc.getZ(),
                            npc.getYaw(),
                            npc.getPitch(),
                            npc.getType(),
                            npc.getTargetServer(),
                            npc.getSkinUuid()
                    );
                }
            }

        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei insert: " + e.getMessage());
        }

        return npc;
    }

    public void deleteById(int id) {
        String sql = "DELETE FROM gf_npcs WHERE id = ?";

        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setInt(1, id);
            ps.executeUpdate();

        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei deleteById: " + e.getMessage());
        }
    }

    // ---- NEU: Update-Methoden ----

    public void updateLocation(int id, String world, double x, double y, double z, float yaw, float pitch) {
        String sql = """
            UPDATE gf_npcs
            SET world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?
            WHERE id = ?
            """;
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, world);
            ps.setDouble(2, x);
            ps.setDouble(3, y);
            ps.setDouble(4, z);
            ps.setFloat(5, yaw);
            ps.setFloat(6, pitch);
            ps.setInt(7, id);

            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei updateLocation: " + e.getMessage());
        }
    }

    public void updateName(int id, String name) {
        String sql = "UPDATE gf_npcs SET name = ? WHERE id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, name);
            ps.setInt(2, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei updateName: " + e.getMessage());
        }
    }

    public void updateTypeAndTarget(int id, String type, String targetServer) {
        String sql = "UPDATE gf_npcs SET type = ?, target_server = ? WHERE id = ?";
        try (Connection con = db.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {

            ps.setString(1, type);
            ps.setString(2, targetServer);
            ps.setInt(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            logger.warning("[NpcRepository] Fehler bei updateTypeAndTarget: " + e.getMessage());
        }
    }

    // ---- intern ----

    private Npc mapRow(ResultSet rs) throws SQLException {
        return new Npc(
                rs.getInt("id"),
                rs.getString("server_name"),
                rs.getString("name"),
                rs.getString("world"),
                rs.getDouble("x"),
                rs.getDouble("y"),
                rs.getDouble("z"),
                rs.getFloat("yaw"),
                rs.getFloat("pitch"),
                rs.getString("type"),
                rs.getString("target_server"),
                rs.getString("skin_uuid")
        );
    }
}
