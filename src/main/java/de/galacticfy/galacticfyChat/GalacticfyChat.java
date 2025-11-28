package de.galacticfy.galacticfyChat;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import de.galacticfy.galacticfyChat.listener.ChatListener;
import de.galacticfy.galacticfyChat.listener.JoinQuitListener;
import de.galacticfy.galacticfyChat.listener.NametagListener;
import de.galacticfy.galacticfyChat.npc.NpcManager;
import de.galacticfy.galacticfyChat.npc.NpcRepository;
import de.galacticfy.galacticfyChat.npc.NpcPosCommand;
import de.galacticfy.galacticfyChat.punish.PunishmentReader;
import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import de.galacticfy.galacticfyChat.scoreboard.GlobalScoreboardService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class GalacticfyChat extends JavaPlugin {

    private DatabaseManager databaseManager;
    private SimpleRankService rankService;
    private GlobalScoreboardService scoreboardService;
    private PunishmentReader punishmentReader;

    private BukkitTask refreshTask;

    private NpcRepository npcRepository;
    private NpcManager npcManager;

    private static GalacticfyChat instance;
    public static GalacticfyChat getInstance() { return instance; }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("[GalacticfyChat] startet...");

        // DB
        this.databaseManager = new DatabaseManager(getLogger());
        this.databaseManager.init();

        // Services
        this.rankService = new SimpleRankService(databaseManager, getLogger());
        this.scoreboardService = new GlobalScoreboardService(rankService);
        this.punishmentReader = new PunishmentReader(databaseManager, getLogger());

        // 🔹 NPC-System
        this.npcRepository = new NpcRepository(databaseManager, getLogger());
        String serverName = getServer().getName(); // z.B. Lobby-1

        this.npcManager = new NpcManager(this, npcRepository, serverName);
        npcManager.loadAndSpawnAll();

        // Listener
        getServer().getPluginManager().registerEvents(
                new ChatListener(rankService, punishmentReader), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(), this);
        getServer().getPluginManager().registerEvents(new NametagListener(rankService), this);

        // Bereits online (z.B. nach /reload)
        Bukkit.getOnlinePlayers().forEach(p -> {
            rankService.updateNametag(p);
            scoreboardService.updateBoard(p);
        });

        // Auto-Refresh: alle 5 Sekunden
        refreshTask = Bukkit.getScheduler().runTaskTimer(
                this,
                () -> {
                    try {
                        rankService.refreshAllOnline();
                        scoreboardService.refreshAll();
                    } catch (Exception ex) {
                        getLogger().warning("[GalacticfyChat] Fehler beim Auto-Refresh: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                },
                20L,   // erster Lauf nach 1 Sekunde
                100L   // danach alle 5 Sekunden
        );

        // 🔹 Command-Executor für /npcpos registrieren
        if (getCommand("npcpos") != null) {
            getCommand("npcpos").setExecutor(
                    new NpcPosCommand(this, npcRepository, npcManager, serverName)
            );
        } else {
            getLogger().warning("[GalacticfyChat] Command 'npcpos' ist nicht in plugin.yml registriert!");
        }

        getLogger().info("[GalacticfyChat] wurde aktiviert.");
    }

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        if (npcManager != null) {
            npcManager.despawnAll();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("[GalacticfyChat] wurde deaktiviert.");
    }

    public SimpleRankService getRankService() {
        return rankService;//test
    }

    public GlobalScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    // ------------------------------------------------------------
    // /npcreload Command (geht über onCommand, NICHT extra Executor)s
    // ------------------------------------------------------------
    @Override
    public boolean onCommand(CommandSender sender,
                             Command command,
                             String label,
                             String[] args) {
        if (command.getName().equalsIgnoreCase("npcreload")) {
            if (!sender.hasPermission("galacticfy.npc.reload")) {
                sender.sendMessage("§cDazu hast du keine Berechtigung.");
                return true;
            }

            if (npcManager == null) {
                sender.sendMessage("§cNPC-System ist noch nicht initialisiert.");
                return true;
            }

            npcManager.despawnAll();
            npcManager.loadAndSpawnAll();
            sender.sendMessage("§aNPCs wurden neu geladen.");
            return true;
        }

        return false;
    }
}
