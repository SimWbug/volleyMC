package fr.cleangang.justvolleyball.admin;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La faux d'arène : clic gauche sur un bloc = point 1 (définit aussi le niveau du sol),
 * clic droit sur un bloc = point 2. Ensuite /jvb create génère le terrain automatiquement.
 */
public final class WandManager {

    private final JustVolleyballMC plugin;
    private final NamespacedKey key;
    private final Map<UUID, Location[]> selections = new HashMap<>();

    public WandManager(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "arena_wand");
    }

    public ItemStack create() {
        ItemStack item = new ItemStack(Material.NETHERITE_HOE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Faux d'arène Volley", NamedTextColor.GOLD, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                line("Clic gauche sur un bloc : point 1 (niveau du sol)"),
                line("Clic droit sur un bloc : point 2"),
                line("Puis : /jvb create <nom> <1v1..6v6> [ranked|open]")));
        meta.setUnbreakable(true);
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    private Component line(String text) {
        return Component.text(text, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_HOE || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public void setPoint(Player p, int index, Location block) {
        Location[] sel = selections.computeIfAbsent(p.getUniqueId(), k -> new Location[2]);
        sel[index] = block.clone();
    }

    public Location[] get(UUID id) {
        return selections.get(id);
    }

    public void clear(UUID id) {
        selections.remove(id);
    }

    /** Dessine le contour de la sélection pour les admins qui tiennent la faux. */
    public void startOutlineTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : plugin.getServer().getOnlinePlayers()) {
                if (!isWand(p.getInventory().getItemInMainHand())) {
                    continue;
                }
                Location[] sel = selections.get(p.getUniqueId());
                if (sel == null || sel[0] == null || sel[1] == null
                        || sel[0].getWorld() == null || !sel[0].getWorld().equals(p.getWorld())
                        || !sel[0].getWorld().equals(sel[1].getWorld())) {
                    continue;
                }
                drawOutline(p, sel);
            }
        }, 20L, 10L);
    }

    private void drawOutline(Player p, Location[] sel) {
        int minX = Math.min(sel[0].getBlockX(), sel[1].getBlockX());
        int maxX = Math.max(sel[0].getBlockX(), sel[1].getBlockX()) + 1;
        int minZ = Math.min(sel[0].getBlockZ(), sel[1].getBlockZ());
        int maxZ = Math.max(sel[0].getBlockZ(), sel[1].getBlockZ()) + 1;
        double y = sel[0].getBlockY() + 1.1;
        int budget = 600;
        for (int x = minX; x <= maxX && budget > 0; x++) {
            p.spawnParticle(Particle.END_ROD, x, y, minZ, 1, 0, 0, 0, 0);
            p.spawnParticle(Particle.END_ROD, x, y, maxZ, 1, 0, 0, 0, 0);
            budget -= 2;
        }
        for (int z = minZ; z <= maxZ && budget > 0; z++) {
            p.spawnParticle(Particle.END_ROD, minX, y, z, 1, 0, 0, 0, 0);
            p.spawnParticle(Particle.END_ROD, maxX, y, z, 1, 0, 0, 0, 0);
            budget -= 2;
        }
    }
}
