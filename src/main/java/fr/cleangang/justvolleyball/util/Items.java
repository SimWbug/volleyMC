package fr.cleangang.justvolleyball.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Construction d'items de menu (MiniMessage, sans italique par défaut). */
public final class Items {

    private Items() {
    }

    public static ItemStack of(Material material, String name, String... lore) {
        return decorate(new ItemStack(material), name, List.of(lore));
    }

    public static ItemStack decorate(ItemStack item, String name, List<String> lore) {
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Msg.c(name).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Msg.c(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }
}
