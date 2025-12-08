package de.galacticfy.galacticfyChat.util;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class ItemBuilder {

    private final ItemStack stack;

    public ItemBuilder(Material material) {
        this.stack = new ItemStack(material);
    }

    public ItemBuilder(ItemStack base) {
        this.stack = base.clone();
    }

    // -----------------------------------------------------
    // NAME
    // -----------------------------------------------------

    public ItemBuilder setName(String name) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        meta.setDisplayName(color(name));
        stack.setItemMeta(meta);
        return this;
    }

    // -----------------------------------------------------
    // LORE
    // -----------------------------------------------------

    public ItemBuilder addLore(String line) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        List<String> lore = meta.getLore();
        if (lore == null) lore = new ArrayList<>();

        lore.add(color(line));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addLore(List<String> lines) {
        for (String s : lines) addLore(s);
        return this;
    }

    public ItemBuilder setLore(List<String> loreLines) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        List<String> lore = new ArrayList<>();
        for (String s : loreLines) {
            lore.add(color(s));
        }

        meta.setLore(lore);
        stack.setItemMeta(meta);
        return this;
    }

    // -----------------------------------------------------
    // ITEM PROPERTIES
    // -----------------------------------------------------

    public ItemBuilder setAmount(int amount) {
        stack.setAmount(amount);
        return this;
    }

    public ItemBuilder addEnchant(Enchantment ench, int level, boolean ignore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        meta.addEnchant(ench, level, ignore);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        meta.addItemFlags(flags);
        stack.setItemMeta(meta);
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) return this;

        meta.setCustomModelData(data);
        stack.setItemMeta(meta);
        return this;
    }

    // -----------------------------------------------------
    // BUILD
    // -----------------------------------------------------

    public ItemStack build() {
        return stack;
    }

    // -----------------------------------------------------
    // UTIL
    // -----------------------------------------------------

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
