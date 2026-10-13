package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.config.Settings;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/**
 * Physique de la balle (gravité, frottements, rebonds), simulée tick par tick.
 * Les vitesses sont en blocs/tick. La même fonction sert à la balle réelle et à la prévisualisation
 * de trajectoire.
 */
public final class BallPhysics {

    public enum Contact {
        NONE, SOLID, GROUND
    }

    private BallPhysics() {
    }

    public static Contact step(World w, Vector pos, Vector vel, Settings s, double floorTop) {
        vel.setY(vel.getY() - s.gravity);
        vel.multiply(s.drag);
        double speed = vel.length();
        if (speed > s.maxSpeed) {
            vel.multiply(s.maxSpeed / speed);
        }

        int n = Math.max(1, (int) Math.ceil(vel.length() / 0.25));
        double dx = vel.getX() / n;
        double dy = vel.getY() / n;
        double dz = vel.getZ() / n;
        Contact result = Contact.NONE;

        for (int k = 0; k < n; k++) {
            double nx = pos.getX() + dx;
            if (solid(w, s, nx, pos.getY(), pos.getZ())) {
                vel.setX(-vel.getX() * s.bounce);
                dx = -dx * s.bounce;
                result = Contact.SOLID;
            } else {
                pos.setX(nx);
            }

            double nz = pos.getZ() + dz;
            if (solid(w, s, pos.getX(), pos.getY(), nz)) {
                vel.setZ(-vel.getZ() * s.bounce);
                dz = -dz * s.bounce;
                result = Contact.SOLID;
            } else {
                pos.setZ(nz);
            }

            double ny = pos.getY() + dy;
            if (dy < 0) {
                double probe = ny - s.radius;
                if (solid(w, s, pos.getX(), probe, pos.getZ())) {
                    double top = Math.floor(probe) + 1.0;
                    if (top <= floorTop + 0.01) {
                        pos.setY(top + s.radius);
                        vel.setY(0);
                        return Contact.GROUND;
                    }
                    // bloc plus haut que le sol (filet, poteau, mur) : on rebondit dessus
                    pos.setY(top + s.radius);
                    vel.setY(-vel.getY() * s.bounce);
                    dy = -dy * s.bounce;
                    result = Contact.SOLID;
                } else {
                    pos.setY(ny);
                }
            } else if (dy > 0) {
                double probe = ny + s.radius;
                if (solid(w, s, pos.getX(), probe, pos.getZ())) {
                    vel.setY(-vel.getY() * s.bounce);
                    dy = -dy * s.bounce;
                    result = Contact.SOLID;
                } else {
                    pos.setY(ny);
                }
            }
        }
        return result;
    }

    private static boolean solid(World w, Settings s, double x, double y, double z) {
        int bx = (int) Math.floor(x);
        int by = (int) Math.floor(y);
        int bz = (int) Math.floor(z);
        if (by < w.getMinHeight()) {
            return true;
        }
        if (by >= w.getMaxHeight()) {
            return false;
        }
        if (!w.isChunkLoaded(bx >> 4, bz >> 4)) {
            return true;
        }
        Block b = w.getBlockAt(bx, by, bz);
        if (b.isPassable()) {
            return false;
        }
        if (!s.netCollision) {
            Material t = b.getType();
            if (t == s.net || t == s.netTop || t == s.post) {
                return false;
            }
        }
        return true;
    }

    /** Points de la trajectoire prévue (un point tous les 2 ticks). */
    public static List<Vector> simulate(World w, Vector startPos, Vector startVel, Settings s,
                                        double floorTop, int maxTicks) {
        Vector pos = startPos.clone();
        Vector vel = startVel.clone();
        List<Vector> points = new ArrayList<>();
        for (int t = 0; t < maxTicks; t++) {
            Contact c = step(w, pos, vel, s, floorTop);
            if (t % 2 == 0) {
                points.add(pos.clone());
            }
            if (c == Contact.GROUND) {
                points.add(pos.clone());
                break;
            }
        }
        return points;
    }
}
