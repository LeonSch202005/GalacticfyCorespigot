package de.galacticfy.galacticfyChat.rank;

public class SimpleRole {

    public final int id;
    public final String name;
    public final String displayName;
    public final String colorHex;
    public final String prefix;
    public final String suffix;
    public final boolean staff;
    public final boolean maintenanceBypass;
    public final int joinPriority;

    public SimpleRole(int id,
                      String name,
                      String displayName,
                      String colorHex,
                      String prefix,
                      String suffix,
                      boolean staff,
                      boolean maintenanceBypass,
                      int joinPriority) {
        this.id = id;
        this.name = name;
        this.displayName = displayName;
        this.colorHex = colorHex;
        this.prefix = prefix;
        this.suffix = suffix;
        this.staff = staff;
        this.maintenanceBypass = maintenanceBypass;
        this.joinPriority = joinPriority;
    }
}
