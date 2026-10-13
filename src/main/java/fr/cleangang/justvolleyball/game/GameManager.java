package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.arena.Arena;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Une Game par arène + un index joueur -> partie. Un seul timer pour tout le plugin. */
public final class GameManager {

    private final JustVolleyballMC plugin;
    private final Map<String, Game> games = new HashMap<>();
    private final Map<UUID, Game> byPlayer = new ConcurrentHashMap<>();
    private BukkitTask task;

    public GameManager(JustVolleyballMC plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private void tick() {
        for (Game g : new ArrayList<>(games.values())) {
            if (g.needsTick()) {
                g.tick();
            }
        }
    }

    public Game forArena(Arena arena) {
        return games.computeIfAbsent(arena.name().toLowerCase(Locale.ROOT), k -> new Game(plugin, arena));
    }

    public Game existing(String arenaName) {
        return games.get(arenaName.toLowerCase(Locale.ROOT));
    }

    public Game gameOf(UUID id) {
        return byPlayer.get(id);
    }

    void register(UUID id, Game game) {
        byPlayer.put(id, game);
    }

    void unregister(UUID id) {
        byPlayer.remove(id);
    }

    public void removeArena(String name) {
        Game g = games.remove(name.toLowerCase(Locale.ROOT));
        if (g != null) {
            g.forceStop();
            g.shutdown();
        }
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
        }
        for (Game g : new ArrayList<>(games.values())) {
            g.shutdown();
        }
        games.clear();
        byPlayer.clear();
    }
}
