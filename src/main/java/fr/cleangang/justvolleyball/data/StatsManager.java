package fr.cleangang.justvolleyball.data;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Statistiques et classement Elo des matchs classés (stats.yml). */
public final class StatsManager {

    public static final int START_ELO = 1000;
    private static final double K = 24.0;

    public record Entry(UUID id, String name, int elo, int wins, int losses, int points) {
    }

    private final JustVolleyballMC plugin;
    private final File file;
    private YamlConfiguration yaml;

    public StatsManager(JustVolleyballMC plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            plugin.getDataFolder().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Impossible de sauvegarder stats.yml : " + e.getMessage());
        }
    }

    private String path(UUID id) {
        return "players." + id;
    }

    public Entry get(UUID id, String fallbackName) {
        ConfigurationSection s = yaml.getConfigurationSection(path(id));
        if (s == null) {
            return new Entry(id, fallbackName, START_ELO, 0, 0, 0);
        }
        return new Entry(id, s.getString("name", fallbackName), s.getInt("elo", START_ELO),
                s.getInt("wins"), s.getInt("losses"), s.getInt("points"));
    }

    public List<Entry> top(int limit) {
        List<Entry> all = new ArrayList<>();
        ConfigurationSection root = yaml.getConfigurationSection("players");
        if (root != null) {
            for (String key : root.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    all.add(get(id, "?"));
                } catch (IllegalArgumentException ignored) {
                    // clé invalide
                }
            }
        }
        all.sort(Comparator.comparingInt(Entry::elo).reversed());
        return all.size() > limit ? new ArrayList<>(all.subList(0, limit)) : all;
    }

    /**
     * Enregistre un match classé terminé.
     * winners/losers : joueurs (UUID -> nom) ; pointsFor = points marqués par chaque camp.
     */
    public void recordMatch(List<UUID> winners, List<String> winnerNames, int winnerPoints,
                            List<UUID> losers, List<String> loserNames, int loserPoints) {
        if (winners.isEmpty() || losers.isEmpty()) {
            return;
        }
        double avgW = average(winners);
        double avgL = average(losers);
        double expectedW = 1.0 / (1.0 + Math.pow(10.0, (avgL - avgW) / 400.0));
        int delta = (int) Math.round(K * (1.0 - expectedW));
        delta = Math.max(1, delta);

        for (int i = 0; i < winners.size(); i++) {
            apply(winners.get(i), winnerNames.get(i), delta, true, winnerPoints);
        }
        for (int i = 0; i < losers.size(); i++) {
            apply(losers.get(i), loserNames.get(i), -delta, false, loserPoints);
        }
        save();
    }

    private double average(List<UUID> ids) {
        double sum = 0;
        for (UUID id : ids) {
            sum += yaml.getInt(path(id) + ".elo", START_ELO);
        }
        return sum / ids.size();
    }

    private void apply(UUID id, String name, int eloDelta, boolean win, int points) {
        String p = path(id);
        yaml.set(p + ".name", name);
        yaml.set(p + ".elo", Math.max(0, yaml.getInt(p + ".elo", START_ELO) + eloDelta));
        if (win) {
            yaml.set(p + ".wins", yaml.getInt(p + ".wins") + 1);
        } else {
            yaml.set(p + ".losses", yaml.getInt(p + ".losses") + 1);
        }
        yaml.set(p + ".points", yaml.getInt(p + ".points") + points);
    }
}
