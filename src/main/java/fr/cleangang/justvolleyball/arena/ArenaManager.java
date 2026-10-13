package fr.cleangang.justvolleyball.arena;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stockage des arènes dans arenas.yml (fichier propre au plugin). */
public final class ArenaManager {

    private final JustVolleyballMC plugin;
    private final File file;
    private final Map<String, Arena> arenas = new LinkedHashMap<>();

    public ArenaManager(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "arenas.yml");
        load();
    }

    private void load() {
        arenas.clear();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("arenas");
        if (root == null) {
            return;
        }
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) {
                continue;
            }
            Arena a = Arena.load(s);
            if (a != null) {
                arenas.put(a.name().toLowerCase(Locale.ROOT), a);
            }
        }
        plugin.getLogger().info(arenas.size() + " arène(s) chargée(s).");
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Arena a : arenas.values()) {
            a.save(yaml.createSection("arenas." + a.name().toLowerCase(Locale.ROOT)));
        }
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder arenas.yml : " + e.getMessage());
        }
    }

    public Arena get(String name) {
        return name == null ? null : arenas.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return get(name) != null;
    }

    public void add(Arena a) {
        arenas.put(a.name().toLowerCase(Locale.ROOT), a);
        save();
    }

    public void remove(String name) {
        arenas.remove(name.toLowerCase(Locale.ROOT));
        save();
    }

    public Collection<Arena> all() {
        return new ArrayList<>(arenas.values());
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Arena a : arenas.values()) {
            out.add(a.name());
        }
        return out;
    }

    /** Vrai si le rectangle de l'arène candidate touche (à 3 blocs près) une arène existante. */
    public Arena overlapping(Arena candidate) {
        for (Arena a : arenas.values()) {
            if (a.overlaps(candidate, 3)) {
                return a;
            }
        }
        return null;
    }
}
