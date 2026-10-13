package fr.cleangang.justvolleyball.data;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Sauvegarde l'inventaire d'un joueur sur disque quand il entre dans une partie, et le rend à la sortie.
 * Les fichiers (saved-inventories/&lt;uuid&gt;.yml) survivent à un crash ou à un redémarrage : l'inventaire
 * est alors rendu à la prochaine connexion du joueur.
 */
public final class InventoryStore {

    private final JustVolleyballMC plugin;
    private final File dir;

    public InventoryStore(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.dir = new File(plugin.getDataFolder(), "saved-inventories");
    }

    private File file(UUID id) {
        return new File(dir, id + ".yml");
    }

    public boolean has(UUID id) {
        return file(id).exists();
    }

    /** Écrit l'inventaire actuel sur disque. Retourne false si l'écriture échoue (le joueur ne doit alors pas être vidé). */
    public boolean save(Player p) {
        try {
            dir.mkdirs();
            File f = file(p.getUniqueId());
            if (f.exists()) {
                // ne jamais écraser une sauvegarde existante : on la met de côté
                File old = new File(dir, p.getUniqueId() + ".yml.old-" + System.currentTimeMillis());
                if (!f.renameTo(old)) {
                    return false;
                }
            }
            PlayerInventory inv = p.getInventory();
            YamlConfiguration y = new YamlConfiguration();
            y.set("name", p.getName());
            y.set("contents", Arrays.asList(inv.getStorageContents()));
            y.set("armor", Arrays.asList(inv.getArmorContents()));
            y.set("offhand", inv.getItemInOffHand());
            y.set("held", inv.getHeldItemSlot());
            y.set("gamemode", p.getGameMode().name());
            y.save(f);
            return f.exists();
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Impossible de sauvegarder l'inventaire de " + p.getName() + " : " + e.getMessage());
            return false;
        }
    }

    /** Rend l'inventaire sauvegardé. Retourne false s'il n'y en a pas (ou s'il est illisible : le fichier est alors conservé). */
    public boolean restore(Player p) {
        File f = file(p.getUniqueId());
        if (!f.exists()) {
            return false;
        }
        try {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            PlayerInventory inv = p.getInventory();
            inv.clear();
            inv.setStorageContents(toArray(y.getList("contents"), 36));
            inv.setArmorContents(toArray(y.getList("armor"), 4));
            ItemStack off = y.getItemStack("offhand");
            inv.setItemInOffHand(off == null ? new ItemStack(Material.AIR) : off);
            inv.setHeldItemSlot(Math.max(0, Math.min(8, y.getInt("held", 0))));
            String gm = y.getString("gamemode");
            if (gm != null) {
                try {
                    p.setGameMode(GameMode.valueOf(gm));
                } catch (IllegalArgumentException ignored) {
                    // mode inconnu : on laisse tel quel
                }
            }
            p.updateInventory();
            if (!f.delete()) {
                plugin.getLogger().warning("Impossible de supprimer " + f.getName() + " après restauration.");
            }
            return true;
        } catch (RuntimeException e) {
            plugin.getLogger().warning("Inventaire illisible pour " + p.getName() + " (" + f.getName() + ") : " + e.getMessage());
            return false;
        }
    }

    private static ItemStack[] toArray(List<?> list, int size) {
        ItemStack[] out = new ItemStack[size];
        if (list != null) {
            for (int i = 0; i < size && i < list.size(); i++) {
                Object o = list.get(i);
                out[i] = o instanceof ItemStack is ? is : null;
            }
        }
        return out;
    }
}
