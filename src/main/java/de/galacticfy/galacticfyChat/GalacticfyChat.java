package de.galacticfy.galacticfyChat;

import de.galacticfy.galacticfyChat.command.BackCommand;
import de.galacticfy.galacticfyChat.command.GamemodeCommand;
import de.galacticfy.galacticfyChat.command.SetSpawnCommand;
import de.galacticfy.galacticfyChat.command.SpawnCommand;
import de.galacticfy.galacticfyChat.db.DatabaseManager;
import de.galacticfy.galacticfyChat.listener.*;
import de.galacticfy.galacticfyChat.npc.*;
import de.galacticfy.galacticfyChat.permission.SpigotStarPermissionListener;
import de.galacticfy.galacticfyChat.punish.PunishmentReader;
import de.galacticfy.galacticfyChat.quest.*;
import de.galacticfy.galacticfyChat.rank.SimpleRankService;
import de.galacticfy.galacticfyChat.scoreboard.GlobalScoreboardService;
import de.galacticfy.galacticfyChat.spawn.BackService;
import de.galacticfy.galacticfyChat.spawn.SpawnService;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Properties;
import java.io.FileInputStream;

import java.io.File;
import java.io.FileInputStream;
import java.util.Locale;

public class GalacticfyChat extends JavaPlugin {

    private DatabaseManager databaseManager;
    private SimpleRankService rankService;
    private GlobalScoreboardService scoreboardService;
    private PunishmentReader punishmentReader;

    private BukkitTask refreshTask;

    private NpcRepository npcRepository;
    private NpcManager npcManager;

    private SpigotStarPermissionListener starPermissionListener;

    // Spawn + Back
    private SpawnService spawnService;
    private BackService backService;

    private static GalacticfyChat instance;
    public static GalacticfyChat getInstance() { return instance; }

    public NpcManager getNpcManager() {
        return npcManager;
    }

    public SpawnService getSpawnService() {
        return spawnService;
    }

    public BackService getBackService() {
        return backService;
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

        // Server-Name aus Bukkit (ohne server.properties / config)
        String serverName = resolveServerName();              // z.B. "Paper", "Lobby-1", "Citybuild-1"
        getLogger().info("[GalacticfyChat] Logischer Servername: " + serverName);

        String serverId;
        try {
            serverId = new java.io.File(".").getCanonicalFile().getName(); // z.B. Lobby-1, Citybuild-1
        } catch (Exception e) {
            serverId = "unknown";
        }
        getLogger().info("[GalacticfyChat] Server-ID = " + serverId);

        // Spawn + Back
        this.spawnService = new de.galacticfy.galacticfyChat.spawn.SpawnService(this, databaseManager, serverId);

        // Back: Warmup 5s, Cooldown 15s, DB-write debounce 2000ms
        this.backService  = new BackService(
                this,
                databaseManager,
                serverId,
                5,      // Warmup: 5s
                15,     // Cooldown: 15s
                2000    // DB debounce
        );

        // ============================
        // NPC-System
        // ============================
        this.npcRepository = new NpcRepository(databaseManager, getLogger());
        this.npcManager = new NpcManager(this, npcRepository, serverName);
        npcManager.loadAndSpawnAll();
        npcManager.startLookTask();

        // ============================
        // Plugin-Message Channels (global)
        // ============================
        getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");

        // ============================
        // QUEST-SYSTEM (hier aktiv)
        // ============================
        getLogger().info("[GalacticfyChat] Quest-System ist auf diesem Server AKTIV.");

        // Quest-GUI (Proxy -> Spigot)
        QuestGuiListener questGui = new QuestGuiListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "galacticfy:quests", questGui);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "galacticfy:quests");

        // Stats für Quests (Spigot -> Proxy)
        QuestEventSender questEventSender = new QuestEventSender(this);
        getServer().getMessenger().registerOutgoingPluginChannel(this, "galacticfy:queststats");

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

        // Allgemeine Stats + Quests
        getServer().getPluginManager().registerEvents(new QuestStatsListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new QuestWalkListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new SnowballPickupListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new XmasSnowmanListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new QuestCombatListener(questEventSender), this);

        // Crafting / Smelting / Villager / Fishing
        getServer().getPluginManager().registerEvents(new CraftingListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new FurnaceSmeltListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new VillagerTradeListener(questEventSender), this);
        getServer().getPluginManager().registerEvents(new FishingListener(questEventSender), this);

        // ============================
        // Star-/ *-Rechte Sync (Spigot)
        // ============================
        this.starPermissionListener = new SpigotStarPermissionListener(databaseManager, getLogger(), this);
        getServer().getPluginManager().registerEvents(starPermissionListener, this);

        // beim Plugin-Start alle schon online Spieler syncen
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                starPermissionListener.syncStarPermissions(p);
            } catch (Exception ex) {
                getLogger().warning("[GalacticfyChat] Fehler beim Star-Sync für " + p.getName() + ": " + ex.getMessage());
                ex.printStackTrace();
            }
        }

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

        // Advancement-Chat deaktivieren
        getServer().getPluginManager().registerEvents(new AdvancementSuppressListener(), this);

        getServer().getPluginManager().registerEvents(new BackPointListener(backService), this);

        getServer().getPluginManager().registerEvents(
                new BackResetOnJoinListener(backService),
                this
        );


        // ============================
        // Join-Spawn (GLOBAL: immer zum Spawn)
        // ============================
        getServer().getPluginManager().registerEvents(
                new LobbyJoinSpawnListener(this, spawnService, backService),
                this
        );

        getLogger().info("[GalacticfyChat] Globaler JoinSpawnListener registriert (immer Spawn beim Join).");

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
                () -> {
                    try {
                        rankService.refreshAllOnline();
                        scoreboardService.refreshAll();
                    } catch (Exception ex) {
                        getLogger().warning("[GalacticfyChat] Fehler beim Auto-Refresh: " + ex.getMessage());
                        ex.printStackTrace();
                    }
                },
                20L,
                100L
        );

        // ============================
        // Commands registrieren (mit Debug)
        // ============================

        // /npc
        if (getCommand("npc") != null) {
            NpcCommand npcCommand = new NpcCommand(this, npcRepository, npcManager, serverName);
            registerCommand("npc", npcCommand, npcCommand);
        } else {
            getLogger().warning("[GalacticfyChat] Command 'npc' ist nicht in plugin.yml registriert!");
        }

        // /gamemode
        registerCommand("gamemode", new GamemodeCommand(this), null);

        // /spawn
        registerCommand("spawn", new de.galacticfy.galacticfyChat.command.SpawnCommand(spawnService, backService), null);


        // /setspawn
        registerCommand("setspawn", new SetSpawnCommand(spawnService), null);

        // Command
        registerCommand(
                "back",
                new BackCommand(backService, serverId),
                null
        );

        getLogger().info("[GalacticfyChat] wurde aktiviert.");
    }

    /**
     * Hilfs-Methode: Command + optional TabCompleter registrieren und dabei loggen,
     * ob getCommand(...) auf null läuft (Fehler in plugin.yml).
     */
    private void registerCommand(String name, CommandExecutor executor, TabCompleter tabCompleter) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().severe("[GalacticfyChat] Command '" + name + "' ist NICHT in plugin.yml registriert!");
            return;
        }
        cmd.setExecutor(executor);
        if (tabCompleter != null) {
            cmd.setTabCompleter(tabCompleter);
        }
        getLogger().info("[GalacticfyChat] Command '" + name + "' erfolgreich registriert.");
    }

    @Override
    public void onDisable() {

        if (backService != null) {
            backService.shutdown();
        }

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

    // ------------------------------------------------------------
    // Server-Name (ohne server.properties / ohne config)
    // ------------------------------------------------------------
    private String resolveServerName() {
        try {
            // 1) Versuch: server.properties lesen
            File file = new File("server.properties");
            if (file.exists()) {
                getLogger().info("[GalacticfyChat] server.properties gefunden unter: " + file.getAbsolutePath());

                Properties props = new Properties();
                try (FileInputStream fis = new FileInputStream(file)) {
                    props.load(fis);
                }

                String name = props.getProperty("server-name");
                if (name != null && !name.isEmpty() && !name.equalsIgnoreCase("Unknown Server")) {
                    getLogger().info("[GalacticfyChat] Verwende server-name aus server.properties: " + name);
                    return name;
                } else {
                    getLogger().warning("[GalacticfyChat] 'server-name' in server.properties ist leer oder 'Unknown Server'.");
                }
            } else {
                getLogger().warning("[GalacticfyChat] server.properties nicht gefunden.");
            }

            // 2) Versuch: Ordnername als Servername
            File cwd = new File(".").getCanonicalFile();
            String folderName = cwd.getName();
            if (folderName != null && !folderName.isEmpty()) {
                getLogger().info("[GalacticfyChat] Verwende Server-Ordnernamen als serverName: " + folderName);
                return folderName;
            }

        } catch (Exception ex) {
            getLogger().warning("[GalacticfyChat] Fehler beim Bestimmen des Servernamens: " + ex.getMessage());
            ex.printStackTrace();
        }

        // 3) Fallback: Bukkit-Servername (Paper / Spigot / etc.)
        String fallback = getServer().getName();
        getLogger().warning("[GalacticfyChat] Fallback auf Bukkit-Servername: " + fallback);
        return fallback;
    }

}
