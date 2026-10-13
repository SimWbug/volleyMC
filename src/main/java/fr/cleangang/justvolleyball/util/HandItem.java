package fr.cleangang.justvolleyball.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Le client n'envoie pas de clic droit "dans le vide" quand les deux mains sont vides.
 * Pour que les actions (passe, charge du service) fonctionnent, on met donc une "main de volley"
 * dans la main secondaire, UNIQUEMENT si les deux mains sont vides (aucun objet du joueur n'est touché).
 */
public final class HandItem {

    private HandItem() {
    }

    public static ItemStack create(NamespacedKey key) {
        ItemStack item = new ItemStack(Material.SLIME_BALL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Main de volley", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Clic droit : passe / service", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false),
                Component.text("Clic gauche en l'air : smash", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isMarker(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() != Material.SLIME_BALL || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    /** Donne la main de volley si les deux mains du joueur sont vides. */
    public static void giveIfHandsEmpty(Player p, NamespacedKey key) {
        PlayerInventory inv = p.getInventory();
        if (inv.getItemInMainHand().getType().isAir() && inv.getItemInOffHand().getType().isAir()) {
            inv.setItemInOffHand(create(key));
        }
    }

    /** Retire toutes les mains de volley de l'inventaire. */
    public static void removeAll(Player p, NamespacedKey key) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = inv.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isMarker(contents[i], key)) {
                inv.setItem(i, null);
            }
        }
    }
}
