package fr.cleangang.justvolleyball.data;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Choix cosmétiques des joueurs (players.yml) : skin de balle et traînée. */
public final class PlayerDataManager {

    private final JustVolleyballMC plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public PlayerDataManager(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder players.yml : " + e.getMessage());
        }
    }

    public String skin(UUID id) {
        return yaml.getString("players." + id + ".skin", "classic");
    }

    public void setSkin(UUID id, String skin) {
        yaml.set("players." + id + ".skin", skin);
        save();
    }

    /** Id de la traînée, ou null si aucune. */
    public String trail(UUID id) {
        String t = yaml.getString("players." + id + ".trail");
        return (t == null || t.isEmpty() || t.equals("off")) ? null : t;
    }

    public void setTrail(UUID id, String trail) {
        yaml.set("players." + id + ".trail", trail == null ? "off" : trail);
        save();
    }
}
