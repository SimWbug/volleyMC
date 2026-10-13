package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.config.Settings;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Rejoue un point : une balle fantôme lumineuse et des têtes de joueurs fantômes qui suivent les positions enregistrées. */
public final class ReplayPlayer {

    public static final String TAG = "jvb_ghost";

    private final JustVolleyballMC plugin;
    private final ReplayRecorder.Data data;
    private final World world;
    private final double offsetY;
    private final ArmorStand ballStand;
    private final Map<UUID, ArmorStand> ghosts = new HashMap<>();
    private int index = 0;

    public ReplayPlayer(JustVolleyballMC plugin, ReplayRecorder.Data data) {
        this.plugin = plugin;
        this.data = data;
        this.world = Bukkit.getWorld(data.worldName);
        Settings s = plugin.settings();
        this.offsetY = s.renderOffsetY;
        Vector first = data.frames.get(0).ball;
        this.ballStand = spawnStand(first.getX(), first.getY() - offsetY, first.getZ(), s.armorStandSmall,
                data.head, Component.text("[Replay]"), true);
    }

    private ArmorStand spawnStand(double x, double y, double z, boolean small, ItemStack head, Component name, boolean glow) {
        final org.bukkit.NamespacedKey key = plugin.ballKey();
        return world.spawn(new Location(world, x, y, z), ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(small);
            as.setBasePlate(false);
            as.setArms(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setPersistent(false);
            as.setCollidable(false);
            as.setGlowing(glow);
            as.addScoreboardTag(TAG);
            as.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            as.customName(name);
            as.setCustomNameVisible(true);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(head);
            }
        });
    }

    /** Avance d'une frame. Retourne false quand le replay est terminé (les fantômes sont alors supprimés). */
    public boolean tick() {
        if (world == null || index >= data.frames.size() || !ballStand.isValid()) {
            stop();
            return false;
        }
        ReplayRecorder.Frame f = data.frames.get(index++);
        ballStand.teleport(new Location(world, f.ball.getX(), f.ball.getY() - offsetY, f.ball.getZ()));
        for (Map.Entry<UUID, Vector> e : f.players.entrySet()) {
            ArmorStand g = ghosts.get(e.getKey());
            if (g == null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(e.getKey());
                ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) skull.getItemMeta();
                meta.setOwningPlayer(op);
                skull.setItemMeta(meta);
                String name = op.getName() == null ? "?" : op.getName();
                g = spawnStand(e.getValue().getX(), e.getValue().getY(), e.getValue().getZ(), false,
                        skull, Component.text(name), false);
                ghosts.put(e.getKey(), g);
            } else if (g.isValid()) {
                g.teleport(new Location(world, e.getValue().getX(), e.getValue().getY(), e.getValue().getZ()));
            }
        }
        return true;
    }

    public void stop() {
        if (ballStand.isValid()) {
            ballStand.remove();
        }
        for (ArmorStand g : ghosts.values()) {
            if (g.isValid()) {
                g.remove();
            }
        }
        ghosts.clear();
        index = Integer.MAX_VALUE;
    }
}
