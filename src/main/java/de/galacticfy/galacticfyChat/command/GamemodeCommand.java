package de.galacticfy.galacticfyChat.command;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class GamemodeCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;

    public GamemodeCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    private String prefix() {
        return "§8[§bGalacticfy§8] §r";
    }

    // ----------------------------------------------------
    // Permission-Helfer
    // ----------------------------------------------------

    private boolean isStar(CommandSender sender) {
        // Spieler mit '*' oder galacticfy.* dürfen immer
        return sender.hasPermission("*") || sender.hasPermission("galacticfy.*");
    }

    private boolean hasSelfPermission(CommandSender sender) {
        return isStar(sender)
                || sender.hasPermission("galacticfy.gamemode")
                || sender.hasPermission("galacticfy.gamemode.self");
    }

    private boolean hasOthersPermission(CommandSender sender) {
        return isStar(sender)
                || sender.hasPermission("galacticfy.gamemode.others")
                || sender.hasPermission("galacticfy.gamemode");
    }

    // ----------------------------------------------------
    // Mode-PARSER
    // ----------------------------------------------------

    private GameMode parseGameMode(String input) {
        if (input == null) return null;
        String s = input.toLowerCase(Locale.ROOT);

        switch (s) {
            case "0":
            case "s":
            case "survival":
            case "su":
                return GameMode.SURVIVAL;

            case "1":
            case "c":
            case "creative":
            case "cr":
                return GameMode.CREATIVE;

            case "2":
            case "a":
            case "adventure":
            case "ad":
                return GameMode.ADVENTURE;

            case "3":
            case "sp":
            case "spectator":
            case "spec":
                return GameMode.SPECTATOR;

            default:
                return null;
        }
    }

    private String modeNiceName(GameMode gm) {
        return switch (gm) {
            case SURVIVAL -> "§aSurvival";
            case CREATIVE -> "§bCreative";
            case ADVENTURE -> "§eAdventure";
            case SPECTATOR -> "§dSpectator";
        };
    }

    private void sendUsage(CommandSender sender, String label) {
        sender.sendMessage(prefix() + "§7Benutzung:");
        sender.sendMessage("§8» §b/" + label + " <0|1|2|3|s|c|a|sp>");
        sender.sendMessage("§8» §b/" + label + " <Mode> <Spieler>");
        sender.sendMessage("§7Beispiele: §f/" + label + " c  §7oder §f/" + label + " 1 LeonS21");
    }

    // ----------------------------------------------------
    // onCommand
    // ----------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {

        if (args.length == 0) {
            sendUsage(sender, label);
            return true;
        }

        GameMode gm = parseGameMode(args[0]);
        if (gm == null) {
            sender.sendMessage(prefix() + "§cUnbekannter Gamemode: §e" + args[0]);
            sender.sendMessage("§7Erlaubt: §f0/1/2/3, s/c/a/sp, survival/creative/adventure/spectator");
            return true;
        }

        // /gm <mode>  → eigenen Gamemode
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix() + "§cNur Ingame-Spieler können ihren eigenen Gamemode ändern.");
                sender.sendMessage(prefix() + "§7Nutze: §f/" + label + " <Mode> <Spieler>");
                return true;
            }

            if (!hasSelfPermission(sender)) {
                sender.sendMessage(prefix() + "§cDazu hast du keine Berechtigung. (Self)");
                return true;
            }

            if (player.getGameMode() == gm) {
                player.sendMessage(prefix() + "§7Du bist bereits im Modus " + modeNiceName(gm) + "§7.");
                return true;
            }

            player.setGameMode(gm);
            player.sendMessage(prefix() + "§7Dein Gamemode wurde auf " + modeNiceName(gm) + " §7gesetzt.");
            return true;
        }

        // /gm <mode> <spieler>
        if (!hasOthersPermission(sender)) {
            sender.sendMessage(prefix() + "§cDazu hast du keine Berechtigung (andere Spieler).");
            return true;
        }

        String targetName = args[1];
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().equalsIgnoreCase(targetName)) {
                    target = p;
                    break;
                }
            }
        }

        if (target == null) {
            sender.sendMessage(prefix() + "§cSpieler §e" + targetName + " §cwurde nicht gefunden.");
            return true;
        }

        if (target.getGameMode() == gm) {
            sender.sendMessage(prefix() + "§7" + target.getName() +
                    " §7ist bereits im Modus " + modeNiceName(gm) + "§7.");
            return true;
        }

        target.setGameMode(gm);

        sender.sendMessage(prefix() + "§7Du hast den Gamemode von §b" + target.getName()
                + " §7auf " + modeNiceName(gm) + " §7gesetzt.");

        target.sendMessage(prefix() + "§7Dein Gamemode wurde von §b"
                + sender.getName() + " §7auf " + modeNiceName(gm) + " §7gesetzt.");

        return true;
    }

    // ----------------------------------------------------
    // TAB COMPLETION
    // ----------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender,
                                      Command command,
                                      String alias,
                                      String[] args) {

        // /gm <TAB>
        if (args.length == 1) {
            List<String> modes = Arrays.asList(
                    "0", "1", "2", "3",
                    "s", "c", "a", "sp",
                    "survival", "creative", "adventure", "spectator"
            );

            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String m : modes) {
                if (prefix.isEmpty() || m.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(m);
                }
            }
            return out;
        }

        // /gm <mode> <TAB> → Spielernamen (nur wenn Rechte für andere)
        if (args.length == 2) {
            if (!hasOthersPermission(sender)) {
                return List.of();
            }

            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                String n = p.getName();
                if (prefix.isEmpty() || n.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(n);
                }
            }
            return out;
        }

        return List.of();
    }
}
