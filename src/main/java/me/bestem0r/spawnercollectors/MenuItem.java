package me.bestem0r.spawnercollectors;


import me.bestem0r.spawnercollectors.utilities.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;

public class MenuItem extends ItemStack {

    public static class Builder {

        private final Material material;
        private String name = "";
        private ArrayList<String> lore = new ArrayList<>();
        private int amount = 1;

        public Builder(Material material) {
            this.material = material;
        }

        public Builder nameFromPath(String path) {
            this.name = new Color.Builder().path(path).build();
            return this;
        }
        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder lore(ArrayList<String> lore) {
            this.lore = lore;
            return this;
        }

        public Builder amount(int amount) {
            this.amount = amount;
            return this;
        }

        public MenuItem build() {
            MenuItem item = new MenuItem(material);
            item.setAmount(amount);

            ItemMeta itemMeta = item.getItemMeta();

            if (itemMeta != null) {
                if (!name.equals("")) itemMeta.setDisplayName(name);
                itemMeta.setLore(lore);
                item.setItemMeta(itemMeta);
            }
            return item;
        }
    }
    private MenuItem(Material material) {
        super(material);
    }
}