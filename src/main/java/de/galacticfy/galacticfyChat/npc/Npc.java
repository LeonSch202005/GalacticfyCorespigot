package de.galacticfy.galacticfyChat.npc;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Npc {

    private final int id;
    private final String serverName;
    private final String name;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final String type;
    private final String targetServer;
    private final String skinUuid;

    public Npc(int id,
               String serverName,
               String name,
               String worldName,
               double x, double y, double z,
               float yaw, float pitch,
               String type,
               String targetServer,
               String skinUuid) {

        this.id = id;
        this.serverName = serverName;
        this.name = name;
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.type = type;
        this.targetServer = targetServer;
        this.skinUuid = skinUuid;
    }

    public int getId() { return id; }
    public String getServerName() { return serverName; }
    public String getName() { return name; }
    public String getWorldName() { return worldName; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public String getType() { return type; }
    public String getTargetServer() { return targetServer; }
    public String getSkinUuid() { return skinUuid; }

    public Location toLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(world, x, y, z, yaw, pitch);
    }
}
