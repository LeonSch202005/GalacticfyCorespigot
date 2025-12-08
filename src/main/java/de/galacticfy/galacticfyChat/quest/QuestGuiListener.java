package de.galacticfy.galacticfyChat.quest;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.bukkit.scheduler.BukkitRunnable;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * Quest-GUI:
 *
 *  - Hauptmenü (Daily/Weekly/Monthly/Lifetime/Event)
 *  - Untermenü mit Fortschrittsbalken + Pagination
 *  - Daily-Countdown "Reset in: Xh Ym" mit Live-Update (Europe/Berlin)
 *  - Event-Modus: farbiger Rahmen (Fullscreen-Overlay-Gefühl)
 *  - Belohnungen werden AUTOMATISCH gutgeschrieben, sobald eine Quest
 *    auf dem Proxy als "completed" markiert wird.
 *  - Kein Claim-System mehr, Klick auf fertige Quest gibt nur Info/Sound.
 *
 *  Channel: "galacticfy:quests"
 */
public class QuestGuiListener implements Listener, PluginMessageListener {

    private static final String CHANNEL = "galacticfy:quests";

    private final Plugin plugin;

    // Quests vom Proxy
    private final Map<UUID, List<QuestEntry>> questsPerPlayer = new HashMap<>();
    // Welcher Screen offen?
    private final Map<UUID, ViewState> viewState = new HashMap<>();
    // Seite pro Spieler & Typ
    private final Map<UUID, Map<QuestType, Integer>> pageState = new HashMap<>();
    // Für "Sound nur einmal pro Quest" (pro Spieler + Quest-Key)
    private final Map<UUID, Set<String>> completedQuestSoundPlayed = new HashMap<>();

    // Slots, in denen Quests angezeigt werden (2 Reihen in der Mitte)
    private static final int[] QUEST_SLOTS = {
            11, 12, 13, 14, 15,
            20, 21, 22, 23, 24
    };

    private static final int QUESTS_PER_PAGE = QUEST_SLOTS.length;

    // Event-Mode (Overlay-Rahmen)
    private static final boolean EVENT_MODE_ENABLED = true; // bei Bedarf über Config

    // Live-Update Task (Daily-Zeit etc.)
    private final BukkitRunnable liveUpdateTask;

    private enum ViewState {
        MAIN,
        DAILY,
        WEEKLY,
        MONTHLY,
        LIFETIME,
        EVENT
    }

    public enum QuestType {
        DAILY,
        WEEKLY,
        MONTHLY,
        LIFETIME,
        EVENT
    }

    private static final class QuestEntry {
        String key;
        String title;
        String description;
        QuestType type;
        long goal;
        long progress;
        long rewardGalas;
        long rewardStardust;
        boolean completed;
    }

    // =====================================================================
    // CONSTRUCTOR
    // =====================================================================

    public QuestGuiListener(Plugin plugin) {
        this.plugin = plugin;

        // Live-Update jede Sekunde:
        //  - Daily-Icon im Hauptmenü (Slot 10)
        //  - Info-Item im Daily-Menü (Slot 4)
        this.liveUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getOpenInventory() == null ||
                            p.getOpenInventory().getTopInventory() == null) {
                        continue;
                    }

                    Inventory top = p.getOpenInventory().getTopInventory();
                    String title = p.getOpenInventory().getTitle();
                    UUID uuid = p.getUniqueId();

                    List<QuestEntry> all = questsPerPlayer
                            .getOrDefault(uuid, Collections.emptyList());

                    // Daily-Stats berechnen
                    long[] dailyStats = countByType(all, QuestType.DAILY);
                    long dailyDone = dailyStats[0];
                    long dailyTotal = dailyStats[1];

                    // Hauptmenü: Daily-Icon updaten
                    if ("Quests".equals(title)) {
                        top.setItem(10, createDailyCategoryItem(dailyDone, dailyTotal));
                    }

                    // Daily-Quest-Liste: Info-Item (Slot 4) updaten
                    if ("Quests | Daily".equals(title)) {
                        top.setItem(4, createInfoItem(QuestType.DAILY, dailyDone, dailyTotal));
                    }
                }
            }
        };
        this.liveUpdateTask.runTaskTimer(plugin, 20L, 20L); // alle 1 Sekunde
    }

    // =====================================================================
    // PluginMessage
    // =====================================================================

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        String payload = new String(message, StandardCharsets.UTF_8);
        String[] lines = payload.split("\n");
        if (lines.length == 0) return;

        String mode = lines[0].trim(); // OPEN oder UPDATE
        List<QuestEntry> list = new ArrayList<>();

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\|", -1);
            // ERWARTET: mindestens 9 Felder (claimed ist optional als 10. Feld)
            // key|title|desc|type|goal|progress|galas|stardust|completed[|claimed]
            if (parts.length < 9) {
                Bukkit.getLogger().warning("[Quests] Ignoriere Zeile (zu wenige Felder): " + line);
                continue;
            }

            String key        = parts[0];
            String title      = parts[1];
            String desc       = parts[2];
            String typeString = parts[3];

            long goal         = parseLongSafe(parts[4]);
            long progress     = parseLongSafe(parts[5]);
            long rewardGalas  = parseLongSafe(parts[6]);
            long rewardDust   = parseLongSafe(parts[7]);
            boolean completed = "1".equals(parts[8]);
            // Optional: parts[9] = claimed -> wird NICHT mehr benutzt, Reward ist auto

            QuestType type;
            try {
                type = QuestType.valueOf(typeString.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                // Unbekannt / COMMUNITY -> ignorieren
                Bukkit.getLogger().warning("[Quests] Unbekannter Quest-Typ: " + typeString + " in Zeile: " + line);
                continue;
            }

            QuestEntry e = new QuestEntry();
            e.key = key;
            e.title = title;
            e.description = desc;
            e.type = type;
            e.goal = goal;
            e.progress = progress;
            e.rewardGalas = rewardGalas;
            e.rewardStardust = rewardDust;
            e.completed = completed;
            list.add(e);
        }

        questsPerPlayer.put(player.getUniqueId(), list);

        if (mode.equalsIgnoreCase("OPEN")) {
            openMainMenu(player);
            return;
        }

        // UPDATE => aktuellen Screen refreshen (Live-Update vom Proxy)
        ViewState vs = viewState.get(player.getUniqueId());
        if (vs == null || vs == ViewState.MAIN) {
            if (player.getOpenInventory() != null &&
                    player.getOpenInventory().getTopInventory() != null &&
                    "Quests".equals(player.getOpenInventory().getTitle())) {
                openMainMenu(player);
            }
        } else {
            QuestType type = stateToType(vs);
            if (type != null &&
                    player.getOpenInventory() != null &&
                    player.getOpenInventory().getTopInventory() != null &&
                    player.getOpenInventory().getTitle().startsWith("Quests |")) {
                openQuestList(player, type);
            }
        }
    }

    private long parseLongSafe(String s) {
        try {
            return Long.parseLong(s);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    // =====================================================================
    // MAIN MENU
    // =====================================================================

    private void openMainMenu(Player p) {
        final Inventory inv = Bukkit.createInventory(p, 3 * 9, "Quests");

        // Event-Rahmen zuerst
        applyEventFrame(inv);

        UUID uuid = p.getUniqueId();
        List<QuestEntry> all = questsPerPlayer
                .getOrDefault(uuid, Collections.emptyList());

        long[] dailyStats    = countByType(all, QuestType.DAILY);
        long[] weeklyStats   = countByType(all, QuestType.WEEKLY);
        long[] monthlyStats  = countByType(all, QuestType.MONTHLY);
        long[] lifetimeStats = countByType(all, QuestType.LIFETIME);
        long[] eventStats    = countByType(all, QuestType.EVENT);

        // Close-Item
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cSchließen");
        cm.setLore(Collections.singletonList("§7Klicke, um das Menü zu schließen."));
        close.setItemMeta(cm);
        inv.setItem(26, close);

        // Übersicht-Icon
        ItemStack overview = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta om = overview.getItemMeta();
        om.setDisplayName("§aQuest-Übersicht");
        om.setLore(Arrays.asList(
                "§7Alle deine Quests im Überblick.",
                "§7Daily, Weekly, Monthly & mehr.",
                " ",
                "§eTipp: §7Schließe Quests ab, um",
                "§eGalas §7und §dStardust §7zu verdienen!"
        ));
        overview.setItemMeta(om);
        inv.setItem(4, overview);

        // Kategorien
        inv.setItem(10, createDailyCategoryItem(dailyStats[0], dailyStats[1]));
        inv.setItem(12, createCategoryItem(
                "Weekly-Quests",
                "§7Wöchentliche Aufgaben.",
                Material.SUNFLOWER,
                QuestType.WEEKLY,
                weeklyStats[0],
                weeklyStats[1]
        ));
        inv.setItem(14, createCategoryItem(
                "Monthly-Quests",
                "§7Monatliche Herausforderungen.",
                Material.NETHER_STAR,
                QuestType.MONTHLY,
                monthlyStats[0],
                monthlyStats[1]
        ));
        inv.setItem(16, createCategoryItem(
                "Lifetime-Quests",
                "§7Langzeit-Ziele.",
                Material.TOTEM_OF_UNDYING,
                QuestType.LIFETIME,
                lifetimeStats[0],
                lifetimeStats[1]
        ));
        inv.setItem(22, createCategoryItem(
                "Event-Quests",
                "§7Spezielle Event-Aufgaben.",
                Material.FIREWORK_ROCKET,
                QuestType.EVENT,
                eventStats[0],
                eventStats[1]
        ));

        viewState.put(p.getUniqueId(), ViewState.MAIN);
        pageState.remove(p.getUniqueId());

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);

        // Animierte Kategorie-Items (kleine "Reveal"-Animation)
        final List<Map.Entry<Integer, ItemStack>> steps = new ArrayList<>();
        steps.add(new AbstractMap.SimpleEntry<>(10, inv.getItem(10)));
        steps.add(new AbstractMap.SimpleEntry<>(12, inv.getItem(12)));
        steps.add(new AbstractMap.SimpleEntry<>(14, inv.getItem(14)));
        steps.add(new AbstractMap.SimpleEntry<>(16, inv.getItem(16)));
        steps.add(new AbstractMap.SimpleEntry<>(22, inv.getItem(22)));

        // Erst mal Slots leeren, Animation setzt sie wieder ein
        inv.setItem(10, null);
        inv.setItem(12, null);
        inv.setItem(14, null);
        inv.setItem(16, null);
        inv.setItem(22, null);

        new BukkitRunnable() {
            int i = 0;
            @Override
            public void run() {
                if (!p.isOnline() ||
                        p.getOpenInventory() == null ||
                        !"Quests".equals(p.getOpenInventory().getTitle())) {
                    cancel();
                    return;
                }

                if (i >= steps.size()) {
                    cancel();
                    return;
                }

                Map.Entry<Integer, ItemStack> step = steps.get(i++);
                inv.setItem(step.getKey(), step.getValue());
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.6f);
            }
        }.runTaskTimer(plugin, 2L, 2L);
    }

    private long[] countByType(List<QuestEntry> list, QuestType type) {
        long done = 0;
        long total = 0;
        for (QuestEntry q : list) {
            if (q.type != type) continue;
            total++;
            if (q.completed) done++;
        }
        return new long[]{done, total};
    }

    private ItemStack createDailyCategoryItem(long done, long total) {
        ItemStack item = new ItemStack(Material.CLOCK);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§dDaily-Quests");

        List<String> lore = new ArrayList<>();
        lore.add("§7Tägliche Aufgaben für jeden Tag.");
        lore.add(" ");

        if (total > 0) {
            lore.add("§7Abgeschlossen: " +
                    (done > 0 ? "§a" + done : "§c" + done) + "§7/§f" + total);
        } else {
            lore.add("§7Abgeschlossen: §c0§7/§f0");
        }

        lore.add(formatDailyResetCountdown());
        lore.add(" ");
        lore.add("§7Klicke, um die Daily-Quests zu öffnen.");
        meta.setLore(lore);

        // Glow, wenn alle Dailies fertig
        if (total > 0 && done >= total) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    private String formatDailyResetCountdown() {
        // Feste Zeitzone Europe/Berlin
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Berlin"));
        ZonedDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("Europe/Berlin"));

        long seconds = Duration.between(now, nextMidnight).getSeconds();
        if (seconds < 0) seconds = 0;

        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        return "§7Reset in: §e" + hours + "h " + minutes + "m";
    }

    private ItemStack createCategoryItem(String name,
                                         String desc,
                                         Material mat,
                                         QuestType type,
                                         long done,
                                         long total) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        String color;
        String resetLine;
        switch (type) {
            case WEEKLY -> {
                color = "§e";
                resetLine = "§7Reset: §eWöchentlich (Montag 00:00 Uhr)";
            }
            case MONTHLY -> {
                color = "§b";
                resetLine = "§7Reset: §eMonatlich (1. des Monats)";
            }
            case LIFETIME -> {
                color = "§a";
                resetLine = "§7Reset: §eNiemals (Lebenszeit-Ziele)";
            }
            case EVENT -> {
                color = "§c";
                resetLine = "§7Event-Zeitraum: §eBegrenzte Zeit";
            }
            default -> {
                color = "§d";
                resetLine = "";
            }
        }

        meta.setDisplayName(color + name);

        List<String> lore = new ArrayList<>();
        lore.add(desc);
        lore.add(" ");

        if (total > 0) {
            lore.add("§7Abgeschlossen: " +
                    (done > 0 ? "§a" + done : "§c" + done) + "§7/§f" + total);
        } else {
            lore.add("§7Abgeschlossen: §c0§7/§f0");
        }

        if (!resetLine.isEmpty()) {
            lore.add(resetLine);
        }

        lore.add(" ");
        lore.add("§7Klicke, um diese Kategorie zu öffnen.");
        meta.setLore(lore);

        // Glow, wenn alles fertig
        if (total > 0 && done >= total) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);

        item.setItemMeta(meta);
        return item;
    }

    // =====================================================================
    // QUEST LIST + PAGING
    // =====================================================================

    private int getPage(UUID uuid, QuestType type) {
        Map<QuestType, Integer> map = pageState.get(uuid);
        if (map == null) return 0;
        Integer v = map.get(type);
        return v == null ? 0 : v;
    }

    private void setPage(UUID uuid, QuestType type, int page) {
        Map<QuestType, Integer> map = pageState.get(uuid);
        if (map == null) {
            map = new EnumMap<>(QuestType.class);
            pageState.put(uuid, map);
        }
        if (page < 0) page = 0;
        map.put(type, page);
    }

    private void openQuestList(Player p, QuestType type) {
        openQuestList(p, type, getPage(p.getUniqueId(), type));
    }

    private void openQuestList(Player p, QuestType type, int page) {
        final String title = "Quests | " + niceTypeName(type);
        final Inventory inv = Bukkit.createInventory(p, 5 * 9, title);

        // Event-Rahmen
        applyEventFrame(inv);

        List<QuestEntry> allQuests =
                questsPerPlayer.getOrDefault(p.getUniqueId(), Collections.emptyList());

        // Nur dieser Typ
        List<QuestEntry> ofType = new ArrayList<>();
        for (QuestEntry q : allQuests) {
            if (q.type == type) {
                ofType.add(q);
            }
        }

        int total = ofType.size();
        int maxPage = (total == 0) ? 0 : (total - 1) / QUESTS_PER_PAGE;
        if (page > maxPage) page = maxPage;
        if (page < 0) page = 0;
        setPage(p.getUniqueId(), type, page);

        long done = 0;
        for (QuestEntry q : ofType) {
            if (q.completed) done++;
        }

        // Kopfzeile: Zurück (0), Info (4), Schließen (8)
        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta bm = back.getItemMeta();
        bm.setDisplayName("§7Zurück");
        bm.setLore(Collections.singletonList("§7Zurück zur Übersicht."));
        back.setItemMeta(bm);
        inv.setItem(0, back);

        inv.setItem(4, createInfoItem(type, done, total));

        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName("§cSchließen");
        cm.setLore(Collections.singletonList("§7Klicke, um das Menü zu schließen."));
        close.setItemMeta(cm);
        inv.setItem(8, close);

        // Quests einsammeln (mit Animation)
        final List<Map.Entry<Integer, ItemStack>> toAnimate = new ArrayList<>();

        if (total == 0) {
            ItemStack none = new ItemStack(Material.GRAY_DYE);
            ItemMeta nm = none.getItemMeta();
            nm.setDisplayName("§7Keine Quests verfügbar");
            nm.setLore(Arrays.asList(
                    "§7Für diese Kategorie gibt es",
                    "§7momentan keine aktiven Quests."
            ));
            none.setItemMeta(nm);
            toAnimate.add(new AbstractMap.SimpleEntry<>(22, none));
        } else {
            int startIndex = page * QUESTS_PER_PAGE;
            int endIndex = Math.min(startIndex + QUESTS_PER_PAGE, total);
            int questsOnPage = endIndex - startIndex;

            if (questsOnPage <= 5) {
                // Wenige Quests -> zentriert in der UNTEREN Reihe (20–24)
                int[] centerRow = {20, 21, 22, 23, 24};
                int offset = (centerRow.length - questsOnPage) / 2;

                int idx = 0;
                for (int i = startIndex; i < endIndex; i++) {
                    QuestEntry q = ofType.get(i);
                    int slot = centerRow[offset + idx++];
                    toAnimate.add(new AbstractMap.SimpleEntry<>(slot, createQuestItem(q)));
                }
            } else {
                // Voller Block -> alle QUEST_SLOTS verwenden (11–15,20–24)
                int idx = 0;
                for (int i = startIndex; i < endIndex && idx < QUEST_SLOTS.length; i++, idx++) {
                    QuestEntry q = ofType.get(i);
                    int slot = QUEST_SLOTS[idx];
                    toAnimate.add(new AbstractMap.SimpleEntry<>(slot, createQuestItem(q)));
                }
            }
        }

        // Pagination-Items
        if (maxPage > 0) {
            ItemStack pageInfo = new ItemStack(Material.PAPER);
            ItemMeta pm = pageInfo.getItemMeta();
            pm.setDisplayName("§7Seite §e" + (page + 1) + "§7/§e" + (maxPage + 1));
            pageInfo.setItemMeta(pm);
            inv.setItem(40, pageInfo);

            if (page > 0) {
                ItemStack prev = new ItemStack(Material.ARROW);
                ItemMeta prevMeta = prev.getItemMeta();
                prevMeta.setDisplayName("§7Vorherige Seite");
                prev.setItemMeta(prevMeta);
                inv.setItem(36, prev);
            }

            if (page < maxPage) {
                ItemStack next = new ItemStack(Material.ARROW);
                ItemMeta nextMeta = next.getItemMeta();
                nextMeta.setDisplayName("§7Nächste Seite");
                next.setItemMeta(nextMeta);
                inv.setItem(44, next);
            }
        }

        viewState.put(p.getUniqueId(), typeToState(type));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.6f, 1.4f);

        // Animation
        if (!toAnimate.isEmpty()) {
            new BukkitRunnable() {
                int i = 0;
                @Override
                public void run() {
                    if (!p.isOnline() ||
                            p.getOpenInventory() == null ||
                            !title.equals(p.getOpenInventory().getTitle())) {
                        cancel();
                        return;
                    }
                    if (i >= toAnimate.size()) {
                        cancel();
                        return;
                    }
                    Map.Entry<Integer, ItemStack> e = toAnimate.get(i++);
                    inv.setItem(e.getKey(), e.getValue());
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.25f, 1.8f);
                }
            }.runTaskTimer(plugin, 2L, 1L);
        }
    }

    private ItemStack createInfoItem(QuestType type, long done, long total) {
        ItemStack info = new ItemStack(Material.BOOK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName("§d" + niceTypeName(type) + "-Quests");

        List<String> infoLore = new ArrayList<>();
        infoLore.add("§7Hier siehst du alle §f" + niceTypeName(type) + "§7-Quests.");
        infoLore.add(" ");
        infoLore.add("§7Abgeschlossen: §a" + done + "§7/§f" + total);

        if (type == QuestType.DAILY) {
            infoLore.add(formatDailyResetCountdown());
        } else if (type == QuestType.WEEKLY) {
            infoLore.add("§7Reset: §eWöchentlich (Montag 00:00 Uhr)");
        } else if (type == QuestType.MONTHLY) {
            infoLore.add("§7Reset: §eMonatlich (1. des Monats)");
        }

        infoLore.add(" ");
        infoLore.add("§8Fortschritt aktualisiert sich automatisch.");

        im.setLore(infoLore);
        info.setItemMeta(im);
        return info;
    }

    // ---------- Quest-Item (Book/Emerald + Fortschrittsbalken) ----------

    private ItemStack createQuestItem(QuestEntry q) {
        // Wieder wie früher: Buch für offen, Emerald für fertig
        Material mat = q.completed ? Material.EMERALD_BLOCK : Material.WRITABLE_BOOK;

        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();

        if (q.completed) {
            im.setDisplayName("§a" + q.title);
        } else {
            im.setDisplayName("§f" + q.title);
        }

        List<String> lore = new ArrayList<>();
        lore.add("§7" + q.description);
        lore.add(" ");

        lore.add("§7Fortschritt: §e" + q.progress + " §8/ §e" + q.goal +
                " §7(§e" + progressPercent(q.progress, q.goal) + "%§7)");
        lore.add("§8" + buildProgressBar(q.progress, q.goal));

        lore.add("§7Belohnung: §e" + q.rewardGalas + "⛃ §7/ §d" + q.rewardStardust + "✧");
        lore.add("§7Status: " + (q.completed ? "§a✔ Abgeschlossen" : "§cOffen"));
        lore.add(" ");

        if (q.completed) {
            lore.add("§7Deine Belohnung wurde");
            lore.add("§7automatisch gutgeschrieben.");
        } else {
            lore.add("§7Spiele weiter, um diese Quest");
            lore.add("§7abzuschließen!");
        }

        lore.add(" ");
        lore.add("§8Key: " + q.key);

        im.setLore(lore);
        im.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES);
        it.setItemMeta(im);
        return it;
    }

    private String buildProgressBar(long progress, long goal) {
        int length = 20;
        if (goal <= 0) return "§a[" + repeat("█", length) + "§8]";

        double ratio = Math.min(1.0, Math.max(0.0, (double) progress / (double) goal));
        int filled = (int) Math.round(ratio * length);
        if (filled > length) filled = length;

        StringBuilder sb = new StringBuilder();
        sb.append("[");

        for (int i = 0; i < length; i++) {
            if (i < filled) {
                if (ratio < 0.33) {
                    sb.append("§c█");
                } else if (ratio < 0.66) {
                    sb.append("§e█");
                } else {
                    sb.append("§a█");
                }
            } else {
                sb.append("§7░");
            }
        }

        sb.append("§8]");
        return sb.toString();
    }

    private String repeat(String s, int count) {
        StringBuilder sb = new StringBuilder(s.length() * count);
        for (int i = 0; i < count; i++) {
            sb.append(s);
        }
        return sb.toString();
    }

    private int progressPercent(long progress, long goal) {
        if (goal <= 0) return 100;
        double r = (double) progress / (double) goal;
        if (r < 0) r = 0;
        if (r > 1) r = 1;
        return (int) Math.round(r * 100.0);
    }

    private String niceTypeName(QuestType type) {
        return switch (type) {
            case DAILY -> "Daily";
            case WEEKLY -> "Weekly";
            case MONTHLY -> "Monthly";
            case LIFETIME -> "Lifetime";
            case EVENT -> "Event";
        };
    }

    private ViewState typeToState(QuestType type) {
        return switch (type) {
            case DAILY -> ViewState.DAILY;
            case WEEKLY -> ViewState.WEEKLY;
            case MONTHLY -> ViewState.MONTHLY;
            case LIFETIME -> ViewState.LIFETIME;
            case EVENT -> ViewState.EVENT;
        };
    }

    private QuestType stateToType(ViewState state) {
        if (state == null) {
            return null;
        }

        return switch (state) {
            case DAILY -> QuestType.DAILY;
            case WEEKLY -> QuestType.WEEKLY;
            case MONTHLY -> QuestType.MONTHLY;
            case LIFETIME -> QuestType.LIFETIME;
            case EVENT -> QuestType.EVENT;
            case MAIN -> null;
        };
    }

    // =====================================================================
    // Inventory Events
    // =====================================================================

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player p = (Player) event.getWhoClicked();

        String title = event.getView().getTitle();
        if (title == null || (!title.equals("Quests") && !title.startsWith("Quests |"))) {
            return;
        }

        event.setCancelled(true);
        int slot = event.getRawSlot();

        // Hauptmenü
        if ("Quests".equals(title)) {
            if (slot == 10) { // Daily
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                openQuestList(p, QuestType.DAILY);
            } else if (slot == 12) { // Weekly
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                openQuestList(p, QuestType.WEEKLY);
            } else if (slot == 14) { // Monthly
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                openQuestList(p, QuestType.MONTHLY);
            } else if (slot == 16) { // Lifetime
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                openQuestList(p, QuestType.LIFETIME);
            } else if (slot == 22) { // Event
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.6f);
                openQuestList(p, QuestType.EVENT);
            } else if (slot == 26) {
                p.closeInventory();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
            }
            return;
        }

        // Untermenü
        if (title.startsWith("Quests |")) {
            // Zurück
            if (slot == 0) {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                openMainMenu(p);
                return;
            }

            // Schließen
            if (slot == 8) {
                p.closeInventory();
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
                return;
            }

            // Pagination
            ViewState vs = viewState.get(p.getUniqueId());
            QuestType type = stateToType(vs);

            if (type != null) {
                int currentPage = getPage(p.getUniqueId(), type);

                if (slot == 36) { // vorherige Seite
                    if (currentPage > 0) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                        openQuestList(p, type, currentPage - 1);
                    }
                    return;
                }

                if (slot == 44) { // nächste Seite
                    List<QuestEntry> all = questsPerPlayer
                            .getOrDefault(p.getUniqueId(), Collections.emptyList());
                    List<QuestEntry> ofType = new ArrayList<>();
                    for (QuestEntry q : all) {
                        if (q.type == type) {
                            ofType.add(q);
                        }
                    }

                    int count = ofType.size();
                    int maxPage = (count == 0) ? 0 : (count - 1) / QUESTS_PER_PAGE;
                    if (currentPage < maxPage) {
                        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f);
                        openQuestList(p, type, currentPage + 1);
                    }
                    return;
                }
            }

            // Klick auf eine Quest
            if (slot >= 9 && slot < event.getInventory().getSize()) {
                ItemStack clicked = event.getCurrentItem();
                if (clicked == null || clicked.getType() == Material.AIR) return;

                Material typeMat = clicked.getType();

                if (typeMat == Material.EMERALD_BLOCK) {
                    // Fertige Quest – nur Info, aber Sound nur EINMAL pro Quest
                    String questKey = extractQuestKey(clicked);
                    UUID uuid = p.getUniqueId();
                    Set<String> played = completedQuestSoundPlayed
                            .computeIfAbsent(uuid, u -> new HashSet<>());

                    if (questKey == null || !played.contains(questKey)) {
                        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
                        if (questKey != null) {
                            played.add(questKey);
                        }
                    }
                    p.sendMessage("§d[Quests] §7Diese Quest hast du bereits abgeschlossen. " +
                            "§7Deine Belohnung wurde automatisch gutgeschrieben.");
                    return;
                }

                if (typeMat == Material.GRAY_DYE ||
                        typeMat == Material.BARRIER ||
                        typeMat == Material.ARROW ||
                        typeMat == Material.PAPER) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6f, 0.8f);
                    return;
                }

                // offene Quest / Info
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.9f);
            }
        }
    }

    private String extractQuestKey(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) return null;

        List<String> lore = meta.getLore();
        if (lore == null) return null;

        for (String line : lore) {
            if (line != null && line.startsWith("§8Key: ")) {
                return line.substring("§8Key: ".length());
            }
        }
        return null;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player p = (Player) event.getPlayer();

        String title = event.getView().getTitle();
        if (title != null && (title.equals("Quests") || title.startsWith("Quests |"))) {
            viewState.remove(p.getUniqueId());
            // pageState & questsPerPlayer bleiben erhalten (für schnelles Reopen)
        }
    }

    // =====================================================================
    // Event-Mode Overlay
    // =====================================================================

    private void applyEventFrame(Inventory inv) {
        if (!EVENT_MODE_ENABLED) return;

        ItemStack paneOuter = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta mo = paneOuter.getItemMeta();
        mo.setDisplayName(" ");
        paneOuter.setItemMeta(mo);

        ItemStack paneInner = new ItemStack(Material.MAGENTA_STAINED_GLASS_PANE);
        ItemMeta mi = paneInner.getItemMeta();
        mi.setDisplayName(" ");
        paneInner.setItemMeta(mi);

        int size = inv.getSize(); // 27 oder 45
        int cols = 9;
        int rows = size / cols;

        for (int slot = 0; slot < size; slot++) {
            int row = slot / cols;
            int col = slot % cols;

            boolean isBorder =
                    row == 0 || row == rows - 1 || col == 0 || col == cols - 1;

            if (isBorder) {
                inv.setItem(slot, paneOuter);
            } else if (size >= 45) {
                // innerer Rahmen bei 5x9-Inventar
                if (row == 1 || row == rows - 2 || col == 1 || col == cols - 2) {
                    inv.setItem(slot, paneInner);
                }
            }
        }
    }
}
