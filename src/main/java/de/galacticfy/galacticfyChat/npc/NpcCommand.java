package de.galacticfy.galacticfyChat.npc;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class NpcCommand implements CommandExecutor, TabCompleter {

    private final GalacticfyChat plugin;
    private final NpcRepository repo;
    private final NpcManager manager;
    private final String serverName;

    public NpcCommand(GalacticfyChat plugin,
                      NpcRepository repo,
                      NpcManager manager,
                      String serverName) {
        this.plugin = plugin;
        this.repo = repo;
        this.manager = manager;
        this.serverName = serverName;
    }

    @Override
    public boolean onCommand(CommandSender sender,
                             Command cmd,
                             String label,
                             String[] args) {

        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Ingame nutzbar.");
            return true;
        }

        if (!player.hasPermission("galacticfy.npc.manage")) {
            player.sendMessage("§cKeine Rechte.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> handleCreate(player, args);
            case "delete" -> handleDelete(player, args);
            case "list"   -> handleList(player);
            case "reload" -> handleReload(player);
            case "move"   -> handleMove(player, args);
            case "rename" -> handleRename(player, args);
            case "type"   -> handleType(player, args);
            case "tp"     -> handleTp(player, args);
            case "info"   -> handleInfo(player, args);
            default       -> sendHelp(player);
        }

        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage("§8§m---------------------------");
        p.sendMessage("§b/npc create <Name> <Type> [TargetServer]");
        p.sendMessage("§b/npc delete <ID>");
        p.sendMessage("§b/npc list");
        p.sendMessage("§b/npc move <ID> §7- verschiebt NPC zu dir");
        p.sendMessage("§b/npc rename <ID> <NeuerName>");
        p.sendMessage("§b/npc type <ID> <Type> [TargetServer]");
        p.sendMessage("§b/npc tp <ID> §7- teleportiert dich zum NPC");
        p.sendMessage("§b/npc info <ID> §7- zeigt alle Infos");
        p.sendMessage("§b/npc reload");
        p.sendMessage("§8§m---------------------------");
        p.sendMessage("§7Types:");
        p.sendMessage("§f  SERVER_SELECTOR §8- Globales Menü");
        p.sendMessage("§f  CB_SELECTOR      §8- Citybuild-Menü");
        p.sendMessage("§f  SKYBLOCK_SELECTOR§8- Skyblock-Menü");
        p.sendMessage("§f  INFO             §8- Info-NPC");
    }


    // ------------ Subcommands ------------

    private void handleCreate(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /npc create <Name> <Type> [TargetServer]");
            return;
        }

        String name = args[1].replace("_", " ");
        String type = args[2].toUpperCase();
        String target = args.length >= 4 ? args[3] : null;

        Npc npc = manager.createNpcAtPlayer(p, name, type, target);
        p.sendMessage("§aNPC erstellt mit ID §e" + npc.getId() +
                " §7auf Server §b" + serverName);
    }

    private void handleDelete(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /npc delete <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            if (!manager.getCache().containsKey(id)) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }

            repo.deleteById(id);
            manager.despawnNpc(id);
            p.sendMessage("§cNPC gelöscht: §e" + id);
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    private void handleList(Player p) {
        Map<Integer, Npc> npcs = manager.getCache();
        if (npcs.isEmpty()) {
            p.sendMessage("§7Keine NPCs für diesen Server.");
            return;
        }

        p.sendMessage("§8§m------ §bNPCs auf " + serverName + " §8§m------");
        for (var entry : npcs.entrySet()) {
            Npc npc = entry.getValue();
            p.sendMessage("§e#" + npc.getId() +
                    " §7» §f" + npc.getName() +
                    " §8(" + npc.getType() +
                    (npc.getTargetServer() != null ? " → " + npc.getTargetServer() : "") +
                    "§8)");
        }
    }

    private void handleReload(Player p) {
        manager.despawnAll();
        manager.loadAndSpawnAll();
        manager.startLookTask();
        p.sendMessage("§aNPCs wurden neu geladen.");
    }

    private void handleMove(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /npc move <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            Location loc = p.getLocation();
            if (!manager.moveNpc(id, loc)) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }
            p.sendMessage("§aNPC §e#" + id + " §awurde zu deiner Position verschoben.");
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    private void handleRename(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /npc rename <ID> <NeuerName>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String newName = args[2].replace("_", " ");

            if (!manager.renameNpc(id, newName)) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }

            p.sendMessage("§aNPC §e#" + id + " §7heißt jetzt §f" + newName + "§7.");
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    private void handleType(Player p, String[] args) {
        if (args.length < 3) {
            p.sendMessage("§cUsage: /npc type <ID> <Type> [TargetServer]");
            p.sendMessage("§7Beispiel: §f/npc type 1 SERVER_SELECTOR Citybuild-1");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            String type = args[2].toUpperCase();
            String target = args.length >= 4 ? args[3] : null;

            if (!manager.retargetNpc(id, type, target)) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }

            p.sendMessage("§aNPC §e#" + id + " §7hat jetzt Type §f" + type +
                    (target != null ? " §7→ §b" + target : "") + "§7.");
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    private void handleTp(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /npc tp <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            Npc npc = manager.getCache().get(id);
            if (npc == null) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }

            Location loc = npc.toLocation();
            if (loc == null) {
                p.sendMessage("§cWelt von diesem NPC ist nicht geladen.");
                return;
            }

            p.teleport(loc.clone().add(0, 1, 0));
            p.sendMessage("§7Du wurdest zu NPC §e#" + id + " §7teleportiert.");
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    private void handleInfo(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage("§cUsage: /npc info <ID>");
            return;
        }

        try {
            int id = Integer.parseInt(args[1]);
            Npc npc = manager.getCache().get(id);
            if (npc == null) {
                p.sendMessage("§cKein NPC mit dieser ID.");
                return;
            }

            p.sendMessage("§8§m----------- §bNPC Info §8§m-----------");
            p.sendMessage("§7ID: §e" + npc.getId());
            p.sendMessage("§7Name: §f" + npc.getName());
            p.sendMessage("§7Server: §b" + npc.getServerName());
            p.sendMessage("§7Type: §f" + npc.getType());
            if (npc.getTargetServer() != null) {
                p.sendMessage("§7Target: §b" + npc.getTargetServer());
            }
            p.sendMessage("§7World: §f" + npc.getWorldName());
            p.sendMessage(String.format("§7Pos: §f%.2f %.2f %.2f (Yaw %.1f Pitch %.1f)",
                    npc.getX(), npc.getY(), npc.getZ(), npc.getYaw(), npc.getPitch()));
            p.sendMessage("§8§m------------------------------");
        } catch (NumberFormatException e) {
            p.sendMessage("§cUngültige ID.");
        }
    }

    // --------------------------------------------------------
    // Tab-Completion
    // --------------------------------------------------------
    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command cmd,
                                      String alias,
                                      String[] args) {

        List<String> result = new ArrayList<>();

        if (!(sender instanceof Player) || !sender.hasPermission("galacticfy.npc.manage")) {
            return result;
        }

        if (args.length == 1) {
            List<String> base = List.of("create", "delete", "list", "reload",
                    "move", "rename", "type", "tp", "info");
            String prefix = args[0].toLowerCase();
            for (String s : base) {
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
            return result;
        }

        // ID-Vorschläge für Befehle, die eine ID brauchen
        if (args.length == 2 &&
                (args[0].equalsIgnoreCase("delete")
                        || args[0].equalsIgnoreCase("move")
                        || args[0].equalsIgnoreCase("rename")
                        || args[0].equalsIgnoreCase("type")
                        || args[0].equalsIgnoreCase("tp")
                        || args[0].equalsIgnoreCase("info"))) {

            String prefix = args[1];
            for (Integer id : manager.getCache().keySet()) {
                String s = String.valueOf(id);
                if (s.startsWith(prefix)) {
                    result.add(s);
                }
            }
            return result;
        }

        // Type-Vorschläge
        // Type-Vorschläge
        if (args.length == 3 &&
                (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("type"))) {

            String prefix = args[2].toUpperCase();
            for (String type : List.of("SERVER_SELECTOR", "CB_SELECTOR", "SKYBLOCK_SELECTOR", "INFO")) {
                if (type.startsWith(prefix)) {
                    result.add(type);
                }
            }
            return result;
        }


        // Server-Vorschläge
        if (args.length == 4 &&
                (args[0].equalsIgnoreCase("create") || args[0].equalsIgnoreCase("type"))) {

            String prefix = args[3];
            for (String srv : NpcConnectGui.AVAILABLE_SERVERS) {
                if (srv.toLowerCase().startsWith(prefix.toLowerCase())) {
                    result.add(srv);
                }
            }
            return result;
        }

        return result;
    }
}
