package fr.cleangang.justvolleyball.config;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Lecture typée de config.yml (les valeurs manquantes retombent sur les défauts). */
public final class Settings {

    public record BallSkin(String id, String display, Material material, String texture) {
    }

    public record TrailDef(String id, String display, Particle particle, Material icon) {
    }

    private static final Map<String, Particle> PARTICLES = new HashMap<>();

    static {
        PARTICLES.put("crit", Particle.CRIT);
        PARTICLES.put("flame", Particle.FLAME);
        PARTICLES.put("end_rod", Particle.END_ROD);
        PARTICLES.put("cloud", Particle.CLOUD);
        PARTICLES.put("heart", Particle.HEART);
        PARTICLES.put("note", Particle.NOTE);
        PARTICLES.put("happy_villager", Particle.HAPPY_VILLAGER);
        PARTICLES.put("soul_fire_flame", Particle.SOUL_FIRE_FLAME);
        PARTICLES.put("snowflake", Particle.SNOWFLAKE);
        PARTICLES.put("firework", Particle.FIREWORK);
    }

    // arène
    public int netHeight;
    public boolean netCollision;
    public boolean outOfBounds;
    public boolean autoResetBall;
    public Material floorA, floorB, line, net, netTop, post;
    public int ceilingHeight;
    public boolean clearAir;

    // balle
    public double gravity, drag, bounce, hitbox, radius, maxSpeed, renderOffsetY;
    public boolean armorStandSmall;

    // visée / mouvement
    public boolean showTrajectory;
    public Particle trajectoryParticle;
    public boolean dashEnabled;
    public double dashCooldown;
    public double reach;

    // actions
    public double serveMaxPower, serveChargeSeconds, serveBase, serveGain, serveLift;
    public int serveTimeoutSeconds;
    public double passUp, passForward;
    public double spikeMinHeight, spikeDown, spikeForward;
    public double blockReflect, blockNetDistance;
    public int blockWindowTicks;

    // règles
    public int maxTouches;
    public boolean forbidDoubleTouch;
    public boolean resetPositions;
    public int pauseTicks;

    // modes
    public boolean rankedEnabled;
    public int rankedPoints, rankedMargin, rankedCountdown;
    public boolean openEnabled, openFreePlay;
    public int openMax, openCountdown;
    public double chatRadius;

    // cosmétiques
    public boolean skinsEnabled, trailsEnabled, replayEnabled;
    public int replaySeconds;
    public final Map<String, BallSkin> skins = new LinkedHashMap<>();
    public final Map<String, TrailDef> trails = new LinkedHashMap<>();

    public Settings(FileConfiguration c) {
        reload(c);
    }

    public void reload(FileConfiguration c) {
        netHeight = Math.max(1, c.getInt("arena_settings.net.height_blocks", 3));
        netCollision = c.getBoolean("arena_settings.net.collision_enabled", true);
        outOfBounds = c.getBoolean("arena_settings.zones.out_of_bounds_detection", true);
        autoResetBall = c.getBoolean("arena_settings.zones.auto_reset_ball_on_point", true);
        floorA = mat(c.getString("arena_settings.court.floor_team_a"), Material.RED_TERRACOTTA);
        floorB = mat(c.getString("arena_settings.court.floor_team_b"), Material.LIGHT_BLUE_TERRACOTTA);
        line = mat(c.getString("arena_settings.court.lines"), Material.WHITE_CONCRETE);
        net = mat(c.getString("arena_settings.court.net"), Material.WHITE_STAINED_GLASS_PANE);
        netTop = mat(c.getString("arena_settings.court.net_top"), Material.BLACK_STAINED_GLASS_PANE);
        post = mat(c.getString("arena_settings.court.posts"), Material.BIRCH_FENCE);
        ceilingHeight = Math.max(netHeight + 4, c.getInt("arena_settings.court.ceiling_clear_height", 12));
        clearAir = c.getBoolean("arena_settings.court.clear_air", true);

        gravity = c.getDouble("ball_physics.gravity", 0.04);
        drag = c.getDouble("ball_physics.drag", 0.98);
        bounce = c.getDouble("ball_physics.bounce_elasticity", 0.6);
        hitbox = c.getDouble("ball_physics.hitbox_size", 0.8);
        radius = c.getDouble("ball_physics.radius", 0.25);
        maxSpeed = c.getDouble("ball_physics.max_speed", 2.5);
        renderOffsetY = c.getDouble("ball_physics.render_offset_y", 1.72);
        armorStandSmall = c.getBoolean("ball_physics.armor_stand_small", false);

        showTrajectory = c.getBoolean("gameplay_mechanics.aiming_system.show_trajectory_particles", true);
        trajectoryParticle = particle(c.getString("gameplay_mechanics.aiming_system.trajectory_particle_type"), Particle.CRIT);
        dashEnabled = c.getBoolean("gameplay_mechanics.player_movement.allow_dash_slide", true);
        dashCooldown = c.getDouble("gameplay_mechanics.player_movement.dash_cooldown_seconds", 2.5);
        reach = c.getDouble("gameplay_mechanics.reach", 3.0);

        serveMaxPower = c.getDouble("gameplay_mechanics.actions.serve.max_power", 1.5);
        serveChargeSeconds = Math.max(0.2, c.getDouble("gameplay_mechanics.actions.serve.charge_seconds", 1.5));
        serveBase = c.getDouble("gameplay_mechanics.actions.serve.base_speed", 0.35);
        serveGain = c.getDouble("gameplay_mechanics.actions.serve.power_speed_gain", 0.4);
        serveLift = c.getDouble("gameplay_mechanics.actions.serve.lift", 0.25);
        serveTimeoutSeconds = Math.max(5, c.getInt("gameplay_mechanics.actions.serve.timeout_seconds", 15));
        passUp = c.getDouble("gameplay_mechanics.actions.pass_bump.upward_force", 0.8);
        passForward = c.getDouble("gameplay_mechanics.actions.pass_bump.forward_speed", 0.5);
        spikeMinHeight = c.getDouble("gameplay_mechanics.actions.spike_smash.min_jump_height", 1.2);
        spikeDown = c.getDouble("gameplay_mechanics.actions.spike_smash.downward_force", 1.4);
        spikeForward = c.getDouble("gameplay_mechanics.actions.spike_smash.forward_speed", 1.0);
        blockReflect = c.getDouble("gameplay_mechanics.actions.block.reflection_multiplier", 0.9);
        blockWindowTicks = Math.max(4, c.getInt("gameplay_mechanics.actions.block.window_ticks", 12));
        blockNetDistance = c.getDouble("gameplay_mechanics.actions.block.net_distance", 1.8);

        maxTouches = Math.max(1, c.getInt("gameplay_mechanics.rules.max_touches_per_side", 3));
        forbidDoubleTouch = c.getBoolean("gameplay_mechanics.rules.forbid_double_touch", true);
        resetPositions = c.getBoolean("gameplay_mechanics.rules.reset_positions_each_rally", true);
        pauseTicks = Math.max(20, c.getInt("gameplay_mechanics.rules.point_pause_ticks", 60));

        rankedEnabled = c.getBoolean("game_modes.ranked_match.enabled", true);
        rankedPoints = Math.max(1, c.getInt("game_modes.ranked_match.points_to_win", 25));
        rankedMargin = Math.max(1, c.getInt("game_modes.ranked_match.win_by_margin", 2));
        rankedCountdown = Math.max(1, c.getInt("game_modes.ranked_match.countdown_seconds", 10));
        openEnabled = c.getBoolean("game_modes.open_gym.enabled", true);
        openMax = Math.max(2, c.getInt("game_modes.open_gym.max_players_lobby", 24));
        openFreePlay = c.getBoolean("game_modes.open_gym.free_play_courts", true);
        chatRadius = c.getDouble("game_modes.open_gym.chat_radius", 30);
        openCountdown = Math.max(1, c.getInt("game_modes.open_gym.countdown_seconds", 5));

        skinsEnabled = c.getBoolean("cosmetics_and_customization.ball_skins_enabled", true);
        trailsEnabled = c.getBoolean("cosmetics_and_customization.player_trail_particles", true);
        replayEnabled = c.getBoolean("cosmetics_and_customization.replay_system.save_last_point_replay", true);
        replaySeconds = Math.max(1, c.getInt("cosmetics_and_customization.replay_system.replay_duration_seconds", 5));

        skins.clear();
        ConfigurationSection sk = c.getConfigurationSection("ball_skins");
        if (sk != null) {
            for (String id : sk.getKeys(false)) {
                ConfigurationSection s = sk.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                skins.put(id.toLowerCase(Locale.ROOT), new BallSkin(id.toLowerCase(Locale.ROOT),
                        s.getString("name", id), mat(s.getString("material"), Material.SNOW_BLOCK),
                        s.getString("texture", "")));
            }
        }
        if (skins.isEmpty()) {
            skins.put("classic", new BallSkin("classic", "Classique", Material.SNOW_BLOCK, ""));
        }

        trails.clear();
        ConfigurationSection tr = c.getConfigurationSection("trails");
        if (tr != null) {
            for (String id : tr.getKeys(false)) {
                ConfigurationSection s = tr.getConfigurationSection(id);
                if (s == null) {
                    continue;
                }
                trails.put(id.toLowerCase(Locale.ROOT), new TrailDef(id.toLowerCase(Locale.ROOT),
                        s.getString("name", id), particle(s.getString("particle"), Particle.CRIT),
                        mat(s.getString("icon"), Material.PAPER)));
            }
        }
    }

    public BallSkin defaultSkin() {
        return skins.containsKey("classic") ? skins.get("classic") : skins.values().iterator().next();
    }

    private static Material mat(String name, Material def) {
        if (name == null) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }

    private static Particle particle(String name, Particle def) {
        if (name == null) {
            return def;
        }
        Particle p = PARTICLES.get(name.toLowerCase(Locale.ROOT));
        return p == null ? def : p;
    }
}
