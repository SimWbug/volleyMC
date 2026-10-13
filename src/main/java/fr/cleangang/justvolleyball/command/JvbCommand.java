package fr.cleangang.justvolleyball.command;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.admin.ArenaService;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.arena.CourtBuilder;
import fr.cleangang.justvolleyball.config.Settings;
import fr.cleangang.justvolleyball.data.StatsManager;
import fr.cleangang.justvolleyball.game.Game;
import fr.cleangang.justvolleyball.game.TeamSide;
import fr.cleangang.justvolleyball.gui.AdminGui;
import fr.cleangang.justvolleyball.gui.CosmeticsGui;
import fr.cleangang.justvolleyball.gui.MainGui;
import fr.cleangang.justvolleyball.util.Msg;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/** /jvb : commandes joueurs et outils d'administration. */
public final class JvbCommand implements TabExecutor {

    private static final String ADMIN = "justvolleyball.admin";
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]{2,24}");
    private static final List<String> PLAYER_SUBS = List.of("help", "menu", "join", "leave", "list", "stats", "top", "replay",
            "cosmetics", "skin", "trail");
    private static final List<String> ADMIN_SUBS = List.of("admin", "wand", "create", "build", "delete", "info", "setlobby",
            "setmode", "settype", "forcestart", "forcestop", "tp", "kick", "reload");

    private final JustVolleyballMC plugin;

    public JvbCommand(JustVolleyballMC plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help", "aide" -> help(sender);
            case "menu" -> menu(sender);
            case "join", "rejoindre" -> join(sender, args);
            case "leave", "quitter" -> leave(sender);
            case "list", "liste" -> list(sender);
            case "stats" -> stats(sender, args);
            case "top" -> top(sender);
            case "replay" -> replay(sender, args);
            case "cosmetics", "cosmetiques" -> cosmetics(sender);
            case "skin" -> skin(sender, args);
            case "trail" -> trail(sender, args);
            default -> {
                if (ADMIN_SUBS.contains(sub) || sub.equals("faux") || sub.equals("creer")) {
                    admin(sender, sub, args);
                } else {
                    Msg.send(sender, "<red>Sous-commande inconnue.</red> <gray>/jvb help</gray>");
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ joueurs

    private void help(CommandSender s) {
        Msg.raw(s, "<gradient:#ffb347:#ffe066><bold>JustVolleyballMC</bold></gradient>");
        Msg.raw(s, "<yellow>/jvb menu</yellow> <gray>- menu du volley (ou clic droit sur le ballon de ta hotbar)");
        Msg.raw(s, "<yellow>/jvb join <arène> [rouge|bleu]</yellow> <gray>- rejoindre une partie");
        Msg.raw(s, "<yellow>/jvb leave</yellow> <gray>- quitter la partie");
        Msg.raw(s, "<yellow>/jvb list</yellow> <gray>- liste des arènes");
        Msg.raw(s, "<yellow>/jvb stats [joueur]</yellow> · <yellow>/jvb top</yellow> <gray>- classement classé");
        Msg.raw(s, "<yellow>/jvb replay [arène]</yellow> <gray>- revoir le dernier point");
        Msg.raw(s, "<yellow>/jvb cosmetics</yellow> · <yellow>/jvb skin <id></yellow> · <yellow>/jvb trail <id|off></yellow>");
        Msg.raw(s, "<gray>Jeu : clic droit = passe/service (maintenir = charger) · clic gauche en l'air = smash · "
                + "Shift+saut près du filet = block · F = dash (Shift+F = glissade)</gray>");
        if (s.hasPermission(ADMIN)) {
            Msg.raw(s, "<gold>Admin :</gold> <gray>admin (menu), wand, create, build, delete, info, setlobby, setmode, settype, "
                    + "forcestart, forcestop, tp, kick, reload</gray>");
        }
    }

    private void menu(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Commande réservée aux joueurs.</red>");
            return;
        }
        MainGui.open(plugin, p);
    }

    private void join(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Commande réservée aux joueurs.</red>");
            return;
        }
        if (!p.hasPermission("justvolleyball.play")) {
            Msg.send(p, "<red>Tu n'as pas la permission de jouer.</red>");
            return;
        }
        if (args.length < 2) {
            Msg.send(p, "<red>Usage :</red> /jvb join <arène> [rouge|bleu]");
            return;
        }
        if (plugin.games().gameOf(p.getUniqueId()) != null) {
            Msg.send(p, "<red>Tu es déjà dans une partie.</red> <gray>/jvb leave pour la quitter.</gray>");
            return;
        }
        Arena arena = plugin.arenas().get(args[1]);
        if (arena == null) {
            Msg.send(p, "<red>Arène introuvable.</red> <gray>/jvb list</gray>");
            return;
        }
        TeamSide side = args.length >= 3 ? TeamSide.parse(args[2]) : null;
        String error = plugin.games().forArena(arena).join(p, side);
        if (error != null) {
            Msg.send(p, "<red>" + error + "</red>");
        }
    }

    private void leave(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return;
        }
        Game g = plugin.games().gameOf(p.getUniqueId());
        if (g == null) {
            Msg.send(p, "<red>Tu n'es dans aucune partie.</red>");
            return;
        }
        g.leave(p.getUniqueId(), true);
    }

    private void list(CommandSender sender) {
        if (plugin.arenas().all().isEmpty()) {
            Msg.send(sender, "<gray>Aucune arène pour le moment.</gray>");
            return;
        }
        Msg.send(sender, "Arènes :");
        for (Arena a : plugin.arenas().all()) {
            Game g = plugin.games().existing(a.name());
            int players = g == null ? 0 : g.playerCount();
            String state = g == null ? "WAITING" : g.state().name();
            Msg.raw(sender, " <yellow>" + Msg.esc(a.name()) + "</yellow> <gray>· " + a.mode().label() + " · "
                    + a.typeLabel() + " · " + players + " joueur(s) · " + state + "</gray>");
        }
    }

    private void stats(CommandSender sender, String[] args) {
        UUID target;
        String name;
        if (args.length >= 2) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            target = op.getUniqueId();
            name = op.getName() == null ? args[1] : op.getName();
        } else if (sender instanceof Player p) {
            target = p.getUniqueId();
            name = p.getName();
        } else {
            Msg.send(sender, "<red>Usage :</red> /jvb stats <joueur>");
            return;
        }
        StatsManager.Entry e = plugin.stats().get(target, name);
        Msg.send(sender, "Stats de <white>" + Msg.esc(name) + "</white> : <gold>" + e.elo() + " Elo</gold> · "
                + "<green>" + e.wins() + " victoires</green> · <red>" + e.losses() + " défaites</red> · "
                + e.points() + " points marqués");
    }

    private void top(CommandSender sender) {
        List<StatsManager.Entry> top = plugin.stats().top(10);
        if (top.isEmpty()) {
            Msg.send(sender, "<gray>Aucun match classé joué pour l'instant.</gray>");
            return;
        }
        Msg.send(sender, "<gold>Top 10 classé</gold>");
        int i = 1;
        for (StatsManager.Entry e : top) {
            Msg.raw(sender, " <yellow>" + i++ + ".</yellow> <white>" + Msg.esc(e.name()) + "</white> <gray>- " + e.elo()
                    + " Elo (" + e.wins() + "V/" + e.losses() + "D)</gray>");
        }
    }

    private void replay(CommandSender sender, String[] args) {
        Game g = null;
        if (args.length >= 2) {
            Arena a = plugin.arenas().get(args[1]);
            if (a == null) {
                Msg.send(sender, "<red>Arène introuvable.</red>");
                return;
            }
            g = plugin.games().forArena(a);
        } else if (sender instanceof Player p) {
            g = plugin.games().gameOf(p.getUniqueId());
        }
        if (g == null) {
            Msg.send(sender, "<red>Précise une arène :</red> /jvb replay <arène>");
            return;
        }
        String error = g.startReplay();
        if (error != null) {
            Msg.send(sender, "<red>" + error + "</red>");
        } else {
            Msg.send(sender, "<aqua>Replay du dernier point lancé !</aqua>");
        }
    }

    private void cosmetics(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return;
        }
        if (!p.hasPermission("justvolleyball.cosmetics")) {
            Msg.send(p, "<red>Tu n'as pas la permission.</red>");
            return;
        }
        CosmeticsGui.open(plugin, p);
    }

    private void skin(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        Settings s = plugin.settings();
        if (!s.skinsEnabled || !p.hasPermission("justvolleyball.cosmetics")) {
            Msg.send(p, "<red>Les skins de balle sont indisponibles.</red>");
            return;
        }
        if (args.length < 2 || !s.skins.containsKey(args[1].toLowerCase(Locale.ROOT))) {
            Msg.send(p, "<red>Skins disponibles :</red> " + String.join(", ", s.skins.keySet()));
            return;
        }
        plugin.playerData().setSkin(p.getUniqueId(), args[1].toLowerCase(Locale.ROOT));
        Msg.send(p, "Skin de balle : <white>" + Msg.esc(args[1].toLowerCase(Locale.ROOT)) + "</white>");
    }

    private void trail(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        Settings s = plugin.settings();
        if (!s.trailsEnabled || !p.hasPermission("justvolleyball.cosmetics")) {
            Msg.send(p, "<red>Les traînées sont indisponibles.</red>");
            return;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("off")) {
            plugin.playerData().setTrail(p.getUniqueId(), null);
            Msg.send(p, "Traînée désactivée.");
            return;
        }
        if (args.length < 2 || !s.trails.containsKey(args[1].toLowerCase(Locale.ROOT))) {
            Msg.send(p, "<red>Traînées disponibles :</red> off, " + String.join(", ", s.trails.keySet()));
            return;
        }
        plugin.playerData().setTrail(p.getUniqueId(), args[1].toLowerCase(Locale.ROOT));
        Msg.send(p, "Traînée : <white>" + Msg.esc(args[1].toLowerCase(Locale.ROOT)) + "</white>");
    }

    // ------------------------------------------------------------------ admin

    private void admin(CommandSender sender, String sub, String[] args) {
        if (!sender.hasPermission(ADMIN)) {
            Msg.send(sender, "<red>Tu n'as pas la permission.</red>");
            return;
        }
        switch (sub) {
            case "admin" -> {
                if (sender instanceof Player ap) {
                    AdminGui.open(plugin, ap);
                }
            }
            case "wand", "faux" -> wand(sender);
            case "create", "creer" -> create(sender, args);
            case "build" -> build(sender, args);
            case "delete" -> delete(sender, args);
            case "info" -> info(sender, args);
            case "setlobby" -> setLobby(sender, args);
            case "setmode" -> setMode(sender, args);
            case "settype" -> setType(sender, args);
            case "forcestart" -> force(sender, args, true);
            case "forcestop" -> force(sender, args, false);
            case "tp" -> tp(sender, args);
            case "kick" -> kick(sender, args);
            case "reload" -> {
                plugin.reloadAll();
                Msg.send(sender, "Configuration rechargée.");
            }
            default -> Msg.send(sender, "<red>Sous-commande inconnue.</red>");
        }
    }

    private Arena arenaArg(CommandSender sender, String[] args, String usage) {
        if (args.length < 2) {
            Msg.send(sender, "<red>Usage :</red> " + usage);
            return null;
        }
        Arena a = plugin.arenas().get(args[1]);
        if (a == null) {
            Msg.send(sender, "<red>Arène introuvable.</red> <gray>/jvb list</gray>");
        }
        return a;
    }

    private boolean busy(CommandSender sender, Arena a) {
        Game g = plugin.games().existing(a.name());
        if (g != null && g.playerCount() > 0) {
            Msg.send(sender, "<red>Des joueurs sont dans cette arène.</red> <gray>/jvb forcestop " + Msg.esc(a.name()) + "</gray>");
            return true;
        }
        return false;
    }

    private Integer parseType(String text) {
        if (text == null) {
            return null;
        }
        String t = text.toLowerCase(Locale.ROOT);
        for (int n : new int[]{1, 2, 3, 4, 5, 6}) {
            if (t.equals(n + "v" + n)) {
                return n;
            }
        }
        return null;
    }

    private void wand(CommandSender sender) {
        if (!(sender instanceof Player p)) {
            return;
        }
        p.getInventory().addItem(plugin.wand().create());
        Msg.send(p, "Tu as reçu la <gold>faux d'arène</gold>. <gray>Clic gauche sur le sol = point 1 (niveau du sol), "
                + "clic droit = point 2, puis /jvb create <nom> <1v1..6v6> [ranked|open].</gray>");
    }

    private void create(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            Msg.send(sender, "<red>Commande réservée aux joueurs.</red>");
            return;
        }
        if (args.length < 3) {
            Msg.send(p, "<red>Usage :</red> /jvb create <nom> <1v1|2v2|3v3|4v4|5v5|6v6> [ranked|open]");
            return;
        }
        Integer size = ArenaService.parseType(args[2]);
        if (size == null) {
            Msg.send(p, "<red>Type invalide.</red> <gray>1v1, 2v2, 3v3, 4v4, 5v5 ou 6v6</gray>");
            return;
        }
        Arena.Mode mode = args.length >= 4 ? Arena.Mode.parse(args[3]) : Arena.Mode.RANKED;
        if (mode == null) {
            Msg.send(p, "<red>Mode invalide.</red> <gray>ranked ou open</gray>");
            return;
        }
        String error = ArenaService.create(plugin, p, args[1], size, mode);
        if (error != null) {
            Msg.send(p, "<red>" + Msg.esc(error) + "</red>");
        }
    }

    private void build(CommandSender sender, String[] args) {
        Arena a = arenaArg(sender, args, "/jvb build <arène>");
        if (a == null || busy(sender, a)) {
            return;
        }
        Msg.send(sender, "Reconstruction du terrain <white>" + Msg.esc(a.name()) + "</white>...");
        CourtBuilder.build(plugin, a, () -> Msg.send(sender, "<green>Terrain reconstruit.</green>"));
    }

    private void delete(CommandSender sender, String[] args) {
        Arena a = arenaArg(sender, args, "/jvb delete <arène> [clear]");
        if (a == null) {
            return;
        }
        boolean clear = args.length >= 3 && args[2].equalsIgnoreCase("clear");
        plugin.games().removeArena(a.name());
        plugin.arenas().remove(a.name());
        if (clear) {
            CourtBuilder.removeNet(a, plugin.settings());
        }
        Msg.send(sender, "Arène <white>" + Msg.esc(a.name()) + "</white> supprimée"
                + (clear ? " (filet retiré)." : ". <gray>Les blocs du terrain restent en place (ajoute 'clear' pour retirer le filet).</gray>"));
    }

    private void info(CommandSender sender, String[] args) {
        Arena a = arenaArg(sender, args, "/jvb info <arène>");
        if (a == null) {
            return;
        }
        Game g = plugin.games().existing(a.name());
        Msg.send(sender, "<white>" + Msg.esc(a.name()) + "</white> · " + a.mode().label() + " · " + a.typeLabel()
                + " · monde " + a.worldName() + " · " + a.length() + "x" + a.width() + " · sol Y=" + a.floorY()
                + " · axe " + (a.alongX() ? "X" : "Z") + " · filet ligne " + a.netRow()
                + (g == null ? "" : " · " + g.state().name() + " · " + g.playerCount() + " joueur(s) · score "
                + g.scoreA() + "-" + g.scoreB()));
    }

    private void setLobby(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        Arena a = arenaArg(sender, args, "/jvb setlobby <arène>");
        if (a == null) {
            return;
        }
        if (!p.getWorld().getName().equals(a.worldName())) {
            Msg.send(p, "<red>Le lobby doit être dans le même monde que l'arène.</red>");
            return;
        }
        a.setLobby(p.getLocation());
        plugin.arenas().save();
        Msg.send(p, "Lobby de <white>" + Msg.esc(a.name()) + "</white> défini ici.");
    }

    private void setMode(CommandSender sender, String[] args) {
        Arena a = arenaArg(sender, args, "/jvb setmode <arène> <ranked|open>");
        if (a == null || busy(sender, a)) {
            return;
        }
        Arena.Mode mode = args.length >= 3 ? Arena.Mode.parse(args[2]) : null;
        if (mode == null) {
            Msg.send(sender, "<red>Usage :</red> /jvb setmode <arène> <ranked|open>");
            return;
        }
        a.setMode(mode);
        plugin.arenas().save();
        Msg.send(sender, "Mode de <white>" + Msg.esc(a.name()) + "</white> : " + mode.label());
    }

    private void setType(CommandSender sender, String[] args) {
        Arena a = arenaArg(sender, args, "/jvb settype <arène> <1v1..6v6>");
        if (a == null || busy(sender, a)) {
            return;
        }
        Integer size = args.length >= 3 ? parseType(args[2]) : null;
        if (size == null) {
            Msg.send(sender, "<red>Usage :</red> /jvb settype <arène> <1v1..6v6>");
            return;
        }
        int minWidth = Math.max(5, size + 3);
        int minLength = Math.max(9, 2 * (size + 3) + 1);
        if (a.width() < minWidth || a.length() < minLength) {
            Msg.send(sender, "<red>Le terrain est trop petit pour du " + size + "v" + size + "</red> (minimum "
                    + minLength + "x" + minWidth + ").");
            return;
        }
        a.setTeamSize(size);
        plugin.arenas().save();
        Msg.send(sender, "Type de <white>" + Msg.esc(a.name()) + "</white> : " + a.typeLabel());
    }

    private void force(CommandSender sender, String[] args, boolean start) {
        Arena a = arenaArg(sender, args, "/jvb " + (start ? "forcestart" : "forcestop") + " <arène>");
        if (a == null) {
            return;
        }
        Game g = plugin.games().existing(a.name());
        if (g == null || g.playerCount() == 0) {
            Msg.send(sender, "<red>Personne n'est dans cette arène.</red>");
            return;
        }
        if (start) {
            g.forceStart();
            Msg.send(sender, "Démarrage forcé.");
        } else {
            g.forceStop();
            Msg.send(sender, "Partie arrêtée.");
        }
    }

    private void tp(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            return;
        }
        Arena a = arenaArg(sender, args, "/jvb tp <arène>");
        if (a == null) {
            return;
        }
        if (a.world() == null) {
            Msg.send(p, "<red>Monde introuvable.</red>");
            return;
        }
        p.teleport(a.center().add(0, 1, 0));
    }

    private void kick(CommandSender sender, String[] args) {
        if (args.length < 2) {
            Msg.send(sender, "<red>Usage :</red> /jvb kick <joueur>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        Game g = target == null ? null : plugin.games().gameOf(target.getUniqueId());
        if (g == null) {
            Msg.send(sender, "<red>Ce joueur n'est dans aucune partie.</red>");
            return;
        }
        g.leave(target.getUniqueId(), true);
        Msg.send(target, "<red>Tu as été retiré de la partie par un admin.</red>");
        Msg.send(sender, "Joueur retiré.");
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (sender.hasPermission(ADMIN)) {
                out.addAll(ADMIN_SUBS);
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            switch (sub) {
                case "join", "replay", "build", "delete", "info", "setlobby", "setmode", "settype", "forcestart",
                        "forcestop", "tp" -> out.addAll(plugin.arenas().names());
                case "skin" -> out.addAll(plugin.settings().skins.keySet());
                case "trail" -> {
                    out.add("off");
                    out.addAll(plugin.settings().trails.keySet());
                }
                case "stats", "kick" -> {
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        out.add(p.getName());
                    }
                }
                default -> {
                }
            }
            return filter(out, args[1]);
        }
        if (args.length == 3) {
            switch (sub) {
                case "join" -> out.addAll(Arrays.asList("rouge", "bleu"));
                case "create" -> out.addAll(Arrays.asList("1v1", "2v2", "3v3", "4v4", "5v5", "6v6"));
                case "settype" -> out.addAll(Arrays.asList("1v1", "2v2", "3v3", "4v4", "5v5", "6v6"));
                case "setmode" -> out.addAll(Arrays.asList("ranked", "open"));
                case "delete" -> out.add("clear");
                default -> {
                }
            }
            return filter(out, args[2]);
        }
        if (args.length == 4 && sub.equals("create")) {
            return filter(new ArrayList<>(Arrays.asList("ranked", "open")), args[3]);
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        String p = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String o : options) {
            if (o.toLowerCase(Locale.ROOT).startsWith(p)) {
                out.add(o);
            }
        }
        return out;
    }
}
