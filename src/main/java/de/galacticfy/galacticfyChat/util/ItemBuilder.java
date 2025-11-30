package de.galacticfy.galacticfyChat.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Sehr einfacher ItemBuilder für GUI-Items.
 * Unterstützt:
 *  - setName(String)
 *  - addLore(String)
 *  - build()
 */
public class ItemBuilder {

    private final ItemStack stack;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
    }

    public ItemBuilder(ItemStack base) {
        this.stack = base.clone();
    }

    public ItemBuilder setName(String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        // Wir benutzen bereits §-Farbcodes, also einfach direkt setzen
        meta.setDisplayName(name);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addLore(String line) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();
        lore.add(line);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemStack build() {
        return stack;
    }
}
