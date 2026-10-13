package fr.cleangang.justvolleyball.gui;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.util.Items;
import fr.cleangang.justvolleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

/** Menu générique : chaque slot a un item et une action. Le listener annule tous les clics. */
public final class Menu implements InventoryHolder {

    public interface Handler {
        void click(Player player, InventoryClickEvent event);
    }

    private final Inventory inventory;
    private final Map<Integer, Handler> handlers = new HashMap<>();

    public Menu(String titleMiniMessage, int size) {
        this.inventory = Bukkit.createInventory(this, size, Msg.c(titleMiniMessage));
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Menu set(int slot, ItemStack item, Handler handler) {
        inventory.setItem(slot, item);
        if (handler != null) {
            handlers.put(slot, handler);
        }
        return this;
    }

    public Menu decor(int slot, ItemStack item) {
        return set(slot, item, null);
    }

    /** Remplit les slots vides avec une vitre grise sans nom. */
    public Menu fillEmpty() {
        ItemStack pane = Items.of(Material.GRAY_STAINED_GLASS_PANE, "<gray> ");
        for (int i = 0; i < inventory.getSize(); i++) {
            if (inventory.getItem(i) == null) {
                inventory.setItem(i, pane);
            }
        }
        return this;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void handle(Player player, InventoryClickEvent event) {
        Handler h = handlers.get(event.getRawSlot());
        if (h != null) {
            h.click(player, event);
        }
    }

    /** Exécute l'action au tick suivant (on n'ouvre pas un menu depuis un clic de menu). */
    public static void later(JustVolleyballMC plugin, Runnable action) {
        Bukkit.getScheduler().runTask(plugin, action);
    }
}
