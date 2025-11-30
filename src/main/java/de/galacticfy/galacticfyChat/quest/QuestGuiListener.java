package de.galacticfy.galacticfyChat.quest;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import de.galacticfy.galacticfyChat.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Empfängt Quest-Daten vom Proxy (Velocity) und zeigt
 * eine mehrseitige GUI mit Daily / Weekly / Monthly.
 */
public class QuestGuiListener implements Listener, PluginMessageListener {

    private final GalacticfyChat plugin;

    public QuestGuiListener(GalacticfyChat plugin) {
        this.plugin = plugin;

        // Task für "Reset in: XX" – jede Sekunde aktualisieren,
        // solange ein Quest-Inventar offen ist.
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (openInv.isEmpty()) return;

            for (Map.Entry<UUID, Inventory> entry : openInv.entrySet()) {
                UUID uuid = entry.getKey();
                Inventory inv = entry.getValue();
                Category cat = category.getOrDefault(uuid, Category.DAILY);

                // Slot 40 ist unser Reset-Item
                inv.setItem(40, createResetInfoItem(cat));
            }
        }, 20L, 20L);
    }

    // eine Questzeile aus dem Proxy
    private static final class QuestView {
        String key;
        String title;
        String description;
        String type; // DAILY / WEEKLY / MONTHLY
        long goal;
        long progress;
        long rewardGalas;
        long rewardStardust;
        boolean completed;
    }

    private enum Category {
        DAILY("Daily Quests"),
        WEEKLY("Weekly Quests"),
        MONTHLY("Monthly Quests");

        final String display;
        Category(String d) { display = d; }

        static Category fromType(String type) {
            try {
                return valueOf(type.toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return DAILY;
            }
        }
    }

    // pro Spieler: Liste aller Quests
    private final Map<UUID, List<QuestView>> quests = new HashMap<>();
    // pro Spieler: aktuell offene GUI + Page + Category + Slot→Quest
    private final Map<UUID, Inventory> openInv = new HashMap<>();
    private final Map<UUID, Integer> page = new HashMap<>();
    private final Map<UUID, Category> category = new HashMap<>();
    private final Map<UUID, Map<Integer, QuestView>> slotMap = new HashMap<>();

    // =========================================================
    // Plugin Message vom Proxy (Velocity)
    // =========================================================
    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!"galacticfy:quests".equalsIgnoreCase(channel)) return;
        if (player == null) return;

        String raw = new String(message, StandardCharsets.UTF_8);
        List<QuestView> list = parsePayload(raw);

        UUID uuid = player.getUniqueId();
        quests.put(uuid, list);

        // hat der Spieler bereits ein Quest-GUI offen?
        Inventory open = openInv.get(uuid);
        if (open == null) {
            // erstes Mal → Standardwerte + Inventar öffnen
            page.put(uuid, 1);
            category.put(uuid, Category.DAILY);
            openGui(player);
            player.playSound(player.getLocation(), Sound.UI_TOAST_IN, 0.6f, 1.2f);
        } else {
            // nur refresh auf aktueller Seite / Kategorie
            int currentPage = page.getOrDefault(uuid, 1);
            Category cat = category.getOrDefault(uuid, Category.DAILY);
            fillGui(player, open, currentPage, cat);
            player.updateInventory();
        }
    }


    private List<QuestView> parsePayload(String raw) {
        List<QuestView> list = new ArrayList<>();
        if (raw == null || raw.isEmpty()) return list;

        String[] lines = raw.split("\n");
        for (String line : lines) {
            String[] parts = line.split("\\|", -1);
            if (parts.length < 9) continue;

            QuestView q = new QuestView();
            q.key = parts[0];
            q.title = parts[1];
            q.description = parts[2];
            q.type = parts[3];
            try {
                q.goal = Long.parseLong(parts[4]);
                q.progress = Long.parseLong(parts[5]);
                q.rewardGalas = Long.parseLong(parts[6]);
                q.rewardStardust = Long.parseLong(parts[7]);
                q.completed = "1".equals(parts[8]);
            } catch (NumberFormatException ignored) {}

            list.add(q);
        }
        return list;
    }

    // =========================================================
    // GUI zeichnen
    // =========================================================

    private void openGui(Player p) {
        int currentPage = page.getOrDefault(p.getUniqueId(), 1);
        Category cat = category.getOrDefault(p.getUniqueId(), Category.DAILY);

        Inventory inv = Bukkit.createInventory(
                null,
                54,
                "§dQuests §8| §7" + cat.display + " §8| §7Seite " + currentPage
        );

        openInv.put(p.getUniqueId(), inv);
        fillGui(p, inv, currentPage, cat);

        p.openInventory(inv);
    }

    private void fillGui(Player p, Inventory inv, int pageNumber, Category cat) {
        inv.clear();

        // Hintergrund
        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, glass);
        }

        Map<Integer, QuestView> slots = new HashMap<>();
        slotMap.put(p.getUniqueId(), slots);

        List<QuestView> all = quests.getOrDefault(p.getUniqueId(), List.of());
        List<QuestView> filtered = all.stream()
                .filter(q -> Category.fromType(q.type) == cat)
                .toList();

        int perPage = 36;
        int start = (pageNumber - 1) * perPage;
        int end = Math.min(start + perPage, filtered.size());

        int slot = 0;
        for (int i = start; i < end; i++) {
            QuestView q = filtered.get(i);
            ItemStack item = createQuestItem(q, cat);
            inv.setItem(slot, item);
            slots.put(slot, q);
            slot++;
        }

        // Navigation
        inv.setItem(45, new ItemBuilder(Material.ARROW)
                .setName("§c← Vorherige Seite")
                .addLore("§7Klicke, um eine Seite zurück zu blättern.")
                .build());

        inv.setItem(49, new ItemBuilder(Material.NETHER_STAR)
                .setName("§dKategorie wechseln")
                .addLore("§7Aktuell: §f" + cat.display)
                .addLore("§7Klicke, um die Kategorie zu wechseln.")
                .build());

        inv.setItem(53, new ItemBuilder(Material.ARROW)
                .setName("§aNächste Seite →")
                .addLore("§7Klicke, um eine Seite weiter zu blättern.")
                .build());

        // Reset-Info unten Mitte (Slot 40)
        inv.setItem(40, createResetInfoItem(cat));
    }

    private ItemStack createQuestItem(QuestView q, Category cat) {
        Material mat = q.completed ? Material.EMERALD_BLOCK : Material.BOOK;

        ItemBuilder ib = new ItemBuilder(mat)
                .setName("§d" + q.title);

        ib.addLore("§7" + q.description);
        ib.addLore(" ");

        // Fortschrittsbalken
        ib.addLore(createProgressBarLine(q.progress, q.goal));

        if (q.completed) {
            ib.addLore("§a✔ Abgeschlossen");
        }

        ib.addLore(" ");

        if (q.rewardGalas > 0) {
            ib.addLore("§e+ " + q.rewardGalas + " Galas");
        }
        if (q.rewardStardust > 0) {
            ib.addLore("§d+ " + q.rewardStardust + " Stardust");
        }

        ib.addLore(" ");
        ib.addLore("§8Kategorie: §7" + cat.display);
        ib.addLore("§8Quest-Key: §7" + q.key);
        ib.addLore("§8Fortschritt wird automatisch aktualisiert.");

        return ib.build();
    }

    private String createProgressBarLine(long progress, long goal) {
        if (goal <= 0) {
            return "§7[§c???§7] §e" + progress + "§7/§e" + goal;
        }

        double ratio = Math.min(1.0, (double) progress / (double) goal);
        int totalBars = 20;
        int done = (int) Math.round(totalBars * ratio);

        StringBuilder sb = new StringBuilder("§7[");
        for (int i = 0; i < totalBars; i++) {
            if (i < done) sb.append("§a█");
            else sb.append("§8█");
        }
        sb.append("§7] §e").append(progress).append("§7/§e").append(goal);
        return sb.toString();
    }

    private ItemStack createResetInfoItem(Category cat) {
        Duration d = computeResetDuration(cat);
        String line = "§7Reset in: §e" + formatDuration(d);

        Material mat = switch (cat) {
            case DAILY -> Material.CLOCK;
            case WEEKLY -> Material.PAPER;
            case MONTHLY -> Material.DRAGON_EGG;
        };

        ItemBuilder ib = new ItemBuilder(mat)
                .setName("§dReset-Information");

        ib.addLore("§7Kategorie: §f" + cat.display);
        ib.addLore(" ");
        ib.addLore(line);

        switch (cat) {
            case DAILY -> ib.addLore("§8Resets täglich um Mitternacht.");
            case WEEKLY -> ib.addLore("§8Resets Montags um 00:00.");
            case MONTHLY -> ib.addLore("§8Resets am Monatsanfang.");
        }

        return ib.build();
    }

    private Duration computeResetDuration(Category cat) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime target;

        switch (cat) {
            case DAILY -> target = now.toLocalDate().plusDays(1).atStartOfDay();
            case WEEKLY -> {
                LocalDate nextMonday = now.toLocalDate().with(java.time.DayOfWeek.MONDAY);
                if (!nextMonday.isAfter(now.toLocalDate())) {
                    nextMonday = nextMonday.plusWeeks(1);
                }
                target = nextMonday.atStartOfDay();
            }
            case MONTHLY -> {
                LocalDate firstNextMonth = now.toLocalDate()
                        .with(TemporalAdjusters.firstDayOfNextMonth());
                target = firstNextMonth.atStartOfDay();
            }
            default -> target = now.plusHours(1);
        }

        if (target.isBefore(now)) {
            target = now;
        }

        return Duration.between(now, target);
    }

    private String formatDuration(Duration d) {
        long seconds = d.getSeconds();
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;

        if (hours > 0) {
            return hours + "h " + minutes + "min";
        }
        return minutes + "min";
    }

    // =========================================================
    // Inventory Events
    // =========================================================

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();

        Inventory open = openInv.get(uuid);
        if (open == null) return;

        // WICHTIG: immer über das Top-Inventory der View vergleichen
        if (!e.getView().getTopInventory().equals(open)) return;

        // Alles blocken (auch Shift-Klick etc.)
        e.setCancelled(true);

        int slot = e.getRawSlot();

        // nur Slots im oberen Inventar (0–53) behandeln
        if (slot < 0 || slot >= open.getSize()) {
            return;
        }

        int currentPage = page.getOrDefault(uuid, 1);
        Category cat = category.getOrDefault(uuid, Category.DAILY);

        // Navigation
        if (slot == 45) {
            if (currentPage > 1) {
                page.put(uuid, currentPage - 1);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 0.9f);
                openGui(p);
            }
            return;
        }

        if (slot == 53) {
            page.put(uuid, currentPage + 1);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.1f);
            openGui(p);
            return;
        }

        if (slot == 49) {
            Category next = switch (cat) {
                case DAILY -> Category.WEEKLY;
                case WEEKLY -> Category.MONTHLY;
                case MONTHLY -> Category.DAILY;
            };
            category.put(uuid, next);
            page.put(uuid, 1);
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.5f);
            openGui(p);
            return;
        }

        // Klick auf eine Quest
        Map<Integer, QuestView> map = slotMap.getOrDefault(uuid, Map.of());
        QuestView q = map.get(slot);
        if (q != null) {
            if (q.completed) {
                // Kein Sound-Spam, nur Info
                p.sendMessage("§d[Quests] §7Diese Quest ist bereits §aabgeschlossen§7.");
            } else {
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.6f, 1.8f);
                p.sendMessage("§d[Quests] §7Fortschritt wird automatisch beim Spielen aktualisiert.");
            }
        }
    }
    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        UUID uuid = p.getUniqueId();

        Inventory open = openInv.get(uuid);
        if (open == null) return;

        // Nur unser Quest-Inventar schützen
        if (!e.getView().getTopInventory().equals(open)) return;

        // Alle Drag-Aktionen blocken (auch wenn Slots unten betroffen wären)
        e.setCancelled(true);
    }



    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        openInv.remove(uuid);
        slotMap.remove(uuid);
        // page / category lassen wir – beim nächsten Öffnen fangen wir eh bei Seite 1 an
    }
}
