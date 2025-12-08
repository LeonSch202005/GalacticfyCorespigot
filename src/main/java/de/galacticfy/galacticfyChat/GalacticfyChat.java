package de.galacticfy.galacticfyChat;

import de.galacticfy.galacticfyChat.db.DatabaseManager;
import de.galacticfy.galacticfyChat.listener.*;
import de.galacticfy.galacticfyChat.npc.*;
import de.galacticfy.galacticfyChat.punish.PunishmentReader;
import de.galacticfy.galacticfyChat.quest.*;
import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import de.galacticfy.galacticfyChat.scoreboard.GlobalScoreboardService;
import de.galacticfy.galacticfyChat.listener.QuestWalkListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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

        npcManager.loadAndSpawnAll();
        npcManager.startLookTask();

        // ============================
        // Plugin-Message Channels
        // ============================

        // BungeeCord (falls du das noch nutzt)
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // Quest-GUI (Proxy -> Spigot)
        QuestGuiListener questGui = new QuestGuiListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "galacticfy:quests", questGui);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "galacticfy:quests");

        // Stats für Quests (Spigot -> Proxy)
        QuestEventSender questEventSender = new QuestEventSender(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "galacticfy:queststats");

        // ============================
        // Listener – Quests
        // ============================

        // GUI
        getServer().getPluginManager().registerEvents(questGui, this);

        // Admin-GUI (/questadmin)
        QuestAdminGui questAdminGui = new QuestAdminGui(this);
        getServer().getPluginManager().registerEvents(questAdminGui, this);
        if (getCommand("questadmin") != null) {
            getCommand("questadmin").setExecutor(questAdminGui);
        } else {
            getLogger().warning("[GalacticfyChat] Command 'questadmin' ist nicht in plugin.yml registriert!");
        }

        // Allgemeine Stats (Playtime, Pflanz-Abuse-Schutz, einige Blöcke, etc.)
        getServer().getPluginManager().registerEvents(new QuestStatsListener(questEventSender), this);

        // Walk-Quests (Laufen)
        getServer().getPluginManager().registerEvents(
                new QuestWalkListener(questEventSender),
                this
        );

        // Combat-Quests (Mobs / Zombies / Creeper / PvP / Death)
        getServer().getPluginManager().registerEvents(new QuestCombatListener(questEventSender), this);

        // Crafting-Quests
        getServer().getPluginManager().registerEvents(new CraftingListener(questEventSender), this);

        // Smelt-Quests
        getServer().getPluginManager().registerEvents(new FurnaceSmeltListener(questEventSender), this);

        // Villager-Trades (inkl. Shift-Klick)
        getServer().getPluginManager().registerEvents(new VillagerTradeListener(questEventSender), this);

        // Fischen (FISH-Stat)
        getServer().getPluginManager().registerEvents(new FishingListener(questEventSender), this);

        // KEINE Community-Listener / quest_events mehr!

        // ============================
        // Listener (Chat / Join / NPCs etc.)
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
        for (Player p : Bukkit.getOnlinePlayers()) {
            rankService.updateNametag(p);
            scoreboardService.updateBoard(p);
        }

        // ============================
        // Auto-Refresh (Ranks + Scoreboard)
        // ============================
        refreshTask = Bukkit.getScheduler().runTaskTimer(
                this,
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            rankService.refreshAllOnline();
                            scoreboardService.refreshAll();
                        } catch (Exception ex) {
                            getLogger().warning("[GalacticfyChat] Fehler beim Auto-Refresh: " + ex.getMessage());
                            ex.printStackTrace();
                        }
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
    // /npcreload Command
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
