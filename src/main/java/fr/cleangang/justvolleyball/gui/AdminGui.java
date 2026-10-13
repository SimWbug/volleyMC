package fr.cleangang.justvolleyball.gui;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.admin.ArenaService;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.arena.CourtBuilder;
import fr.cleangang.justvolleyball.game.Game;
import fr.cleangang.justvolleyball.util.Items;
import fr.cleangang.justvolleyball.util.MenuItems;
import fr.cleangang.justvolleyball.util.Msg;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Outils admin en menu : faux, création de terrain, gestion de chaque arène. */
public final class AdminGui {

    private AdminGui() {
    }

    private static boolean denied(Player p) {
        if (!p.hasPermission(MenuItems.ADMIN_PERMISSION)) {
            Msg.send(p, "<red>Tu n'as pas la permission.</red>");
            return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ menu admin principal

    public static void open(JustVolleyballMC plugin, Player p) {
        if (denied(p)) {
            return;
        }
        Menu menu = new Menu("<red><bold>Volley · Admin", 54);

        menu.set(0, Items.of(Material.NETHERITE_HOE, "<gold>Faux d'arène",
                "<gray>Clic gauche sur le sol : point 1 (niveau du sol)",
                "<gray>Clic droit sur un bloc : point 2",
                "",
                "<yellow>Clic : recevoir la faux"), (pl, e) -> {
            pl.closeInventory();
            pl.getInventory().addItem(plugin.wand().create());
            Msg.send(pl, "Tu as reçu la <gold>faux d'arène</gold>. Sélectionne 2 coins puis rouvre ce menu "
                    + "pour <white>créer un terrain</white>.");
        });

        Location[] sel = plugin.wand().get(p.getUniqueId());
        boolean ready = sel != null && sel[0] != null && sel[1] != null;
        List<String> createLore = new ArrayList<>();
        if (ready) {
            int dx = Math.abs(sel[0].getBlockX() - sel[1].getBlockX()) + 1;
            int dz = Math.abs(sel[0].getBlockZ() - sel[1].getBlockZ()) + 1;
            createLore.add("<green>Sélection prête : " + dx + "x" + dz + " blocs");
            createLore.add("");
            createLore.add("<yellow>Clic : choisir le format");
        } else {
            createLore.add("<red>Aucune sélection.");
            createLore.add("<gray>Prends la faux et clique 2 coins du terrain.");
        }
        menu.set(1, Items.of(ready ? Material.EMERALD_BLOCK : Material.REDSTONE_BLOCK,
                "<green>Créer un terrain", createLore.toArray(new String[0])), (pl, e) -> {
            Location[] s2 = plugin.wand().get(pl.getUniqueId());
            if (s2 == null || s2[0] == null || s2[1] == null) {
                Msg.send(pl, "<red>Sélectionne d'abord 2 coins avec la faux.</red>");
                return;
            }
            Menu.later(plugin, () -> openCreate(plugin, pl));
        });

        menu.set(2, Items.of(Material.COMPARATOR, "<aqua>Recharger la config",
                "<gray>Relit config.yml (physique, skins, règles...)"), (pl, e) -> {
            plugin.reloadAll();
            Msg.send(pl, "Configuration rechargée.");
        });

        menu.decor(4, Items.of(Material.PAPER, "<yellow>Arènes",
                "<gray>Clique une arène ci-dessous pour la gérer."));

        int slot = 9;
        for (Arena a : plugin.arenas().all()) {
            if (slot >= 45) {
                break;
            }
            Game g = plugin.games().existing(a.name());
            int players = g == null ? 0 : g.playerCount();
            menu.set(slot++, Items.of(a.mode() == Arena.Mode.RANKED ? Material.GOLD_BLOCK : Material.LIME_CONCRETE,
                    "<white>" + Msg.esc(a.name()),
                    "<gray>" + a.mode().label() + " · " + a.typeLabel(),
                    "<gray>" + a.length() + "x" + a.width() + " · monde " + Msg.esc(a.worldName()),
                    "<gray>Joueurs : <white>" + players,
                    "",
                    "<yellow>Clic : gérer"), (pl, e) -> Menu.later(plugin, () -> openArena(plugin, pl, a)));
        }

        menu.set(49, Items.of(Material.ARROW, "<gray>Retour au menu joueur"),
                (pl, e) -> Menu.later(plugin, () -> MainGui.open(plugin, pl)));

        menu.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ choix du format à la création

    public static void openCreate(JustVolleyballMC plugin, Player p) {
        if (denied(p)) {
            return;
        }
        Menu menu = new Menu("<green><bold>Créer un terrain", 27);
        Material[] icons = {Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD,
                Material.DIAMOND_SWORD, Material.NETHERITE_SWORD};
        for (int size = 1; size <= 6; size++) {
            final int s = size;
            menu.set(9 + size, Items.of(icons[size - 1], "<white>" + size + "v" + size,
                    "<gray>Taille minimale : " + ArenaService.minLength(size) + "x" + ArenaService.minWidth(size),
                    "",
                    "<yellow>Clic gauche : <gold>classé",
                    "<yellow>Clic droit : <green>open gym"), (pl, e) -> {
                Arena.Mode mode = e.getClick().isRightClick() ? Arena.Mode.OPEN : Arena.Mode.RANKED;
                pl.closeInventory();
                String error = ArenaService.create(plugin, pl, ArenaService.autoName(plugin), s, mode);
                if (error != null) {
                    Msg.send(pl, "<red>" + Msg.esc(error) + "</red>");
                }
            });
        }
        menu.set(22, Items.of(Material.ARROW, "<gray>Retour"),
                (pl, e) -> Menu.later(plugin, () -> open(plugin, pl)));
        menu.fillEmpty().open(p);
    }

    // ------------------------------------------------------------------ gestion d'une arène

    private static boolean busy(JustVolleyballMC plugin, Player p, Arena a) {
        Game g = plugin.games().existing(a.name());
        if (g != null && g.playerCount() > 0) {
            Msg.send(p, "<red>Des joueurs sont dans cette arène :</red> arrête d'abord la partie.");
            return true;
        }
        return false;
    }

    public static void openArena(JustVolleyballMC plugin, Player p, Arena a) {
        if (denied(p)) {
            return;
        }
        if (!plugin.arenas().exists(a.name())) {
            Msg.send(p, "<red>Cette arène n'existe plus.</red>");
            return;
        }
        Menu menu = new Menu("<red>Arène · " + Msg.esc(a.name()), 27);
        Game g = plugin.games().existing(a.name());
        menu.decor(4, Items.of(Material.PAPER, "<white>" + Msg.esc(a.name()),
                "<gray>" + a.mode().label() + " · " + a.typeLabel(),
                "<gray>" + a.length() + "x" + a.width() + " · sol Y=" + a.floorY(),
                "<gray>Monde : " + Msg.esc(a.worldName()),
                "<gray>Joueurs : <white>" + (g == null ? 0 : g.playerCount())));

        menu.set(9, Items.of(Material.ENDER_PEARL, "<aqua>Se téléporter", "<gray>Au centre du terrain"), (pl, e) -> {
            if (a.world() == null) {
                Msg.send(pl, "<red>Monde introuvable.</red>");
                return;
            }
            pl.closeInventory();
            pl.teleport(a.center().add(0, 1, 0));
        });

        menu.set(10, Items.of(Material.RED_BED, "<yellow>Définir le lobby ici",
                "<gray>Là où tu te trouves (avec ton regard)"), (pl, e) -> {
            if (!pl.getWorld().getName().equals(a.worldName())) {
                Msg.send(pl, "<red>Le lobby doit être dans le même monde que l'arène.</red>");
                return;
            }
            a.setLobby(pl.getLocation());
            plugin.arenas().save();
            Msg.send(pl, "Lobby de <white>" + Msg.esc(a.name()) + "</white> défini ici.");
        });

        menu.set(11, Items.of(Material.BRICKS, "<gold>Reconstruire le terrain",
                "<gray>Régénère sol, lignes et filet"), (pl, e) -> {
            if (busy(plugin, pl, a)) {
                return;
            }
            pl.closeInventory();
            Msg.send(pl, "Reconstruction du terrain <white>" + Msg.esc(a.name()) + "</white>...");
            CourtBuilder.build(plugin, a, () -> Msg.send(pl, "<green>Terrain reconstruit.</green>"));
        });

        menu.set(12, Items.of(Material.COMPARATOR, "<light_purple>Mode : " + a.mode().label(),
                "<gray>Clic : basculer classé / open gym"), (pl, e) -> {
            if (busy(plugin, pl, a)) {
                return;
            }
            a.setMode(a.mode() == Arena.Mode.RANKED ? Arena.Mode.OPEN : Arena.Mode.RANKED);
            plugin.arenas().save();
            Msg.send(pl, "Mode de <white>" + Msg.esc(a.name()) + "</white> : " + a.mode().label());
            Menu.later(plugin, () -> openArena(plugin, pl, a));
        });

        menu.set(13, Items.of(Material.IRON_SWORD, "<light_purple>Format : " + a.typeLabel(),
                "<gray>Clic : format suivant (1v1 → 6v6)",
                "<gray>Limité par la taille du terrain"), (pl, e) -> {
            if (busy(plugin, pl, a)) {
                return;
            }
            int next = a.teamSize();
            boolean found = false;
            for (int i = 0; i < 6; i++) {
                next = next % 6 + 1;
                if (a.width() >= ArenaService.minWidth(next) && a.length() >= ArenaService.minLength(next)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                Msg.send(pl, "<red>Le terrain ne peut accueillir aucun autre format.</red>");
                return;
            }
            a.setTeamSize(next);
            plugin.arenas().save();
            Msg.send(pl, "Format de <white>" + Msg.esc(a.name()) + "</white> : " + a.typeLabel());
            Menu.later(plugin, () -> openArena(plugin, pl, a));
        });

        menu.set(14, Items.of(Material.LIME_DYE, "<green>Forcer le lancement",
                "<gray>Démarre la partie dans 3 s, même incomplète"), (pl, e) -> {
            Game game = plugin.games().existing(a.name());
            if (game == null || game.playerCount() == 0) {
                Msg.send(pl, "<red>Personne n'est dans cette arène.</red>");
                return;
            }
            game.forceStart();
            Msg.send(pl, "Démarrage forcé.");
        });

        menu.set(15, Items.of(Material.RED_DYE, "<red>Arrêter la partie",
                "<gray>Renvoie tous les joueurs"), (pl, e) -> {
            Game game = plugin.games().existing(a.name());
            if (game == null || game.playerCount() == 0) {
                Msg.send(pl, "<red>Personne n'est dans cette arène.</red>");
                return;
            }
            game.forceStop();
            Msg.send(pl, "Partie arrêtée.");
        });

        menu.set(16, Items.of(Material.TNT, "<dark_red>Supprimer l'arène",
                "<gray>Les blocs du terrain restent en place.",
                "<gray>Shift + clic gauche : supprimer et retirer le filet",
                "<gray>Shift + clic droit : supprimer seulement"), (pl, e) -> {
            boolean left = e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_LEFT;
            boolean right = e.getClick() == org.bukkit.event.inventory.ClickType.SHIFT_RIGHT;
            if (!left && !right) {
                Msg.send(pl, "<red>Pour confirmer, fais Shift + clic sur la TNT.</red>");
                return;
            }
            pl.closeInventory();
            plugin.games().removeArena(a.name());
            plugin.arenas().remove(a.name());
            if (left) {
                CourtBuilder.removeNet(a, plugin.settings());
            }
            Msg.send(pl, "Arène <white>" + Msg.esc(a.name()) + "</white> supprimée" + (left ? " (filet retiré)." : "."));
        });

        menu.set(22, Items.of(Material.ARROW, "<gray>Retour"),
                (pl, e) -> Menu.later(plugin, () -> open(plugin, pl)));
        menu.fillEmpty().open(p);
    }
}
