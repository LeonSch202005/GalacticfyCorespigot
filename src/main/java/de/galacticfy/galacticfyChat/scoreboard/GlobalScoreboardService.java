package de.galacticfy.galacticfyChat.scoreboard;

import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GlobalScoreboardService {

    private final SimpleRankService rankService;
    private final Map<UUID, Scoreboard> boards = new HashMap<>();

    private static final String OBJECTIVE_ID = "gf_lobby";

    public GlobalScoreboardService(SimpleRankService rankService) {
        this.rankService = rankService;
    }

    // ------------------------------------------------------------------------
    // Hilfs-Methoden
    // ------------------------------------------------------------------------

    private Scoreboard getOrCreateBoard(Player player) {
        Scoreboard board = boards.get(player.getUniqueId());
        ScoreboardManager mgr = Bukkit.getScoreboardManager();

        if (mgr == null) {
            throw new IllegalStateException("ScoreboardManager ist null (Server noch nicht bereit?)");
        }

        if (board == null) {
            board = mgr.getNewScoreboard();
            boards.put(player.getUniqueId(), board);
        }
        return board;
    }

    private void setLine(Objective obj, int score, String text) {
        obj.getScore(text).setScore(score);
    }

    // ------------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------------

    public void removeBoard(Player player) {
        boards.remove(player.getUniqueId());
        ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr != null) {
            player.setScoreboard(mgr.getMainScoreboard());
        }
    }

    public void updateBoard(Player player) {
        Scoreboard board = getOrCreateBoard(player);

        // Objective holen / erstellen
        Objective obj = board.getObjective(OBJECTIVE_ID);
        if (obj == null) {
            obj = board.registerNewObjective(
                    OBJECTIVE_ID,
                    "dummy",
                    ChatColor.AQUA + "" + ChatColor.BOLD + "✦ Galacticfy ✦"
            );
        } else {
            obj.setDisplayName(ChatColor.AQUA + "" + ChatColor.BOLD + "✦ Galacticfy ✦");
        }
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);

        // Werte
        String name = player.getName();
        String rankLegacy = rankService.getLegacyRankPrefix(player); // z.B. "&4Besitzer"
        String rankColored = ChatColor.translateAlternateColorCodes('&', rankLegacy);

        int online = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        int ping = player.getPing(); // Paper / moderne Spigot-Versionen

        // Scoreboard leeren
        for (String s : board.getEntries()) {
            board.resetScores(s);
        }

        // Leere/Spacer-Zeilen (müssen einzigartig sein)
        String blank1 = ChatColor.DARK_GRAY.toString();
        String blank2 = ChatColor.GRAY.toString();
        String blank3 = ChatColor.BLACK.toString();
        String blank4 = ChatColor.DARK_BLUE.toString();

        int line = 15;

        // Abstand unter dem Titel
        setLine(obj, line--, blank1);

        // --------------------------------------------------
        // PROFIL BLOCK
        // --------------------------------------------------
        setLine(obj, line--,
                ChatColor.DARK_AQUA + "» " + ChatColor.AQUA + "Profil");
        setLine(obj, line--,
                ChatColor.GRAY + "Name: " + ChatColor.WHITE + name);
        setLine(obj, line--,
                ChatColor.GRAY + "Rang: " + rankColored);

        setLine(obj, line--, blank2);

        // --------------------------------------------------
        // NETZWERK BLOCK
        // --------------------------------------------------
        setLine(obj, line--,
                ChatColor.DARK_AQUA + "» " + ChatColor.AQUA + "Netzwerk");
        setLine(obj, line--,
                ChatColor.GRAY + "Online: "
                        + ChatColor.WHITE + online
                        + ChatColor.GRAY + "/"
                        + ChatColor.WHITE + maxPlayers);
        setLine(obj, line--,
                ChatColor.GRAY + "Ping: "
                        + ChatColor.WHITE + ping + "ms");

        // kleiner Abstand vor der Domain
        setLine(obj, line--, blank3);

        // --------------------------------------------------
        // DOMAIN (ganz unten)
        // --------------------------------------------------
        setLine(obj, line--,
                ChatColor.DARK_AQUA + "➥ "
                        + ChatColor.AQUA + "play"
                        + ChatColor.WHITE + "."
                        + ChatColor.AQUA + "galacticfy"
                        + ChatColor.WHITE + ".de"
        );

        // allerletzte unsichtbare Zeile (Abstand)
        setLine(obj, line--, blank4);

        // Scoreboard setzen
        player.setScoreboard(board);
    }

    public void refreshAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            updateBoard(p);
        }
    }
}
