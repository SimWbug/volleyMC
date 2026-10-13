package fr.cleangang.justvolleyball.gui;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.data.StatsManager;
import fr.cleangang.justvolleyball.game.Game;
import fr.cleangang.justvolleyball.game.TeamSide;
import fr.cleangang.justvolleyball.util.Items;
import fr.cleangang.justvolleyball.util.Msg;
import fr.cleangang.justvolleyball.util.MenuItems;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.ArrayList;
import java.util.List;

/** Menu principal ouvert par le ballon de la hotbar (ou /jvb menu). */
public final class MainGui {

    private MainGui() {
    }

    public static void open(JustVolleyballMC plugin, Player p) {
        Menu menu = new Menu("<gold><bold>Volleyball", 54);
        Game current = plugin.games().gameOf(p.getUniqueId());

        int slot = 0;
        for (Arena a : plugin.arenas().all()) {
            if (slot >= 36) {
                break;
            }
            Game g = plugin.games().existing(a.name());
            int players = g == null ? 0 : g.playerCount();
            String state = g == null ? "En attente" : stateLabel(g.state());
            boolean ranked = a.mode() == Arena.Mode.RANKED;

            List<String> lore = new ArrayList<>();
            lore.add("<gray>Mode : <white>" + a.mode().label());
            lore.add("<gray>Format : <white>" + a.typeLabel());
            lore.add("<gray>Joueurs : <white>" + players);
            lore.add("<gray>État : <white>" + state);
            if (g != null && players > 0 && g.state() != Game.State.WAITING && g.state() != Game.State.COUNTDOWN) {
                lore.add("<gray>Score : <red>" + g.scoreA() + "</red> - <blue>" + g.scoreB() + "</blue>");
            }
            lore.add("");
            if (current != null) {
                lore.add("<red>Tu es déjà dans une partie.");
            } else {
                lore.add("<yellow>Clic : rejoindre (camp auto)");
                lore.add("<yellow>Shift + clic gauche : camp rouge");
                lore.add("<yellow>Shift + clic droit : camp bleu");
            }
            String name = (ranked ? "<gold>" : "<green>") + Msg.esc(a.name());
            menu.set(slot++, Items.of(ranked ? Material.GOLD_BLOCK : Material.LIME_CONCRETE, name,
                    lore.toArray(new String[0])), (pl, e) -> {
                if (plugin.games().gameOf(pl.getUniqueId()) != null) {
                    Msg.send(pl, "<red>Tu es déjà dans une partie.</red>");
                    return;
                }
                if (!pl.hasPermission("justvolleyball.play")) {
                    Msg.send(pl, "<red>Tu n'as pas la permission de jouer.</red>");
                    return;
                }
                TeamSide side = null;
                if (e.getClick() == ClickType.SHIFT_LEFT) {
                    side = TeamSide.A;
                } else if (e.getClick() == ClickType.SHIFT_RIGHT) {
                    side = TeamSide.B;
                }
                final TeamSide chosen = side;
                pl.closeInventory();
                String error = plugin.games().forArena(a).join(pl, chosen);
                if (error != null) {
                    Msg.send(pl, "<red>" + Msg.esc(error) + "</red>");
                }
            });
        }
        if (plugin.arenas().all().isEmpty()) {
            menu.decor(22, Items.of(Material.BARRIER, "<red>Aucune arène",
                    "<gray>Un admin doit d'abord créer un terrain."));
        }

        // ---- barre du bas
        StatsManager.Entry me = plugin.stats().get(p.getUniqueId(), p.getName());
        menu.decor(45, Items.of(Material.BOOK, "<yellow>Mes stats",
                "<gray>Classement : <gold>" + me.elo() + " Elo",
                "<gray>Victoires : <green>" + me.wins(),
                "<gray>Défaites : <red>" + me.losses(),
                "<gray>Points marqués : <white>" + me.points()));

        List<String> topLore = new ArrayList<>();
        int rank = 1;
        for (StatsManager.Entry e : plugin.stats().top(5)) {
            topLore.add("<yellow>" + rank++ + ".</yellow> <white>" + Msg.esc(e.name()) + "</white> <gray>- " + e.elo() + " Elo");
        }
        if (topLore.isEmpty()) {
            topLore.add("<gray>Aucun match classé joué.");
        }
        menu.decor(46, Items.of(Material.GOLD_INGOT, "<gold>Top classé", topLore.toArray(new String[0])));

        menu.set(47, Items.of(Material.FIREWORK_STAR, "<light_purple>Cosmétiques",
                "<gray>Skins de balle et traînées"), (pl, e) -> {
            if (!pl.hasPermission("justvolleyball.cosmetics")) {
                Msg.send(pl, "<red>Tu n'as pas la permission.</red>");
                return;
            }
            Menu.later(plugin, () -> CosmeticsGui.open(plugin, pl));
        });

        if (current != null) {
            menu.set(48, Items.of(Material.CLOCK, "<aqua>Revoir le dernier point",
                    "<gray>Rejoue l'échange sur ton arène"), (pl, e) -> {
                Game g = plugin.games().gameOf(pl.getUniqueId());
                if (g == null) {
                    return;
                }
                pl.closeInventory();
                String error = g.startReplay();
                Msg.send(pl, error == null ? "<aqua>Replay du dernier point lancé !</aqua>"
                        : "<red>" + Msg.esc(error) + "</red>");
            });
            menu.set(49, Items.of(Material.BARRIER, "<red>Quitter la partie",
                    "<gray>Retour à ta position d'avant"), (pl, e) -> {
                Game g = plugin.games().gameOf(pl.getUniqueId());
                pl.closeInventory();
                if (g != null) {
                    g.leave(pl.getUniqueId(), true);
                }
            });
        }

        if (p.hasPermission(MenuItems.ADMIN_PERMISSION)) {
            menu.set(53, Items.of(Material.COMMAND_BLOCK, "<red>Outils admin",
                    "<gray>Faux, création et gestion des arènes"), (pl, e) ->
                    Menu.later(plugin, () -> AdminGui.open(plugin, pl)));
        }

        menu.fillEmpty().open(p);
    }

    static String stateLabel(Game.State state) {
        return switch (state) {
            case WAITING -> "En attente";
            case COUNTDOWN -> "Compte à rebours";
            case SERVING, RALLY, POINT_PAUSE -> "En match";
            case ENDED -> "Terminé";
        };
    }
}
