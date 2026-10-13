package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.config.Settings;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;
import org.bukkit.util.Vector;

import java.util.UUID;

/** La balle : un armor stand invisible (marker) portant une tête, déplacé par notre propre physique. */
public final class Ball {

    public static final String TAG = "jvb_ball";

    private final World world;
    private final ArmorStand stand;
    private final Vector pos;
    private final double offsetY;
    private double spin;

    public final Vector vel = new Vector();
    public boolean held = true;
    public TeamSide lastTeam;
    public UUID lastPlayer;
    public int touches;

    public Ball(Settings s, Location at, ItemStack head, NamespacedKey key) {
        this.world = at.getWorld();
        this.pos = at.toVector();
        this.offsetY = s.renderOffsetY;
        Location spawn = at.clone().subtract(0, offsetY, 0);
        this.stand = world.spawn(spawn, ArmorStand.class, as -> {
            as.setInvisible(true);
            as.setGravity(false);
            as.setMarker(true);
            as.setSmall(s.armorStandSmall);
            as.setBasePlate(false);
            as.setArms(false);
            as.setInvulnerable(true);
            as.setSilent(true);
            as.setPersistent(false);
            as.setCollidable(false);
            as.setCanPickupItems(false);
            as.addScoreboardTag(TAG);
            as.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            if (as.getEquipment() != null) {
                as.getEquipment().setHelmet(head);
            }
        });
    }

    public Vector pos() {
        return pos;
    }

    public void setPos(Vector v) {
        pos.copy(v);
    }

    public World world() {
        return world;
    }

    public boolean isValid() {
        return stand.isValid();
    }

    public void render() {
        if (!stand.isValid()) {
            return;
        }
        stand.teleport(new Location(world, pos.getX(), pos.getY() - offsetY, pos.getZ()));
        if (!held) {
            spin += Math.min(0.5, vel.length() * 0.35);
            stand.setHeadPose(new EulerAngle(spin, 0.0, 0.0));
        }
    }

    public Location location() {
        return new Location(world, pos.getX(), pos.getY(), pos.getZ());
    }

    public BallPhysics.Contact step(Settings s, double floorTop) {
        BallPhysics.Contact c = BallPhysics.step(world, pos, vel, s, floorTop);
        render();
        return c;
    }

    public void remove() {
        if (stand.isValid()) {
            stand.remove();
        }
    }
}
