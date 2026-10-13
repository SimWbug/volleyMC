package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.config.Settings;
import fr.cleangang.justvolleyball.util.HandItem;
import fr.cleangang.justvolleyball.util.Msg;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Une partie sur une arène : lobby, compte à rebours, service, échanges, points, fin de match. */
public final class Game {

    public enum State {
        WAITING, COUNTDOWN, SERVING, RALLY, POINT_PAUSE, ENDED
    }

    private static final class Participant {
        final UUID id;
        final String name;
        TeamSide side;
        boolean bench;
        Location previousLocation;
        GameMode previousMode;
        long hitCooldownUntil;
        long dashReadyAt;
        long blockUntil;
        long lastClickTick = -1;
        boolean wasOnGround = true;

        Participant(UUID id, String name, TeamSide side) {
            this.id = id;
            this.name = name;
            this.side = side;
        }
    }

    private final JustVolleyballMC plugin;
    private final Arena arena;
    private final Random random = new Random();
    private final Map<UUID, Participant> parts = new LinkedHashMap<>();
    private final ReplayRecorder recorder = new ReplayRecorder();

    private State state = State.WAITING;
    private long tick = 0;
    private long stateUntil = 0;
    private long totalCountdownTicks = 1;
    private boolean forced = false;
    private boolean closing = false;

    private int scoreA, scoreB;
    private TeamSide serving = TeamSide.A;
    private final int[] rotation = new int[2];
    private UUID serverId;
    private long serveDeadline;
    private long chargeStart = -1;
    private long lastChargeEvent;
    private long rallyStart;
    private long lastBounceSound;

    private Ball ball;
    private ItemStack currentHead;
    private ReplayRecorder.Data lastReplay;
    private ReplayPlayer replay;
    private BossBar bar;

    public Game(JustVolleyballMC plugin, Arena arena) {
        this.plugin = plugin;
        this.arena = arena;
    }

    private Settings s() {
        return plugin.settings();
    }

    // ------------------------------------------------------------------ infos

    public Arena arena() {
        return arena;
    }

    public State state() {
        return state;
    }

    public int scoreA() {
        return scoreA;
    }

    public int scoreB() {
        return scoreB;
    }

    public int playerCount() {
        return parts.size();
    }

    public boolean isParticipant(UUID id) {
        return parts.containsKey(id);
    }

    public boolean needsTick() {
        return !parts.isEmpty() || replay != null;
    }

    public boolean isBench(UUID id) {
        Participant p = parts.get(id);
        return p != null && p.bench;
    }

    public int activeCount(TeamSide side) {
        int n = 0;
        for (Participant p : parts.values()) {
            if (!p.bench && p.side == side) {
                n++;
            }
        }
        return n;
    }

    private List<Participant> active(TeamSide side) {
        List<Participant> out = new ArrayList<>();
        for (Participant p : parts.values()) {
            if (!p.bench && p.side == side) {
                out.add(p);
            }
        }
        return out;
    }

    private boolean practice() {
        return activeCount(TeamSide.A) == 0 || activeCount(TeamSide.B) == 0;
    }

    private boolean ranked() {
        return arena.mode() == Arena.Mode.RANKED;
    }

    // ------------------------------------------------------------------ rejoindre / quitter

    /** Retourne un message d'erreur, ou null si le joueur a bien rejoint. */
    public String join(Player p, TeamSide preferred) {
        Settings s = s();
        if (ranked() && !s.rankedEnabled) {
            return "Le mode classé est désactivé.";
        }
        if (!ranked() && !s.openEnabled) {
            return "L'open gym est désactivé.";
        }
        if (arena.world() == null) {
            return "Le monde de cette arène est introuvable.";
        }
        if (parts.containsKey(p.getUniqueId())) {
            return "Tu es déjà dans cette arène.";
        }
        if (state == State.ENDED) {
            return "Le match vient de se terminer, réessaie dans un instant.";
        }
        if (ranked() && state != State.WAITING && state != State.COUNTDOWN) {
            return "Un match classé est déjà en cours sur cette arène.";
        }
        int cap = ranked() ? arena.teamSize() * 2 : s.openMax;
        if (parts.size() >= cap) {
            return "Le lobby est plein (" + cap + " joueurs).";
        }

        int ca = activeCount(TeamSide.A);
        int cb = activeCount(TeamSide.B);
        int size = arena.teamSize();
        TeamSide side = null;
        if (preferred != null && activeCount(preferred) < size) {
            side = preferred;
        }
        if (side == null) {
            if (ca < size && (ca <= cb || cb >= size)) {
                side = TeamSide.A;
            } else if (cb < size) {
                side = TeamSide.B;
            }
        }
        boolean bench = false;
        if (side == null) {
            bench = true;
            side = ca <= cb ? TeamSide.A : TeamSide.B;
        }

        // l'inventaire est mis de côté sur disque AVANT d'être vidé ; si l'écriture échoue, on n'entre pas en partie
        if (!plugin.inventories().save(p)) {
            return "Impossible de sauvegarder ton inventaire : partie annulée (rien n'a été touché).";
        }

        Participant part = new Participant(p.getUniqueId(), p.getName(), side);
        part.bench = bench;
        part.previousLocation = p.getLocation().clone();
        part.previousMode = p.getGameMode();
        parts.put(p.getUniqueId(), part);
        plugin.games().register(p.getUniqueId(), this);

        p.setGameMode(GameMode.ADVENTURE);
        giveKit(p);
        ensureBar();
        p.showBossBar(bar);

        boolean running = state == State.SERVING || state == State.RALLY || state == State.POINT_PAUSE;
        if (running && !bench) {
            teleportToFormation(p, part);
        } else {
            p.teleport(arena.lobby());
        }

        if (bench) {
            Msg.send(p, "<yellow>Terrain plein</yellow> : tu es sur le banc, tu entreras quand une place se libère.");
        } else {
            Msg.send(p, "Tu rejoins l'arène <white>" + Msg.esc(arena.name()) + "</white> (" + arena.mode().label()
                    + ", " + arena.typeLabel() + ") dans le camp " + side.colored() + ".");
        }
        broadcastExcept(p.getUniqueId(), "<white>" + Msg.esc(p.getName()) + "</white> a rejoint "
                + (bench ? "le banc" : "le camp " + side.colored()) + " <gray>(" + parts.size() + " joueur(s))</gray>");
        evaluateStart();
        return null;
    }

    /**
     * Inventaire de jeu : tout est vidé (l'original est sur disque), il ne reste que l'objet "Quitter"
     * (slot 9) et la main de volley en main secondaire. Aussi utilisé après une mort en partie.
     */
    public void giveKit(Player p) {
        PlayerInventory inv = p.getInventory();
        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));
        inv.setHeldItemSlot(0);
        inv.setItem(8, plugin.menuItems().leave());
        HandItem.giveIfHandsEmpty(p, plugin.handKey());
        p.updateInventory();
    }

    public void leave(UUID id, boolean restore) {
        Participant part = parts.remove(id);
        if (part == null) {
            return;
        }
        plugin.games().unregister(id);
        Player pl = Bukkit.getPlayer(id);
        if (pl != null) {
            if (bar != null) {
                pl.hideBossBar(bar);
            }
            // l'inventaire est toujours rendu, même si la position n'est pas restaurée (changement de monde, kick...)
            if (!plugin.inventories().restore(pl)) {
                HandItem.removeAll(pl, plugin.handKey());
                plugin.menuItems().removeLeave(pl);
            }
            if (part.previousMode != null) {
                pl.setGameMode(part.previousMode);
            }
            if (restore && part.previousLocation != null && part.previousLocation.getWorld() != null) {
                pl.teleport(part.previousLocation);
            }
            plugin.menuItems().give(pl);
            if (restore && !closing) {
                Msg.send(pl, "Tu as quitté l'arène <white>" + Msg.esc(arena.name()) + "</white>.");
            }
        }
        if (closing) {
            return;
        }
        afterLeave(part);
    }

    private void afterLeave(Participant part) {
        if (parts.isEmpty()) {
            resetGame();
            return;
        }
        broadcast("<white>" + Msg.esc(part.name) + "</white> a quitté la partie.");
        if (!part.bench) {
            promoteBench(part.side);
        }
        int size = arena.teamSize();
        if (state == State.COUNTDOWN && ranked() && !forced
                && (activeCount(TeamSide.A) < size || activeCount(TeamSide.B) < size)) {
            state = State.WAITING;
            broadcast("<yellow>Compte à rebours annulé : il manque des joueurs.</yellow>");
            return;
        }
        boolean running = state == State.SERVING || state == State.RALLY || state == State.POINT_PAUSE;
        if (running) {
            if (ranked() && practice()) {
                broadcast("<red>Match annulé : un camp est vide.</red>");
                removeBall();
                state = State.ENDED;
                stateUntil = tick + 60;
                return;
            }
            if (state == State.SERVING && part.id.equals(serverId)) {
                prepareRally();
            }
        }
    }

    private void promoteBench(TeamSide side) {
        if (activeCount(side) >= arena.teamSize()) {
            return;
        }
        for (Participant p : parts.values()) {
            if (p.bench) {
                p.bench = false;
                p.side = side;
                Player pl = Bukkit.getPlayer(p.id);
                if (pl != null) {
                    Msg.send(pl, "<green>Une place se libère !</green> Tu entres dans le camp " + side.colored() + ".");
                    boolean running = state == State.SERVING || state == State.RALLY || state == State.POINT_PAUSE;
                    if (running) {
                        teleportToFormation(pl, p);
                    }
                }
                return;
            }
        }
    }

    // ------------------------------------------------------------------ démarrage

    private void evaluateStart() {
        if (state != State.WAITING) {
            return;
        }
        Settings s = s();
        if (ranked()) {
            int size = arena.teamSize();
            if (activeCount(TeamSide.A) == size && activeCount(TeamSide.B) == size) {
                beginCountdown(s.rankedCountdown);
            } else {
                broadcast("Classé : <white>" + (activeCount(TeamSide.A) + activeCount(TeamSide.B)) + "/" + (size * 2)
                        + "</white> joueurs. Le match démarre quand le terrain est plein.");
            }
        } else if (activeCount(TeamSide.A) + activeCount(TeamSide.B) >= 1) {
            beginCountdown(s.openCountdown);
        }
    }

    private void beginCountdown(int seconds) {
        state = State.COUNTDOWN;
        totalCountdownTicks = Math.max(1, seconds * 20L);
        stateUntil = tick + totalCountdownTicks;
        broadcast("Début de la partie dans <white>" + seconds + "</white> secondes !");
    }

    public void forceStart() {
        if (state == State.WAITING || state == State.COUNTDOWN) {
            forced = true;
            state = State.COUNTDOWN;
            totalCountdownTicks = 60;
            stateUntil = tick + 60;
            broadcast("<gold>Démarrage forcé par un admin.</gold>");
        }
    }

    public void forceStop() {
        closing = true;
        for (UUID id : new ArrayList<>(parts.keySet())) {
            Player pl = Bukkit.getPlayer(id);
            if (pl != null) {
                Msg.send(pl, "<red>La partie a été arrêtée par un admin.</red>");
            }
            leave(id, true);
        }
        closing = false;
        resetGame();
    }

    private void startMatch() {
        scoreA = 0;
        scoreB = 0;
        rotation[0] = 0;
        rotation[1] = 0;
        serving = random.nextBoolean() ? TeamSide.A : TeamSide.B;
        recorder.clear();
        broadcast("<green>C'est parti !</green> Premier service : " + serving.colored() + ".");
        prepareRally();
    }

    private void resetGame() {
        removeBall();
        state = State.WAITING;
        scoreA = 0;
        scoreB = 0;
        rotation[0] = 0;
        rotation[1] = 0;
        serverId = null;
        chargeStart = -1;
        forced = false;
        if (bar != null) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.hideBossBar(bar);
            }
            bar = null;
        }
    }

    // ------------------------------------------------------------------ échange

    private int indexInTeam(Participant target) {
        int i = 0;
        for (Participant p : parts.values()) {
            if (!p.bench && p.side == target.side) {
                if (p == target) {
                    return i;
                }
                i++;
            }
        }
        return 0;
    }

    private void teleportToFormation(Player pl, Participant part) {
        Location l = arena.spawn(part.side, indexInTeam(part));
        pl.teleport(l);
        pl.setVelocity(new Vector(0, 0, 0));
        pl.setFallDistance(0f);
    }

    private void prepareRally() {
        removeBall();
        if (active(TeamSide.A).isEmpty() && active(TeamSide.B).isEmpty()) {
            resetGame();
            return;
        }
        Settings s = s();
        TeamSide srv = serving;
        if (active(srv).isEmpty()) {
            srv = srv.other();
            serving = srv;
        }
        List<Participant> list = active(srv);
        Participant server = list.get(Math.floorMod(rotation[srv.ordinal()], list.size()));
        serverId = server.id;

        if (s.resetPositions) {
            for (TeamSide side : TeamSide.values()) {
                List<Participant> team = active(side);
                for (int i = 0; i < team.size(); i++) {
                    Participant part = team.get(i);
                    Player pl = Bukkit.getPlayer(part.id);
                    if (pl == null) {
                        continue;
                    }
                    Location l = part.id.equals(serverId) ? arena.servePos(side) : arena.spawn(side, i);
                    pl.teleport(l);
                    pl.setVelocity(new Vector(0, 0, 0));
                    pl.setFallDistance(0f);
                }
            }
        } else {
            Player sp = Bukkit.getPlayer(serverId);
            if (sp != null) {
                sp.teleport(arena.servePos(srv));
            }
        }

        Player serverPlayer = Bukkit.getPlayer(serverId);
        if (serverPlayer == null) {
            return;
        }
        Settings.BallSkin skin = s.defaultSkin();
        if (s.skinsEnabled) {
            Settings.BallSkin chosen = s.skins.get(plugin.playerData().skin(serverId));
            if (chosen != null) {
                skin = chosen;
            }
        }
        currentHead = SkinFactory.head(skin);
        recorder.setMaxFrames(s.replaySeconds * 20);
        recorder.clear();

        ball = new Ball(s, heldBallPos(serverPlayer).toLocation(arena.world()), currentHead, plugin.ballKey());
        ball.held = true;
        ball.lastTeam = null;
        ball.lastPlayer = null;
        ball.touches = 0;

        state = State.SERVING;
        serveDeadline = tick + s.serveTimeoutSeconds * 20L;
        chargeStart = -1;
        for (Participant part : parts.values()) {
            Player pl = Bukkit.getPlayer(part.id);
            if (pl == null) {
                continue;
            }
            if (part.id.equals(serverId)) {
                Msg.bar(pl, "<yellow>À toi de servir !</yellow> <gray>Maintiens le clic droit pour charger, relâche pour tirer.</gray>");
            } else {
                Msg.bar(pl, "<gray>Service de</gray> " + serving.colored() + " <gray>:</gray> <white>" + Msg.esc(server.name) + "</white>");
            }
        }
    }

    private Vector heldBallPos(Player server) {
        Location eye = server.getEyeLocation();
        return eye.toVector().add(eye.getDirection().multiply(0.9)).add(new Vector(0, -0.55, 0));
    }

    private void removeBall() {
        if (ball != null) {
            ball.remove();
            ball = null;
        }
    }

    // ------------------------------------------------------------------ boucle

    public void tick() {
        tick++;

        for (UUID id : new ArrayList<>(parts.keySet())) {
            Player pl = Bukkit.getPlayer(id);
            if (pl == null || !pl.isOnline()) {
                leave(id, false);
            }
        }

        if (replay != null && !replay.tick()) {
            replay = null;
        }
        if (parts.isEmpty()) {
            return;
        }

        switch (state) {
            case COUNTDOWN -> tickCountdown();
            case SERVING -> tickServing();
            case RALLY -> tickRally();
            case POINT_PAUSE -> {
                if (tick >= stateUntil) {
                    prepareRally();
                }
            }
            case ENDED -> {
                if (tick >= stateUntil) {
                    finish();
                }
            }
            default -> {
            }
        }
        if (tick % 10 == 0) {
            updateBar();
        }
    }

    private void tickCountdown() {
        long remaining = stateUntil - tick;
        if (remaining <= 0) {
            startMatch();
            return;
        }
        if (remaining % 20 == 0 && remaining / 20 <= 5) {
            long sec = remaining / 20;
            for (Participant part : parts.values()) {
                Player pl = Bukkit.getPlayer(part.id);
                if (pl != null) {
                    pl.showTitle(Title.title(Msg.c("<yellow>" + sec), Msg.c("<gray>Préparez-vous"),
                            Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100))));
                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.0f + (5 - sec) * 0.1f);
                }
            }
        }
    }

    private void tickServing() {
        Player server = serverId == null ? null : Bukkit.getPlayer(serverId);
        if (server == null || !parts.containsKey(serverId) || ball == null || !ball.isValid()) {
            prepareRally();
            return;
        }
        ball.setPos(heldBallPos(server));
        ball.render();

        if (chargeStart >= 0) {
            double frac = Math.min(1.0, (tick - chargeStart) / (s().serveChargeSeconds * 20.0));
            if (tick % 2 == 0) {
                Msg.bar(server, "<yellow>Service</yellow> " + chargeBar(frac));
            }
            if (s().showTrajectory && tick % 3 == 0) {
                showPreview(server, ball.pos(), serveVelocity(server, frac));
            }
            if (tick - lastChargeEvent > 7) {
                doServe(server, frac);
                return;
            }
        }
        if (tick >= serveDeadline) {
            Msg.bar(server, "<red>Temps écoulé : service automatique.</red>");
            doServe(server, 0.4);
        }
    }

    private String chargeBar(double frac) {
        int filled = (int) Math.round(frac * 20);
        StringBuilder sb = new StringBuilder("<gold>");
        for (int i = 0; i < 20; i++) {
            if (i == filled) {
                sb.append("</gold><dark_gray>");
            }
            sb.append('|');
        }
        sb.append("</dark_gray> <white>").append((int) Math.round(frac * 100)).append("%");
        return sb.toString();
    }

    private Vector serveVelocity(Player p, double frac) {
        Settings s = s();
        double power = Math.max(0.2, Math.min(1.0, frac)) * s.serveMaxPower;
        double speed = s.serveBase + s.serveGain * power;
        Vector v = p.getEyeLocation().getDirection().normalize().multiply(speed);
        v.setY(v.getY() + s.serveLift);
        return v;
    }

    private void doServe(Player p, double frac) {
        Participant part = parts.get(p.getUniqueId());
        if (part == null || ball == null) {
            return;
        }
        ball.vel.copy(serveVelocity(p, frac));
        ball.held = false;
        ball.lastTeam = part.side;
        ball.lastPlayer = p.getUniqueId();
        ball.touches = 1;
        state = State.RALLY;
        rallyStart = tick;
        chargeStart = -1;
        recorder.clear();
        World w = arena.world();
        Location l = ball.location();
        w.playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);
        w.playSound(l, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1.4f);
        w.spawnParticle(Particle.CLOUD, l, 6, 0.15, 0.15, 0.15, 0.02);
    }

    private void tickRally() {
        if (ball == null || !ball.isValid()) {
            broadcast("<yellow>Le ballon a disparu, on relance.</yellow>");
            ball = null;
            state = State.POINT_PAUSE;
            stateUntil = tick + 40;
            return;
        }
        Settings s = s();

        Map<UUID, Vector> snap = new HashMap<>();
        for (Participant part : parts.values()) {
            if (part.bench) {
                continue;
            }
            Player pl = Bukkit.getPlayer(part.id);
            if (pl != null) {
                snap.put(part.id, pl.getLocation().toVector());
            }
        }

        BallPhysics.Contact c = ball.step(s, arena.floorTop());
        recorder.record(ball.pos(), snap);

        if (c == BallPhysics.Contact.SOLID && ball.vel.length() > 0.12 && tick - lastBounceSound > 3) {
            lastBounceSound = tick;
            arena.world().playSound(ball.location(), Sound.BLOCK_SLIME_BLOCK_HIT, 0.7f, 1.3f);
        }

        processBlocks();

        if (c == BallPhysics.Contact.GROUND) {
            resolveGround(false);
            return;
        }

        Location center = arena.center();
        Vector bp = ball.pos();
        double dist = Math.hypot(bp.getX() - center.getX(), bp.getZ() - center.getZ());
        if (dist > 45 || bp.getY() > arena.floorTop() + 60) {
            resolveGround(true);
            return;
        }
        if (tick - rallyStart > 20 * 90) {
            broadcast("<yellow>Échange interminable : on relance sans point.</yellow>");
            removeBall();
            state = State.POINT_PAUSE;
            stateUntil = tick + 40;
            return;
        }

        if (s.showTrajectory && tick % 3 == 0) {
            for (Participant part : parts.values()) {
                if (part.bench) {
                    continue;
                }
                Player pl = Bukkit.getPlayer(part.id);
                if (pl == null || !inReach(pl)) {
                    continue;
                }
                boolean air = !pl.isOnGround() && heightAboveGround(pl) >= s.spikeMinHeight;
                showPreview(pl, ball.pos(), air ? spikeVelocity(pl) : bumpVelocity(pl));
            }
        }
    }

    private void showPreview(Player viewer, Vector from, Vector velocity) {
        Settings s = s();
        World w = arena.world();
        List<Vector> pts = BallPhysics.simulate(w, from, velocity, s, arena.floorTop(), 60);
        for (Vector v : pts) {
            viewer.spawnParticle(s.trajectoryParticle, v.getX(), v.getY(), v.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ actions des joueurs

    /** Retourne true si le joueur est dans la partie (l'événement doit alors être annulé). */
    public boolean onInteract(Player p, boolean leftClick) {
        Participant part = parts.get(p.getUniqueId());
        if (part == null) {
            return false;
        }
        if (part.bench) {
            return true;
        }
        if (part.lastClickTick == tick && !leftClick) {
            return true; // doublon main principale / secondaire
        }
        part.lastClickTick = tick;

        if (state == State.SERVING) {
            if (!leftClick && p.getUniqueId().equals(serverId)) {
                if (chargeStart < 0) {
                    chargeStart = tick;
                }
                lastChargeEvent = tick;
            }
        } else if (state == State.RALLY) {
            if (leftClick) {
                trySpike(p, part);
            } else {
                tryBump(p, part);
            }
        }
        return true;
    }

    private boolean inReach(Player pl) {
        if (ball == null) {
            return false;
        }
        Settings s = s();
        Vector bp = ball.pos();
        Location loc = pl.getLocation();
        double max = s.reach + s.hitbox / 2.0;
        double dBody = bp.distance(new Vector(loc.getX(), loc.getY() + 1.0, loc.getZ()));
        double dEye = bp.distance(pl.getEyeLocation().toVector());
        return Math.min(dBody, dEye) <= max;
    }

    private double heightAboveGround(Player p) {
        RayTraceResult r = p.getWorld().rayTraceBlocks(p.getLocation(), new Vector(0, -1, 0), 8.0,
                FluidCollisionMode.NEVER, true);
        if (r == null) {
            return 99.0;
        }
        return p.getLocation().getY() - r.getHitPosition().getY();
    }

    private Vector horizontalLook(Player p) {
        Vector h = p.getEyeLocation().getDirection().setY(0);
        if (h.lengthSquared() < 1.0E-6) {
            return new Vector(0, 0, 0);
        }
        return h.normalize();
    }

    private Vector bumpVelocity(Player p) {
        Settings s = s();
        Vector h = horizontalLook(p);
        double pitchFactor = Math.max(0.35, Math.cos(Math.toRadians(p.getEyeLocation().getPitch())));
        return new Vector(h.getX() * s.passForward * pitchFactor, s.passUp, h.getZ() * s.passForward * pitchFactor);
    }

    private Vector spikeVelocity(Player p) {
        Settings s = s();
        Vector h = horizontalLook(p);
        double pitch = p.getEyeLocation().getPitch(); // positif = regarde vers le bas
        double down = s.spikeDown * (0.4 + 0.6 * Math.max(0.0, Math.sin(Math.toRadians(pitch))));
        return new Vector(h.getX() * s.spikeForward, -down, h.getZ() * s.spikeForward);
    }

    /** Vérifie les règles de touche. Retourne false et donne le point à l'adversaire en cas de faute. */
    private boolean registerTouch(Player p, Participant part) {
        Settings s = s();
        if (ball.lastTeam == part.side) {
            if (!practice()) {
                boolean multi = activeCount(part.side) > 1;
                if (s.forbidDoubleTouch && multi && p.getUniqueId().equals(ball.lastPlayer)) {
                    awardPoint(part.side.other(), "double touche de <white>" + Msg.esc(part.name) + "</white>");
                    return false;
                }
                if (ball.touches + 1 > s.maxTouches) {
                    awardPoint(part.side.other(), "plus de " + s.maxTouches + " touches pour " + part.side.colored());
                    return false;
                }
            }
            ball.touches++;
        } else {
            ball.touches = 1;
        }
        return true;
    }

    private void tryBump(Player p, Participant part) {
        if (tick < part.hitCooldownUntil || ball == null || ball.held || !inReach(p)) {
            return;
        }
        if (!registerTouch(p, part)) {
            return;
        }
        ball.vel.copy(bumpVelocity(p));
        ball.lastTeam = part.side;
        ball.lastPlayer = p.getUniqueId();
        part.hitCooldownUntil = tick + 6;
        Location l = ball.location();
        arena.world().playSound(l, Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.9f, 1.3f);
        arena.world().spawnParticle(Particle.CLOUD, l, 4, 0.1, 0.1, 0.1, 0.01);
        Msg.bar(p, "<aqua>Passe !</aqua> <gray>(touche " + ball.touches + "/" + s().maxTouches + ")</gray>");
    }

    private void trySpike(Player p, Participant part) {
        if (tick < part.hitCooldownUntil || ball == null || ball.held || !inReach(p)) {
            return;
        }
        Settings s = s();
        if (p.isOnGround() || heightAboveGround(p) < s.spikeMinHeight) {
            Msg.bar(p, "<yellow>Saute pour smasher !</yellow>");
            return;
        }
        if (!registerTouch(p, part)) {
            return;
        }
        ball.vel.copy(spikeVelocity(p));
        ball.lastTeam = part.side;
        ball.lastPlayer = p.getUniqueId();
        part.hitCooldownUntil = tick + 8;
        Location l = ball.location();
        arena.world().playSound(l, Sound.ENTITY_PLAYER_ATTACK_STRONG, 1.0f, 0.7f);
        arena.world().playSound(l, Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.9f);
        arena.world().spawnParticle(Particle.FLAME, l, 10, 0.15, 0.15, 0.15, 0.03);
        Msg.bar(p, "<red><bold>SMASH !</bold></red>");
    }

    private void processBlocks() {
        Settings s = s();
        for (Participant part : parts.values()) {
            if (part.bench || part.blockUntil <= tick || ball.lastTeam == part.side) {
                continue;
            }
            Player pl = Bukkit.getPlayer(part.id);
            if (pl == null) {
                continue;
            }
            Location hand = pl.getLocation().add(0, 1.9, 0);
            Vector bp = ball.pos();
            double dh = Math.hypot(bp.getX() - hand.getX(), bp.getZ() - hand.getZ());
            double dv = bp.getY() - hand.getY();
            if (dh > 1.3 || Math.abs(dv) > 1.4) {
                continue;
            }
            double away = part.side == TeamSide.A ? 1.0 : -1.0;
            Vector v = ball.vel.clone().multiply(s.blockReflect);
            double axisV = arena.alongX() ? v.getX() : v.getZ();
            double newAxis = away * Math.max(0.35, Math.abs(axisV));
            if (arena.alongX()) {
                v.setX(newAxis);
            } else {
                v.setZ(newAxis);
            }
            v.setY(-Math.max(0.5, Math.abs(v.getY())));
            ball.vel.copy(v);
            ball.lastTeam = part.side;
            ball.lastPlayer = null;
            ball.touches = 0;
            part.blockUntil = 0;
            part.hitCooldownUntil = tick + 6;
            Location l = ball.location();
            arena.world().playSound(l, Sound.BLOCK_ANVIL_LAND, 0.6f, 1.6f);
            arena.world().spawnParticle(Particle.CRIT, l, 15, 0.2, 0.2, 0.2, 0.2);
            Msg.bar(pl, "<gold><bold>BLOCK !</bold></gold>");
            return;
        }
    }

    public void onMove(Player p, Location from, Location to) {
        Participant part = parts.get(p.getUniqueId());
        if (part == null) {
            return;
        }
        Settings s = s();

        if (s.trailsEnabled && !part.bench) {
            String trailId = plugin.playerData().trail(part.id);
            if (trailId != null && from.distanceSquared(to) > 0.002) {
                Settings.TrailDef td = s.trails.get(trailId);
                if (td != null) {
                    from.getWorld().spawnParticle(td.particle(), from.getX(), from.getY() + 0.15, from.getZ(),
                            2, 0.12, 0.05, 0.12, 0.0);
                }
            }
        }

        if ((state == State.RALLY || state == State.SERVING) && !part.bench) {
            boolean onGround = p.isOnGround();
            boolean jumped = part.wasOnGround && !onGround && to.getY() > from.getY() + 0.1;
            part.wasOnGround = onGround;
            if (jumped && p.isSneaking() && tick >= part.blockUntil) {
                double dNet = Math.abs(arena.axisOf(to.getX(), to.getZ()) - arena.netCoord());
                if (dNet <= s.blockNetDistance) {
                    part.blockUntil = tick + s.blockWindowTicks;
                    Msg.bar(p, "<gold>Block armé !</gold>");
                }
            }
        }
    }

    /** Dash (touche F). Retourne true si le joueur est dans la partie (le swap de mains doit être annulé). */
    public boolean onDash(Player p) {
        Participant part = parts.get(p.getUniqueId());
        if (part == null) {
            return false;
        }
        Settings s = s();
        if (!s.dashEnabled || part.bench || (state != State.RALLY && state != State.SERVING)) {
            return true;
        }
        if (tick < part.dashReadyAt) {
            Msg.bar(p, "<red>Dash disponible dans " + String.format("%.1f", (part.dashReadyAt - tick) / 20.0) + " s</red>");
            return true;
        }
        Vector dir = horizontalLook(p);
        if (dir.lengthSquared() < 1.0E-6) {
            return true;
        }
        boolean slide = p.isSneaking();
        Vector v = dir.multiply(slide ? 1.15 : 0.9);
        v.setY(slide ? 0.08 : 0.28);
        p.setVelocity(v);
        part.dashReadyAt = tick + (long) (s.dashCooldown * 20);
        Location l = p.getLocation();
        l.getWorld().spawnParticle(Particle.CLOUD, l, 8, 0.2, 0.1, 0.2, 0.02);
        l.getWorld().playSound(l, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, slide ? 0.7f : 1.2f);
        Msg.bar(p, slide ? "<aqua>Glissade !</aqua>" : "<aqua>Dash !</aqua>");
        return true;
    }

    // ------------------------------------------------------------------ points

    private void resolveGround(boolean forcedOut) {
        Settings s = s();
        Vector p = ball.pos();
        boolean in = !forcedOut && arena.inCourt(p.getX(), p.getZ());
        TeamSide landing = arena.sideOf(p.getX(), p.getZ());
        TeamSide winner;
        String reason;
        if (in || !s.outOfBounds) {
            winner = landing.other();
            reason = "ballon au sol dans le camp " + landing.colored();
        } else {
            TeamSide last = ball.lastTeam != null ? ball.lastTeam : serving;
            winner = last.other();
            reason = "ballon sorti, faute de " + last.colored();
        }
        Location l = ball.location();
        arena.world().spawnParticle(Particle.CLOUD, l, 12, 0.25, 0.05, 0.25, 0.03);
        arena.world().playSound(l, Sound.BLOCK_NOTE_BLOCK_BASEDRUM, 1.0f, 0.8f);
        awardPoint(winner, reason);
    }

    private void awardPoint(TeamSide winner, String reason) {
        if (state != State.RALLY && state != State.SERVING) {
            return;
        }
        Settings s = s();
        boolean scoring = !practice();
        if (scoring) {
            if (winner == TeamSide.A) {
                scoreA++;
            } else {
                scoreB++;
            }
        }

        if (ball != null) {
            if (s.autoResetBall) {
                removeBall();
            } else {
                ball.vel.zero();
            }
        }
        if (s.replayEnabled) {
            lastReplay = recorder.snapshot(currentHead, arena.worldName());
        }

        if (scoring) {
            broadcast(winner.colored() + " <white>marque !</white> <gray>(" + reason + ")</gray> "
                    + "<red>" + scoreA + "</red><gray> - </gray><blue>" + scoreB + "</blue>");
            Title title = Title.title(Msg.c(winner.colored() + " marque !"),
                    Msg.c("<red>" + scoreA + "</red><gray> - </gray><blue>" + scoreB + "</blue>"),
                    Title.Times.times(Duration.ofMillis(100), Duration.ofMillis(1200), Duration.ofMillis(300)));
            for (Participant part : parts.values()) {
                Player pl = Bukkit.getPlayer(part.id);
                if (pl != null) {
                    pl.showTitle(title);
                    pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, part.side == winner ? 1.4f : 0.8f);
                }
            }
        } else {
            broadcast("<gray>Ballon au sol (" + reason + "). Entraînement : pas de point, on relance.</gray>");
        }
        if (s.replayEnabled && lastReplay != null) {
            for (Participant part : parts.values()) {
                Player pl = Bukkit.getPlayer(part.id);
                if (pl != null) {
                    Msg.raw(pl, "<click:run_command:'/jvb replay " + arena.name() + "'>"
                            + "<hover:show_text:'Revoir le dernier point'><aqua>[▶ Revoir le point]</aqua></hover></click>");
                }
            }
        }

        if (scoring && winner != serving) {
            serving = winner;
            rotation[winner.ordinal()]++;
        }

        if (scoring && ranked() && hasWon()) {
            endMatch(winner);
            return;
        }
        state = State.POINT_PAUSE;
        stateUntil = tick + s.pauseTicks;
    }

    private boolean hasWon() {
        Settings s = s();
        int hi = Math.max(scoreA, scoreB);
        int diff = Math.abs(scoreA - scoreB);
        return hi >= s.rankedPoints && diff >= s.rankedMargin;
    }

    private void endMatch(TeamSide winner) {
        state = State.ENDED;
        stateUntil = tick + 160;
        removeBall();

        int winPoints = winner == TeamSide.A ? scoreA : scoreB;
        int losePoints = winner == TeamSide.A ? scoreB : scoreA;
        broadcast("<gold><bold>VICTOIRE</bold></gold> de " + winner.colored() + " ! <red>" + scoreA
                + "</red><gray> - </gray><blue>" + scoreB + "</blue>");

        List<UUID> wIds = new ArrayList<>();
        List<String> wNames = new ArrayList<>();
        List<UUID> lIds = new ArrayList<>();
        List<String> lNames = new ArrayList<>();
        for (Participant part : parts.values()) {
            if (part.bench) {
                continue;
            }
            if (part.side == winner) {
                wIds.add(part.id);
                wNames.add(part.name);
            } else {
                lIds.add(part.id);
                lNames.add(part.name);
            }
        }
        plugin.stats().recordMatch(wIds, wNames, winPoints, lIds, lNames, losePoints);

        Title title = Title.title(Msg.c(winner.colored() + " gagne !"),
                Msg.c("<red>" + scoreA + "</red><gray> - </gray><blue>" + scoreB + "</blue>"),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(3000), Duration.ofMillis(500)));
        for (Participant part : parts.values()) {
            Player pl = Bukkit.getPlayer(part.id);
            if (pl != null) {
                pl.showTitle(title);
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                Msg.send(pl, "Classement mis à jour : <white>/jvb stats</white> · <white>/jvb top</white>");
            }
        }
    }

    private void finish() {
        closing = true;
        for (UUID id : new ArrayList<>(parts.keySet())) {
            leave(id, true);
        }
        closing = false;
        resetGame();
    }

    // ------------------------------------------------------------------ replay

    public String startReplay() {
        if (!s().replayEnabled) {
            return "Les replays sont désactivés.";
        }
        if (lastReplay == null || lastReplay.frames.isEmpty()) {
            return "Aucun point n'a encore été enregistré sur cette arène.";
        }
        if (Bukkit.getWorld(lastReplay.worldName) == null) {
            return "Le monde du replay est introuvable.";
        }
        if (replay != null) {
            replay.stop();
        }
        replay = new ReplayPlayer(plugin, lastReplay);
        return null;
    }

    // ------------------------------------------------------------------ boss bar / messages

    private void ensureBar() {
        if (bar == null) {
            bar = BossBar.bossBar(Msg.c("<yellow>Volley"), 1.0f, BossBar.Color.YELLOW, BossBar.Overlay.PROGRESS);
        }
    }

    private void updateBar() {
        if (bar == null) {
            return;
        }
        Settings s = s();
        String arenaName = Msg.esc(arena.name());
        switch (state) {
            case WAITING -> {
                bar.name(Msg.c("<yellow>" + arenaName + "</yellow> <gray>· en attente de joueurs ("
                        + (activeCount(TeamSide.A) + activeCount(TeamSide.B)) + ")"));
                bar.progress(1.0f);
            }
            case COUNTDOWN -> {
                long remaining = Math.max(0, stateUntil - tick);
                bar.name(Msg.c("<yellow>Début dans " + (remaining / 20 + 1) + " s"));
                bar.progress((float) Math.min(1.0, Math.max(0.0, remaining / (double) totalCountdownTicks)));
            }
            case ENDED -> {
                bar.name(Msg.c("<gold>Match terminé · <red>" + scoreA + "</red> - <blue>" + scoreB + "</blue>"));
                bar.progress(1.0f);
            }
            default -> {
                String mode = ranked() ? "Classé " + s.rankedPoints + " pts" : "Open Gym";
                bar.name(Msg.c("<red>Rouge " + scoreA + "</red> <gray>-</gray> <blue>" + scoreB + " Bleu</blue> <gray>· " + mode));
                if (ranked()) {
                    bar.progress((float) Math.min(1.0, Math.max(scoreA, scoreB) / (double) s.rankedPoints));
                } else {
                    bar.progress(1.0f);
                }
            }
        }
    }

    private void broadcast(String mm) {
        for (Participant part : parts.values()) {
            Player pl = Bukkit.getPlayer(part.id);
            if (pl != null) {
                Msg.send(pl, mm);
            }
        }
    }

    private void broadcastExcept(UUID except, String mm) {
        for (Participant part : parts.values()) {
            if (part.id.equals(except)) {
                continue;
            }
            Player pl = Bukkit.getPlayer(part.id);
            if (pl != null) {
                Msg.send(pl, mm);
            }
        }
    }

    // ------------------------------------------------------------------ arrêt du plugin

    public void shutdown() {
        closing = true;
        for (UUID id : new ArrayList<>(parts.keySet())) {
            leave(id, true);
        }
        closing = false;
        removeBall();
        if (replay != null) {
            replay.stop();
            replay = null;
        }
        resetGame();
    }
}
