package de.galacticfy.galacticfyChat;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import de.galacticfy.galacticfyChat.listener.ChatListener;
import de.galacticfy.galacticfyChat.listener.JoinQuitListener;
import de.galacticfy.galacticfyChat.listener.NametagListener;
import de.galacticfy.galacticfyChat.npc.*;
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

    public NpcManager getNpcManager() {
        return npcManager;
    }

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("[GalacticfyChat] startet...");

        // ============================
        // DB
        // ============================
        this.databaseManager = new DatabaseManager(getLogger());
        this.databaseManager.init();

        // ============================
        // Services
        // ============================
        this.rankService = new SimpleRankService(databaseManager, getLogger());
        this.scoreboardService = new GlobalScoreboardService(rankService);
        this.punishmentReader = new PunishmentReader(databaseManager, getLogger());

        // ============================
        // NPC-System
        // ============================
        this.npcRepository = new NpcRepository(databaseManager, getLogger());

        String serverName = getServer().getName(); // z.B. Lobby-1
        this.npcManager = new NpcManager(this, npcRepository, serverName);

        // NPCs direkt beim Start laden + LookTask
        npcManager.loadAndSpawnAll();
        npcManager.startLookTask();

        // ============================
        // BungeeCord-Channel
        // ============================
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // ============================
        // Listener
        // ============================
        getServer().getPluginManager().registerEvents(
                new ChatListener(rankService, punishmentReader), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(), this);
        getServer().getPluginManager().registerEvents(new NametagListener(rankService), this);
        getServer().getPluginManager().registerEvents(new NpcInteractListener(npcManager), this);
        getServer().getPluginManager().registerEvents(new NpcProtectionListener(npcManager), this);
        getServer().getPluginManager().registerEvents(new NpcConnectGui(), this);

        // ============================
        // Bereits online (z.B. nach /reload)
        // ============================
        Bukkit.getOnlinePlayers().forEach(p -> {
            rankService.updateNametag(p);
            scoreboardService.updateBoard(p);
        });

        // ============================
        // Auto-Refresh (Ranks + Scoreboard)
        // ============================
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
                20L,   // 1 Sekunde
                100L   // alle 5 Sekunden
        );

        // ============================
        // /npc Command
        // ============================
        if (getCommand("npc") != null) {
            NpcCommand npcCommand = new NpcCommand(this, npcRepository, npcManager, serverName);
            getCommand("npc").setExecutor(npcCommand);
            getCommand("npc").setTabCompleter(npcCommand);
        } else {
            getLogger().warning("[GalacticfyChat] Command 'npc' ist nicht in plugin.yml registriert!");
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
            npcManager.stopLookTask();
            npcManager.despawnAll();
        }

        if (databaseManager != null) {
            databaseManager.shutdown();
        }

        getLogger().info("[GalacticfyChat] wurde deaktiviert.");
    }

    public SimpleRankService getRankService() {
        return rankService;
    }

    public GlobalScoreboardService getScoreboardService() {
        return scoreboardService;
    }

    // ------------------------------------------------------------
    // /npcreload Command (geht über onCommand, NICHT extra Executor)
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
