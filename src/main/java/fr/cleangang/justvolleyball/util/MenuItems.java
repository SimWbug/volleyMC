package fr.cleangang.justvolleyball.util;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.config.Settings;
import fr.cleangang.justvolleyball.game.SkinFactory;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.function.Predicate;

/**
 * Les deux objets de hotbar du plugin :
 *  - le ballon (tous les joueurs) : ouvre le menu principal ;
 *  - les outils admin (permission justvolleyball.admin) : ouvrent le menu admin.
 * Ils sont verrouillés (ni lâchables, ni déplaçables, ni perdus à la mort).
 */
public final class MenuItems {

    public static final String ADMIN_PERMISSION = "justvolleyball.admin";

    private final JustVolleyballMC plugin;
    private final NamespacedKey ballKey;
    private final NamespacedKey adminKey;

    public MenuItems(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.ballKey = new NamespacedKey(plugin, "menu_ball");
        this.adminKey = new NamespacedKey(plugin, "menu_admin");
    }

    public ItemStack ball() {
        ItemStack item = SkinFactory.head(plugin.settings().defaultSkin());
        Items.decorate(item, "<gold><bold>Ballon de volley",
                List.of("<gray>Clic droit : ouvrir le menu du volley",
                        "<gray>Arènes, stats, cosmétiques, replay"));
        tag(item, ballKey);
        return item;
    }

    public ItemStack admin() {
        ItemStack item = Items.of(Material.COMMAND_BLOCK, "<red><bold>Outils admin Volley",
                "<gray>Clic droit : ouvrir le menu admin",
                "<gray>Faux, création et gestion des arènes");
        tag(item, adminKey);
        return item;
    }

    private void tag(ItemStack item, NamespacedKey key) {
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
    }

    private boolean has(ItemStack item, NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public boolean isBall(ItemStack item) {
        return has(item, ballKey);
    }

    public boolean isAdmin(ItemStack item) {
        return has(item, adminKey);
    }

    public boolean isMenuItem(ItemStack item) {
        return isBall(item) || isAdmin(item);
    }

    /** Donne (ou remet) les objets de hotbar selon la config et les permissions du joueur. */
    public void give(Player p) {
        Settings s = plugin.settings();
        if (!s.hotbarEnabled) {
            return;
        }
        place(p, ball(), this::isBall, s.hotbarBallSlot);
        if (p.hasPermission(ADMIN_PERMISSION)) {
            place(p, admin(), this::isAdmin, s.hotbarAdminSlot);
        } else {
            removeWhere(p, this::isAdmin);
        }
    }

    /** Retire les objets du plugin (à la désactivation ou si la config est coupée). */
    public void removeAll(Player p) {
        removeWhere(p, this::isMenuItem);
    }

    private void place(Player p, ItemStack item, Predicate<ItemStack> test, int slot) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < 36; i++) {
            if (test.test(inv.getItem(i))) {
                return; // déjà présent : on ne le déplace pas
            }
        }
        int preferred = Math.max(0, Math.min(8, slot));
        ItemStack current = inv.getItem(preferred);
        if (current == null || current.getType().isAir()) {
            inv.setItem(preferred, item);
            return;
        }
        // le slot voulu est pris par un objet d'un autre plugin/du joueur : on ne l'écrase jamais
        for (int i = 0; i < 9; i++) {
            ItemStack other = inv.getItem(i);
            if (other == null || other.getType().isAir()) {
                inv.setItem(i, item);
                return;
            }
        }
    }

    private void removeWhere(Player p, Predicate<ItemStack> test) {
        PlayerInventory inv = p.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            if (test.test(inv.getItem(i))) {
                inv.setItem(i, null);
            }
        }
    }
}
