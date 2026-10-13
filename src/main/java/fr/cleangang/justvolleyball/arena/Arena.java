package fr.cleangang.justvolleyball.arena;

import fr.cleangang.justvolleyball.game.TeamSide;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;

/**
 * Un terrain. Le terrain est un rectangle (minX..maxX, minZ..maxZ) dont le sol est à floorY.
 * L'axe le plus long est l'axe "de jeu" : le filet est une rangée perpendiculaire, au milieu.
 * Camp A = coordonnées basses sur l'axe de jeu, camp B = coordonnées hautes.
 */
public final class Arena {

    public enum Mode {
        RANKED, OPEN;

        public static Mode parse(String text) {
            if (text == null) {
                return null;
            }
            switch (text.toLowerCase(Locale.ROOT)) {
                case "ranked":
                case "classe":
                case "classé":
                    return RANKED;
                case "open":
                case "opengym":
                case "open_gym":
                    return OPEN;
                default:
                    return null;
            }
        }

        public String label() {
            return this == RANKED ? "Classé" : "Open Gym";
        }
    }

    private final String name;
    private String worldName;
    private int minX, maxX, minZ, maxZ, floorY;
    private int teamSize;
    private boolean alongX;
    private Mode mode;
    private boolean hasLobby;
    private double lobbyX, lobbyY, lobbyZ;
    private float lobbyYaw, lobbyPitch;

    public Arena(String name, String worldName, int minX, int maxX, int minZ, int maxZ, int floorY,
                 int teamSize, Mode mode, boolean alongX) {
        this.name = name;
        this.worldName = worldName;
        this.minX = minX;
        this.maxX = maxX;
        this.minZ = minZ;
        this.maxZ = maxZ;
        this.floorY = floorY;
        this.teamSize = teamSize;
        this.mode = mode;
        this.alongX = alongX;
    }

    // ---------------------------------------------------------------- accesseurs

    public String name() {
        return name;
    }

    public String worldName() {
        return worldName;
    }

    public World world() {
        return Bukkit.getWorld(worldName);
    }

    public int minX() {
        return minX;
    }

    public int maxX() {
        return maxX;
    }

    public int minZ() {
        return minZ;
    }

    public int maxZ() {
        return maxZ;
    }

    public int floorY() {
        return floorY;
    }

    public double floorTop() {
        return floorY + 1.0;
    }

    public int teamSize() {
        return teamSize;
    }

    public void setTeamSize(int teamSize) {
        this.teamSize = teamSize;
    }

    public Mode mode() {
        return mode;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    public boolean alongX() {
        return alongX;
    }

    public String typeLabel() {
        return teamSize + "v" + teamSize;
    }

    // ---------------------------------------------------------------- géométrie

    public int axisMin() {
        return alongX ? minX : minZ;
    }

    public int axisMax() {
        return alongX ? maxX : maxZ;
    }

    public int shortMin() {
        return alongX ? minZ : minX;
    }

    public int shortMax() {
        return alongX ? maxZ : maxX;
    }

    public int length() {
        return axisMax() - axisMin() + 1;
    }

    public int width() {
        return shortMax() - shortMin() + 1;
    }

    /** Index de la rangée de blocs du filet sur l'axe de jeu. */
    public int netRow() {
        return axisMin() + length() / 2;
    }

    /** Coordonnée (centre de la rangée du filet) sur l'axe de jeu. */
    public double netCoord() {
        return netRow() + 0.5;
    }

    public double axisOf(double x, double z) {
        return alongX ? x : z;
    }

    public boolean inCourt(double x, double z) {
        return x >= minX && x < maxX + 1 && z >= minZ && z < maxZ + 1;
    }

    public TeamSide sideOf(double x, double z) {
        return axisOf(x, z) < netCoord() ? TeamSide.A : TeamSide.B;
    }

    /** Vrai si le bloc est dans le volume du terrain (avec marge), pour la protection. */
    public boolean containsBlock(String world, int x, int y, int z, int ceiling) {
        return world.equals(worldName)
                && x >= minX - 1 && x <= maxX + 1
                && z >= minZ - 1 && z <= maxZ + 1
                && y >= floorY - 1 && y <= floorY + ceiling;
    }

    public boolean overlaps(Arena other, int margin) {
        if (!other.worldName.equals(worldName)) {
            return false;
        }
        return other.minX - margin <= maxX && other.maxX + margin >= minX
                && other.minZ - margin <= maxZ && other.maxZ + margin >= minZ;
    }

    public Location center() {
        return new Location(world(), (minX + maxX + 1) / 2.0, floorY + 1.0, (minZ + maxZ + 1) / 2.0);
    }

    private float yawFor(TeamSide side) {
        double dir = side == TeamSide.A ? 1 : -1;
        double dx = alongX ? dir : 0;
        double dz = alongX ? 0 : dir;
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    private Location at(double axis, double shortPos, TeamSide facing) {
        double x = alongX ? axis : shortPos;
        double z = alongX ? shortPos : axis;
        return new Location(world(), x, floorY + 1.0, z, yawFor(facing), 0f);
    }

    /** Position de départ du joueur numéro index dans son camp. */
    public Location spawn(TeamSide side, int index) {
        int count = Math.max(1, teamSize);
        double w = width();
        double sPos = shortMin() + (index % count + 0.5) * w / count;
        double limit = length() / 2.0 - 1.0;
        double depth = Math.min(limit, index % 2 == 0 ? 6.0 : 3.5);
        depth = Math.max(1.5, depth);
        double axis = side == TeamSide.A ? netCoord() - depth : netCoord() + depth;
        return at(axis, sPos, side);
    }

    /** Position de service : juste derrière la ligne de fond du camp. */
    public Location servePos(TeamSide side) {
        double axis = side == TeamSide.A ? axisMin() + 0.5 : axisMax() + 0.5;
        double sPos = shortMin() + width() / 2.0;
        return at(axis, sPos, side);
    }

    // ---------------------------------------------------------------- lobby

    public Location lobby() {
        if (hasLobby) {
            return new Location(world(), lobbyX, lobbyY, lobbyZ, lobbyYaw, lobbyPitch);
        }
        double axis = axisMin() - 2.0;
        double sPos = shortMin() + width() / 2.0;
        return at(axis, sPos, TeamSide.A);
    }

    public void setLobby(Location l) {
        hasLobby = true;
        lobbyX = l.getX();
        lobbyY = l.getY();
        lobbyZ = l.getZ();
        lobbyYaw = l.getYaw();
        lobbyPitch = l.getPitch();
    }

    // ---------------------------------------------------------------- sauvegarde

    public void save(ConfigurationSection s) {
        s.set("name", name);
        s.set("world", worldName);
        s.set("min-x", minX);
        s.set("max-x", maxX);
        s.set("min-z", minZ);
        s.set("max-z", maxZ);
        s.set("floor-y", floorY);
        s.set("along-x", alongX);
        s.set("team-size", teamSize);
        s.set("mode", mode.name());
        s.set("lobby", null);
        if (hasLobby) {
            s.set("lobby.x", lobbyX);
            s.set("lobby.y", lobbyY);
            s.set("lobby.z", lobbyZ);
            s.set("lobby.yaw", (double) lobbyYaw);
            s.set("lobby.pitch", (double) lobbyPitch);
        }
    }

    public static Arena load(ConfigurationSection s) {
        String name = s.getString("name");
        String world = s.getString("world");
        if (name == null || world == null) {
            return null;
        }
        Mode mode = Mode.parse(s.getString("mode", "RANKED"));
        int minX = s.getInt("min-x");
        int maxX = s.getInt("max-x");
        int minZ = s.getInt("min-z");
        int maxZ = s.getInt("max-z");
        Arena a = new Arena(name, world, minX, maxX, minZ, maxZ,
                s.getInt("floor-y"), Math.max(1, Math.min(6, s.getInt("team-size", 2))), mode == null ? Mode.RANKED : mode,
                s.getBoolean("along-x", (maxX - minX) >= (maxZ - minZ)));
        if (s.isConfigurationSection("lobby")) {
            a.hasLobby = true;
            a.lobbyX = s.getDouble("lobby.x");
            a.lobbyY = s.getDouble("lobby.y");
            a.lobbyZ = s.getDouble("lobby.z");
            a.lobbyYaw = (float) s.getDouble("lobby.yaw");
            a.lobbyPitch = (float) s.getDouble("lobby.pitch");
        }
        return a;
    }
}
