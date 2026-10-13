package fr.cleangang.justvolleyball.gui;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.config.Settings;
import fr.cleangang.justvolleyball.game.SkinFactory;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** Menu des cosmétiques : skins de balle (ligne du haut) et traînées de particules (ligne du bas). */
public final class CosmeticsGui {

    public static final class Holder implements InventoryHolder {
        public final List<String> skinSlots = new ArrayList<>();
        public final List<String> trailSlots = new ArrayList<>();
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int TRAIL_ROW_START = 18;

    private CosmeticsGui() {
    }

    public static void open(JustVolleyballMC plugin, Player p) {
        Settings s = plugin.settings();
        Holder holder = new Holder();
        Inventory inv = Bukkit.createInventory(holder, 27, Component.text("Volley · Cosmétiques"));
        holder.inventory = inv;

        String currentSkin = plugin.playerData().skin(p.getUniqueId());
        int slot = 0;
        if (s.skinsEnabled) {
            for (Settings.BallSkin skin : s.skins.values()) {
                if (slot >= 9) {
                    break;
                }
                ItemStack item = SkinFactory.head(skin);
                ItemMeta meta = item.getItemMeta();
                boolean selected = skin.id().equals(currentSkin);
                meta.displayName(Component.text("Balle : " + skin.display(), selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text(selected ? "Sélectionné" : "Clique pour choisir", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                holder.skinSlots.add(skin.id());
                slot++;
            }
        }

        String currentTrail = plugin.playerData().trail(p.getUniqueId());
        slot = TRAIL_ROW_START;
        if (s.trailsEnabled) {
            for (Settings.TrailDef trail : s.trails.values()) {
                if (slot >= TRAIL_ROW_START + 8) {
                    break;
                }
                ItemStack item = new ItemStack(trail.icon());
                ItemMeta meta = item.getItemMeta();
                boolean selected = trail.id().equals(currentTrail);
                meta.displayName(Component.text("Traînée : " + trail.display(), selected ? NamedTextColor.GREEN : NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                meta.lore(List.of(Component.text(selected ? "Sélectionnée" : "Clique pour choisir", NamedTextColor.GRAY)
                        .decoration(TextDecoration.ITALIC, false)));
                item.setItemMeta(meta);
                inv.setItem(slot, item);
                holder.trailSlots.add(trail.id());
                slot++;
            }
            ItemStack off = new ItemStack(Material.BARRIER);
            ItemMeta meta = off.getItemMeta();
            meta.displayName(Component.text("Aucune traînée", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            off.setItemMeta(meta);
            inv.setItem(26, off);
        }
        p.openInventory(inv);
    }

    /** Gère un clic dans le menu (déjà annulé par le listener). */
    public static void click(JustVolleyballMC plugin, Player p, Holder holder, int rawSlot) {
        if (rawSlot >= 0 && rawSlot < holder.skinSlots.size() && rawSlot < 9) {
            String id = holder.skinSlots.get(rawSlot);
            plugin.playerData().setSkin(p.getUniqueId(), id);
            p.sendMessage(fr.cleangang.justvolleyball.util.Msg.c(fr.cleangang.justvolleyball.util.Msg.PREFIX
                    + "Skin de balle : <white>" + fr.cleangang.justvolleyball.util.Msg.esc(id)));
            open(plugin, p);
        } else if (rawSlot >= TRAIL_ROW_START && rawSlot < TRAIL_ROW_START + holder.trailSlots.size()) {
            String id = holder.trailSlots.get(rawSlot - TRAIL_ROW_START);
            plugin.playerData().setTrail(p.getUniqueId(), id);
            p.sendMessage(fr.cleangang.justvolleyball.util.Msg.c(fr.cleangang.justvolleyball.util.Msg.PREFIX
                    + "Traînée : <white>" + fr.cleangang.justvolleyball.util.Msg.esc(id)));
            open(plugin, p);
        } else if (rawSlot == 26) {
            plugin.playerData().setTrail(p.getUniqueId(), null);
            p.sendMessage(fr.cleangang.justvolleyball.util.Msg.c(fr.cleangang.justvolleyball.util.Msg.PREFIX
                    + "Traînée désactivée."));
            open(plugin, p);
        }
    }
}
