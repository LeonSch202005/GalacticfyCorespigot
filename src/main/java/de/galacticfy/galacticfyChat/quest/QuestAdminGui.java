package de.galacticfy.galacticfyChat.quest;

import de.galacticfy.galacticfyChat.GalacticfyChat;
import de.galacticfy.galacticfyChat.util.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class QuestAdminGui implements Listener, CommandExecutor {

    private static final String TITLE = "§dQuest-Admin";

    private final GalacticfyChat plugin;

    public QuestAdminGui(GalacticfyChat plugin) {
        this.plugin = plugin;
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(null, 27, TITLE);

        ItemStack glass = new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE)
                .setName(" ")
                .build();
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass);
        }

        inv.setItem(11, new ItemBuilder(Material.BOOKSHELF)
                .setName("§aQuests neu laden")
                .addLore("§7Entspricht §d/quests reload")
                .addLore("§8Lädt alle aktiven Quests auf dem Proxy neu.")
                .build());

        inv.setItem(13, new ItemBuilder(Material.PAPER)
                .setName("§fQuest-System Info")
                .addLore("§7Links: System neu laden")
                .addLore("§7Rechts: neue Zufalls-Quests")
                .addLore("§7Unten: Menü schließen")
                .addLore(" ")
                .addLore("§8Aktionen laufen über Velocity (/quests).")
                .build());

        inv.setItem(15, new ItemBuilder(Material.NETHER_STAR)
                .setName("§dDaily/Weekly/Monthly neu würfeln")
                .addLore("§7Entspricht §d/quests reroll")
                .addLore("§8Erzeugt neue Tages-/Wochen-/Monats-Missionen.")
                .build());

        inv.setItem(22, new ItemBuilder(Material.BARRIER)
                .setName("§cSchließen")
                .build());

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1.2f);
    }

    private boolean isAdminGuiTitle(String title) {
        return title != null && title.equals(TITLE);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        if (!isAdminGuiTitle(title)) return;

        Inventory top = e.getView().getTopInventory();
        int rawSlot = e.getRawSlot();

        if (rawSlot < 0 || rawSlot >= top.getSize()) {
            return;
        }

        e.setCancelled(true);

        switch (rawSlot) {
            case 11 -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
                p.performCommand("quests reload");
                p.closeInventory();
            }
            case 15 -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.4f);
                p.performCommand("quests reroll");
                p.closeInventory();
            }
            case 22 -> {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 0.8f);
                p.closeInventory();
            }
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;

        String title = e.getView().getTitle();
        if (!isAdminGuiTitle(title)) return;

        e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("§cNur Ingame-Spieler können das Quest-Admin-GUI öffnen.");
            return true;
        }

        if (!p.hasPermission("galacticfy.quests.admin")) {
            p.sendMessage("§cDazu hast du keine Berechtigung.");
            return true;
        }

        open(p);
        return true;
    }
}
