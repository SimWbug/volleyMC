package fr.cleangang.justvolleyball.admin;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.arena.CourtBuilder;
import fr.cleangang.justvolleyball.util.Msg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.regex.Pattern;

/** Création d'arène à partir de la sélection de la faux (utilisé par /jvb create et par le menu admin). */
public final class ArenaService {

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{2,24}");

    private ArenaService() {
    }

    public static Integer parseType(String text) {
        if (text == null) {
            return null;
        }
        String t = text.toLowerCase(Locale.ROOT);
        for (int n = 1; n <= 6; n++) {
            if (t.equals(n + "v" + n)) {
                return n;
            }
        }
        return null;
    }

    public static int minWidth(int size) {
        return Math.max(5, size + 3);
    }

    public static int minLength(int size) {
        return Math.max(9, 2 * (size + 3) + 1);
    }

    public static String autoName(JustVolleyballMC plugin) {
        int n = 1;
        while (plugin.arenas().exists("terrain-" + n)) {
            n++;
        }
        return "terrain-" + n;
    }

    /** Retourne un message d'erreur (texte brut) ou null si la création est lancée. */
    public static String create(JustVolleyballMC plugin, Player p, String name, int size, Arena.Mode mode) {
        if (!NAME.matcher(name).matches()) {
            return "Nom invalide (2 à 24 caractères : lettres, chiffres, - et _).";
        }
        if (plugin.arenas().exists(name)) {
            return "Une arène porte déjà ce nom.";
        }
        Location[] sel = plugin.wand().get(p.getUniqueId());
        if (sel == null || sel[0] == null || sel[1] == null) {
            return "Sélection incomplète : prends la faux (menu admin) et clique 2 coins.";
        }
        World world = sel[0].getWorld();
        if (world == null || !world.equals(sel[1].getWorld())) {
            return "Les deux points doivent être dans le même monde.";
        }

        int minX = Math.min(sel[0].getBlockX(), sel[1].getBlockX());
        int maxX = Math.max(sel[0].getBlockX(), sel[1].getBlockX());
        int minZ = Math.min(sel[0].getBlockZ(), sel[1].getBlockZ());
        int maxZ = Math.max(sel[0].getBlockZ(), sel[1].getBlockZ());
        int floorY = sel[0].getBlockY();

        int dx = maxX - minX + 1;
        int dz = maxZ - minZ + 1;
        boolean alongX = dx >= dz;
        String note = "";
        int length = alongX ? dx : dz;
        if (length % 2 == 0) {
            if (alongX) {
                maxX--;
            } else {
                maxZ--;
            }
            length--;
            note = " <gray>(longueur ajustée à " + length + " pour un filet centré)</gray>";
        }
        int width = alongX ? (maxZ - minZ + 1) : (maxX - minX + 1);
        if (width < minWidth(size) || length < minLength(size)) {
            return "Zone trop petite pour du " + size + "v" + size + " : il faut au moins "
                    + minLength(size) + "x" + minWidth(size) + " blocs (tu as " + length + "x" + width + ").";
        }
        if (width * length > 1500) {
            return "Zone trop grande (maximum 1500 blocs de surface).";
        }

        Arena arena = new Arena(name, world.getName(), minX, maxX, minZ, maxZ, floorY, size, mode, alongX);
        Arena clash = plugin.arenas().overlapping(arena);
        if (clash != null) {
            return "Cette zone touche l'arène " + clash.name() + ".";
        }
        plugin.arenas().add(arena);
        plugin.wand().clear(p.getUniqueId());
        Msg.send(p, "Création de l'arène <white>" + Msg.esc(name) + "</white> (" + size + "v" + size + ", " + mode.label()
                + ", " + length + "x" + width + ")" + note + " : construction du terrain...");
        CourtBuilder.build(plugin, arena, () -> Msg.send(p, "<green>Terrain construit !</green> <gray>Fixe le lobby depuis "
                + "le menu admin ou avec /jvb setlobby " + Msg.esc(name) + ".</gray>"));
        return null;
    }
}
