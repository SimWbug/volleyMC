package fr.cleangang.justvolleyball.arena;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.config.Settings;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Génère le terrain (sol coloré par camp, lignes, filet + poteaux, vide au-dessus).
 * La construction est étalée sur plusieurs ticks pour ne pas faire lagger le serveur.
 */
public final class CourtBuilder {

    private static final int BLOCKS_PER_TICK = 2500;

    private CourtBuilder() {
    }

    public static void build(JustVolleyballMC plugin, Arena arena, Runnable whenDone) {
        World world = arena.world();
        if (world == null) {
            return;
        }
        Settings s = plugin.settings();
        List<int[]> columns = new ArrayList<>();
        for (int x = arena.minX(); x <= arena.maxX(); x++) {
            for (int z = arena.minZ(); z <= arena.maxZ(); z++) {
                columns.add(new int[]{x, z});
            }
        }
        final int height = s.ceilingHeight;
        final int perTick = Math.max(1, BLOCKS_PER_TICK / (height + 2));

        new BukkitRunnable() {
            private int index = 0;

            @Override
            public void run() {
                int processed = 0;
                while (index < columns.size() && processed < perTick) {
                    int[] col = columns.get(index++);
                    buildColumn(world, arena, s, col[0], col[1], height);
                    processed++;
                }
                if (index >= columns.size()) {
                    cancel();
                    if (whenDone != null) {
                        whenDone.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static void buildColumn(World world, Arena a, Settings s, int x, int z, int height) {
        int i = a.alongX() ? x : z;
        int j = a.alongX() ? z : x;
        int row = a.netRow();

        boolean border = i == a.axisMin() || i == a.axisMax() || j == a.shortMin() || j == a.shortMax();
        boolean attackLine = Math.abs(i - row) == 3;
        Material floor;
        if (border || i == row || attackLine) {
            floor = s.line;
        } else {
            floor = i < row ? s.floorA : s.floorB;
        }
        world.getBlockAt(x, a.floorY(), z).setType(floor, false);

        boolean netCell = i == row;
        boolean postCell = netCell && (j == a.shortMin() || j == a.shortMax());

        for (int dy = 1; dy <= height; dy++) {
            Block b = world.getBlockAt(x, a.floorY() + dy, z);
            if (netCell && dy <= s.netHeight) {
                Material m;
                if (postCell) {
                    m = s.post;
                } else if (dy == s.netHeight) {
                    m = s.netTop;
                } else {
                    m = s.net;
                }
                // physique activée pour que vitres/clôtures se connectent entre elles
                b.setType(m, true);
            } else if (s.clearAir && b.getType() != Material.AIR) {
                b.setType(Material.AIR, false);
            }
        }
    }

    /** Retire le filet et les poteaux (utilisé à la suppression de l'arène avec l'option clear). */
    public static void removeNet(Arena a, Settings s) {
        World world = a.world();
        if (world == null) {
            return;
        }
        int row = a.netRow();
        for (int j = a.shortMin(); j <= a.shortMax(); j++) {
            int x = a.alongX() ? row : j;
            int z = a.alongX() ? j : row;
            for (int dy = 1; dy <= s.netHeight; dy++) {
                world.getBlockAt(x, a.floorY() + dy, z).setType(Material.AIR, false);
            }
        }
    }
}
