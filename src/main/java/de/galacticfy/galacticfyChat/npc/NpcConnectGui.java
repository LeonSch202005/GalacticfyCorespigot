package de.galacticfy.galacticfyChat.npc;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import de.galacticfy.galacticfyChat.GalacticfyChat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Stream;

public class NpcConnectGui implements Listener {

    private static final String TITLE_KEYWORD = "server-auswahl";

    private static final String GLOBAL_TITLE   = "§8« §bGalacticfy §8| §7Server-Auswahl §8»";
    private static final String CB_TITLE       = "§8« §aCitybuild §8| §7Server-Auswahl §8»";
    private static final String SKYBLOCK_TITLE = "§8« §3Skyblock §8| §7Server-Auswahl §8»";

    private static final NamespacedKey SERVER_ID_KEY =
            new NamespacedKey(GalacticfyChat.getInstance(), "server_id");

    private record ServerButton(
            String serverId,
            String displayName,
            Material material,
            List<String> lore,
            boolean comingSoon
    ) {}

    private static final List<String> TIPS = List.of(
            "§8Tipp: §7Mit §b/hub§7 kommst du immer zur Lobby.",
            "§8Tipp: §7Rechtsklick auf den NPC öffnet dieses Menü.",
            "§8Tipp: §7Bleib freundlich im Chat ♥",
            "§8Tipp: §7Teammitglieder können NPCs mit §b/npc§7 verwalten."
    );

    // ========= GLOBAL =========
    private static final List<ServerButton> GLOBAL_BUTTONS = List.of(
            new ServerButton(
                    "Citybuild-1",
                    "§aCitybuild §7[1]",
                    Material.GRASS_BLOCK,
                    Arrays.asList(
                            "§7Baue deine Stadt,",
                            "§7farme Ressourcen und",
                            "§7spiele mit Freunden.",
                            " ",
                            "§eKlicke, um zu §6Citybuild-1 §ezu verbinden."
                    ),
                    false
            ),
            new ServerButton(
                    "Skyblock-1",
                    "§3Skyblock §7[1]",
                    Material.END_STONE,
                    Arrays.asList(
                            "§7Starte dein Insel-Abenteuer,",
                            "§7wenig Blöcke, viele Möglichkeiten.",
                            " ",
                            "§eKlicke, um zu §6Skyblock-1 §ezu verbinden."
                    ),
                    false
            ),
            new ServerButton(
                    "Lobby-1",
                    "§dLobby",
                    Material.NETHER_STAR,
                    Arrays.asList(
                            "§7Kehre zur Haupt-Lobby zurück,",
                            "§7und wähle einen anderen Modus.",
                            " ",
                            "§eKlicke, um zu §6Lobby-1 §ezu verbinden."
                    ),
                    false
            )
    );

    // ========= CITYBUILD =========
    private static final List<ServerButton> CB_BUTTONS = List.of(
            new ServerButton(
                    null,
                    "§8Coming Soon",
                    Material.BLACK_CONCRETE,
                    Arrays.asList(
                            "§7Geplanter weiterer",
                            "§aCitybuild§7-Server.",
                            " ",
                            "§8Noch nicht verfügbar."
                    ),
                    true
            ),
            new ServerButton(
                    "Citybuild-1",
                    "§a§lCitybuild-1",
                    Material.GRASS_BLOCK,
                    Arrays.asList(
                            "§7Dein Haupt-Citybuild-Server.",
                            "§7Grundstücke, Plotwelt und mehr.",
                            " ",
                            "§eKlicke, um zu §6Citybuild-1 §ezu verbinden."
                    ),
                    false
            ),
            new ServerButton(
                    null,
                    "§8Coming Soon",
                    Material.BLACK_CONCRETE,
                    Arrays.asList(
                            "§7Noch ein Citybuild-Slot",
                            "§7für zukünftige Erweiterungen.",
                            " ",
                            "§8Noch nicht verfügbar."
                    ),
                    true
            )
    );

    // ========= SKYBLOCK =========
    private static final List<ServerButton> SKYBLOCK_BUTTONS = List.of(
            new ServerButton(
                    null,
                    "§8Coming Soon",
                    Material.BLACK_CONCRETE,
                    Arrays.asList(
                            "§7Hier könnte §3Skyblock-2§7",
                            "§7entstehen.",
                            " ",
                            "§8Noch nicht verfügbar."
                    ),
                    true
            ),
            new ServerButton(
                    "Skyblock-1",
                    "§3§lSkyblock-1",
                    Material.END_STONE,
                    Arrays.asList(
                            "§7Skyblock-Startserver,",
                            "§7perfekt für den Einstieg.",
                            " ",
                            "§eKlicke, um zu §6Skyblock-1 §ezu verbinden."
                    ),
                    false
            ),
            new ServerButton(
                    null,
                    "§8Coming Soon",
                    Material.BLACK_CONCRETE,
                    Arrays.asList(
                            "§7Noch ein Slot für",
                            "§3Skyblock§7 in Planung.",
                            " ",
                            "§8Noch nicht verfügbar."
                    ),
                    true
            )
    );

    // Für Tab-Completion
    public static final String[] AVAILABLE_SERVERS =
            Stream.of(GLOBAL_BUTTONS, CB_BUTTONS, SKYBLOCK_BUTTONS)
                    .flatMap(List::stream)
                    .map(ServerButton::serverId)
                    .filter(Objects::nonNull)
                    .distinct()
                    .toArray(String[]::new);

    // ------------------------------------------------
    // GUI öffnen – nur Buttons animieren (Fly-In)
    // ------------------------------------------------
    public static void open(Player player, Npc npc) {
        String type = npc.getType() != null ? npc.getType().toUpperCase(Locale.ROOT) : "";

        // ✨ Effekt direkt am NPC
        playNpcEffect(player, npc, type);

        List<ServerButton> buttons;
        String title;
        String infoLine;
        String categoryLine;

        switch (type) {
            case "CB_SELECTOR":
            case "CITYBUILD_SELECTOR":
            case "CITYBUILD":
                buttons = CB_BUTTONS;
                title = CB_TITLE;
                infoLine = "§7Wähle deinen §aCitybuild§7-Server.";
                categoryLine = "§aCitybuild §8• §7Bauen & Farmen";
                break;

            case "SKYBLOCK_SELECTOR":
            case "SKYBLOCK":
                buttons = SKYBLOCK_BUTTONS;
                title = SKYBLOCK_TITLE;
                infoLine = "§7Wähle deinen §3Skyblock§7-Server.";
                categoryLine = "§3Skyblock §8• §7Insel-Abenteuer";
                break;

            case "SERVER_SELECTOR":
            default:
                buttons = GLOBAL_BUTTONS;
                title = GLOBAL_TITLE;
                infoLine = "§7Wähle einen §bServer§7 im Netzwerk.";
                categoryLine = "§dNetzwerk §8• §7Alle Modi";
                break;
        }

        Inventory inv = Bukkit.createInventory(null, 27, title);

        ItemStack border = namedPane(Material.BLACK_STAINED_GLASS_PANE, " ");
        ItemStack filler = namedPane(Material.GRAY_STAINED_GLASS_PANE, " ");

        // alles grau
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, filler);
        }

        // Rahmen
        int[] borderSlots = {
                0, 1, 2, 3, 4, 5, 6, 7, 8,
                9, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26
        };
        for (int slot : borderSlots) {
            inv.setItem(slot, border);
        }

        // Random Tipp
        String tip = TIPS.get(ThreadLocalRandom.current().nextInt(TIPS.size()));

        // Info-Item oben Mitte
        ItemStack info = new ItemStack(Material.NETHER_STAR);
        ItemMeta infoMeta = info.getItemMeta();
        if (infoMeta != null) {
            infoMeta.setDisplayName("§bGalacticfy §7Netzwerk");
            infoMeta.setLore(Arrays.asList(
                    categoryLine,
                    "§8───────────────",
                    infoLine,
                    " ",
                    tip,
                    " ",
                    "§8» §fKlicke auf ein Icon, um zu verbinden."
            ));
            infoMeta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            info.setItemMeta(infoMeta);
        }
        inv.setItem(4, info);

        // Close-Button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta cMeta = close.getItemMeta();
        if (cMeta != null) {
            cMeta.setDisplayName("§cSchließen");
            close.setItemMeta(cMeta);
        }
        inv.setItem(22, close);

        // GUI öffnen
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_LOOM_SELECT_PATTERN, 0.7f, 1.4f);

        // Ziel-Slots
        final int leftTarget   = 11;
        final int centerTarget = 13;
        final int rightTarget  = 15;

        // Links: Coming Soon oder erster Button
        if (!buttons.isEmpty()) {
            ItemStack leftItem = createServerItem(buttons.get(0));
            int[] leftPath = {9, 10, leftTarget};
            flyIn(inv, player, leftPath, leftItem, filler, 2L);
        }

        // Rechts: dritter Button (wenn vorhanden)
        if (buttons.size() >= 3) {
            ItemStack rightItem = createServerItem(buttons.get(2));
            int[] rightPath = {17, 16, rightTarget};
            flyIn(inv, player, rightPath, rightItem, filler, 2L);
        }

        // Mitte: Haupt-Server
        if (buttons.size() >= 2) {
            ItemStack centerItem = createServerItem(buttons.get(1));

            Bukkit.getScheduler().runTaskLater(
                    GalacticfyChat.getInstance(),
                    () -> {
                        if (!player.getOpenInventory().getTopInventory().equals(inv)) return;

                        // kurz „ploppen“: direkt setzen + Glow
                        centerItem.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1);
                        ItemMeta cm = centerItem.getItemMeta();
                        if (cm != null) {
                            cm.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                            centerItem.setItemMeta(cm);
                        }
                        inv.setItem(centerTarget, centerItem);

                        player.playSound(
                                player.getLocation(),
                                Sound.BLOCK_NOTE_BLOCK_PLING,
                                0.9f,
                                1.7f
                        );

                        // Glow nach kurzer Zeit wieder weg
                        Bukkit.getScheduler().runTaskLater(
                                GalacticfyChat.getInstance(),
                                () -> {
                                    ItemStack current = inv.getItem(centerTarget);
                                    if (current == null) return;
                                    if (current.containsEnchantment(Enchantment.VANISHING_CURSE)) {
                                        current.removeEnchantment(Enchantment.VANISHING_CURSE);
                                        inv.setItem(centerTarget, current);
                                    }
                                },
                                12L
                        );
                    },
                    8L
            );
        }
    }

    // Effekt am NPC (Partikel + Sound)
    private static void playNpcEffect(Player player, Npc npc, String type) {
        Location loc = npc.toLocation();
        if (loc == null || loc.getWorld() == null) return;

        Sound s;
        switch (type) {
            case "CB_SELECTOR":
            case "CITYBUILD_SELECTOR":
            case "CITYBUILD":
                s = Sound.BLOCK_NOTE_BLOCK_BELL;
                break;
            case "SKYBLOCK_SELECTOR":
            case "SKYBLOCK":
                s = Sound.BLOCK_AMETHYST_BLOCK_CHIME;
                break;
            case "SERVER_SELECTOR":
            default:
                s = Sound.BLOCK_BEACON_ACTIVATE;
                break;
        }

        // Sound beim NPC abspielen
        player.playSound(loc, s, 0.8f, 1.2f);

        // kleiner Partikelring um den NPC-Kopf
        Location base = loc.clone().add(0, 1.6, 0);
        for (int i = 0; i < 10; i++) {
            double angle = (Math.PI * 2 / 10) * i;
            double x = Math.cos(angle) * 0.6;
            double z = Math.sin(angle) * 0.6;
            base.getWorld().spawnParticle(
                    Particle.END_ROD,
                    base.getX() + x,
                    base.getY(),
                    base.getZ() + z,
                    1,
                    0, 0, 0, 0
            );
        }
    }

    private static void flyIn(Inventory inv,
                              Player player,
                              int[] pathSlots,
                              ItemStack item,
                              ItemStack filler,
                              long stepTicks) {

        new BukkitRunnable() {
            int index = 0;
            int lastSlot = -1;

            @Override
            public void run() {
                if (!player.getOpenInventory().getTopInventory().equals(inv)) {
                    cancel();
                    return;
                }

                if (index >= pathSlots.length) {
                    cancel();
                    return;
                }

                int slot = pathSlots[index];

                if (lastSlot != -1) {
                    inv.setItem(lastSlot, filler);
                }

                inv.setItem(slot, item.clone());

                if (index == pathSlots.length - 1) {
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_HAT,
                            0.6f,
                            1.6f
                    );
                } else {
                    player.playSound(
                            player.getLocation(),
                            Sound.BLOCK_NOTE_BLOCK_SNARE,
                            0.4f,
                            1.4f
                    );
                }

                lastSlot = slot;
                index++;
            }
        }.runTaskTimer(GalacticfyChat.getInstance(), 0L, stepTicks);
    }

    private static ItemStack namedPane(Material mat, String name) {
        ItemStack is = new ItemStack(mat);
        ItemMeta im = is.getItemMeta();
        if (im != null) {
            im.setDisplayName(name);
            is.setItemMeta(im);
        }
        return is;
    }

    private static ItemStack createServerItem(ServerButton button) {
        ItemStack item = new ItemStack(button.material());
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(button.displayName());
            meta.setLore(button.lore());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);

            if (button.serverId() != null) {
                meta = item.getItemMeta();
                if (meta != null) {
                    meta.getPersistentDataContainer().set(
                            SERVER_ID_KEY,
                            PersistentDataType.STRING,
                            button.serverId()
                    );
                    item.setItemMeta(meta);
                }
            }
        }
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        String title = e.getView().getTitle();
        if (title == null) return;

        String stripped = ChatColor.stripColor(title).toLowerCase(Locale.ROOT);
        if (!stripped.contains(TITLE_KEYWORD)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        String displayName = meta.getDisplayName() != null
                ? ChatColor.stripColor(meta.getDisplayName())
                : "";

        if (displayName.equalsIgnoreCase("Schließen")) {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.0f);
            return;
        }

        String targetServer = meta.getPersistentDataContainer()
                .get(SERVER_ID_KEY, PersistentDataType.STRING);

        if (targetServer == null || targetServer.isEmpty()) {
            if (displayName.toLowerCase(Locale.ROOT).contains("coming soon")) {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
                player.sendMessage("§8[§bGalacticfy§8] §7Dieser Server ist §cnoch nicht§7 verfügbar.");
            }
            return;
        }

        player.closeInventory();
        player.sendMessage("§7Verbinde zu §b" + targetServer + "§7...");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);

        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("Connect");
            out.writeUTF(targetServer);

            player.sendPluginMessage(
                    GalacticfyChat.getInstance(),
                    "BungeeCord",
                    out.toByteArray()
            );
        } catch (Exception ex) {
            player.sendMessage("§cFehler beim Verbinden: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
}
