package fr.cleangang.justvolleyball;

import fr.cleangang.justvolleyball.admin.WandManager;
import fr.cleangang.justvolleyball.arena.ArenaManager;
import fr.cleangang.justvolleyball.command.JvbCommand;
import fr.cleangang.justvolleyball.config.Settings;
import fr.cleangang.justvolleyball.data.PlayerDataManager;
import fr.cleangang.justvolleyball.data.StatsManager;
import fr.cleangang.justvolleyball.game.Ball;
import fr.cleangang.justvolleyball.game.GameManager;
import fr.cleangang.justvolleyball.game.ReplayPlayer;
import fr.cleangang.justvolleyball.listener.GameListener;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.ArmorStand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * JustVolleyballMC.
 *
 * Isolation vis-à-vis des autres plugins du serveur :
 *  - une seule commande : /jvb (alias /justvolleyball), permissions justvolleyball.*
 *  - fichiers de données séparés (arenas.yml, stats.yml, players.yml) dans plugins/JustVolleyballMC
 *  - aucun scoreboard/team touché (pas de conflit avec les sidebars des autres plugins), bossbar uniquement
 *  - aucune modification d'inventaire (sauf une "main de volley" en main secondaire si les 2 mains sont vides)
 *  - tous les événements ne concernent que les joueurs inscrits dans une partie, ou nos propres entités/objets
 *  - entités de la balle non persistantes, taguées jvb_ball / jvb_ghost, nettoyées au chargement
 */
public final class JustVolleyballMC extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenas;
    private GameManager games;
    private WandManager wand;
    private StatsManager stats;
    private PlayerDataManager playerData;
    private NamespacedKey ballKey;
    private NamespacedKey handKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(getConfig());
        ballKey = new NamespacedKey(this, "ball_entity");
        handKey = new NamespacedKey(this, "volley_hand");

        arenas = new ArenaManager(this);
        stats = new StatsManager(this);
        playerData = new PlayerDataManager(this);
        wand = new WandManager(this);
        games = new GameManager(this);

        getServer().getPluginManager().registerEvents(new GameListener(this), this);

        PluginCommand cmd = getCommand("jvb");
        if (cmd != null) {
            JvbCommand executor = new JvbCommand(this);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        cleanupStaleEntities();
        games.start();
        wand.startOutlineTask();
        getLogger().info("JustVolleyballMC activé (" + arenas.all().size() + " arène(s)).");
    }

    @Override
    public void onDisable() {
        if (games != null) {
            games.shutdown();
        }
        if (stats != null) {
            stats.save();
        }
        if (playerData != null) {
            playerData.save();
        }
    }

    public void reloadAll() {
        reloadConfig();
        settings.reload(getConfig());
    }

    /** Supprime les armor stands de balle/replay oubliés (crash, reload). */
    public void cleanupStaleEntities() {
        for (World w : getServer().getWorlds()) {
            for (ArmorStand as : w.getEntitiesByClass(ArmorStand.class)) {
                if (isOurEntity(as)) {
                    as.remove();
                }
            }
        }
    }

    public boolean isOurEntity(org.bukkit.entity.Entity e) {
        return e.getScoreboardTags().contains(Ball.TAG) || e.getScoreboardTags().contains(ReplayPlayer.TAG);
    }

    public Settings settings() {
        return settings;
    }

    public ArenaManager arenas() {
        return arenas;
    }

    public GameManager games() {
        return games;
    }

    public WandManager wand() {
        return wand;
    }

    public StatsManager stats() {
        return stats;
    }

    public PlayerDataManager playerData() {
        return playerData;
    }

    public NamespacedKey ballKey() {
        return ballKey;
    }

    public NamespacedKey handKey() {
        return handKey;
    }
}
