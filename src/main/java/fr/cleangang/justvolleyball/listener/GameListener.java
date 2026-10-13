package fr.cleangang.justvolleyball.listener;

import fr.cleangang.justvolleyball.JustVolleyballMC;
import fr.cleangang.justvolleyball.arena.Arena;
import fr.cleangang.justvolleyball.game.Game;
import fr.cleangang.justvolleyball.gui.AdminGui;
import fr.cleangang.justvolleyball.gui.CosmeticsGui;
import fr.cleangang.justvolleyball.gui.MainGui;
import fr.cleangang.justvolleyball.gui.Menu;
import fr.cleangang.justvolleyball.util.HandItem;
import fr.cleangang.justvolleyball.util.Msg;
import io.papermc.paper.event.player.AsyncChatEvent;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

/**
 * Tous les handlers ne réagissent que pour : (1) la faux d'arène d'un admin, (2) un joueur inscrit dans une partie,
 * (3) nos propres entités / objets. Rien n'est annulé globalement, pour ne pas gêner les autres plugins.
 */
public final class GameListener implements Listener {

    private static final String ADMIN = "justvolleyball.admin";

    private final JustVolleyballMC plugin;

    public GameListener(JustVolleyballMC plugin) {
        this.plugin = plugin;
    }

    private Game gameOf(Player p) {
        return plugin.games().gameOf(p.getUniqueId());
    }

    // ------------------------------------------------------------------ faux admin + actions de jeu

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        ItemStack item = e.getItem();

        if (item != null && plugin.wand().isWand(item)) {
            if (!p.hasPermission(ADMIN) || e.getHand() != EquipmentSlot.HAND) {
                return;
            }
            Action a = e.getAction();
            if (a == Action.LEFT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                setPoint(p, 0, e.getClickedBlock().getLocation());
            } else if (a == Action.RIGHT_CLICK_BLOCK && e.getClickedBlock() != null) {
                e.setCancelled(true);
                setPoint(p, 1, e.getClickedBlock().getLocation());
            }
            return;
        }

        Game g = gameOf(p);

        // ballon / outils admin de la hotbar : ouvrent un menu (hors partie, en partie le clic sert au jeu)
        if (item != null && plugin.menuItems().isMenuItem(item)) {
            Action act = e.getAction();
            if (act == Action.PHYSICAL) {
                return;
            }
            e.setCancelled(true);
            if (g == null && e.getHand() == EquipmentSlot.HAND) {
                if (plugin.menuItems().isAdmin(item)) {
                    AdminGui.open(plugin, p);
                } else {
                    MainGui.open(plugin, p);
                }
            } else if (g != null) {
                boolean l = act == Action.LEFT_CLICK_AIR || act == Action.LEFT_CLICK_BLOCK;
                g.onInteract(p, l);
            }
            return;
        }

        if (g == null) {
            return;
        }
        Action a = e.getAction();
        boolean left = a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK;
        boolean right = a == Action.RIGHT_CLICK_AIR || a == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        e.setCancelled(true);
        g.onInteract(p, left);
    }

    private void setPoint(Player p, int index, Location block) {
        plugin.wand().setPoint(p, index, block);
        Location[] sel = plugin.wand().get(p.getUniqueId());
        String msg = "Point " + (index + 1) + " : <white>" + block.getBlockX() + " " + block.getBlockY() + " "
                + block.getBlockZ() + "</white>";
        if (index == 0) {
            msg += " <gray>(niveau du sol : Y=" + block.getBlockY() + ")</gray>";
        }
        if (sel != null && sel[0] != null && sel[1] != null && sel[0].getWorld() != null
                && sel[0].getWorld().equals(sel[1].getWorld())) {
            int dx = Math.abs(sel[0].getBlockX() - sel[1].getBlockX()) + 1;
            int dz = Math.abs(sel[0].getBlockZ() - sel[1].getBlockZ()) + 1;
            msg += " <gray>· zone " + dx + "x" + dz + " · /jvb create <nom> <1v1..6v6></gray>";
        }
        Msg.send(p, msg);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && g.onDash(e.getPlayer())) {
            e.setCancelled(true);
            return;
        }
        // les objets de menu ne passent jamais en main secondaire
        if (plugin.menuItems().isMenuItem(e.getMainHandItem()) || plugin.menuItems().isMenuItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    /** En partie, si les deux mains sont vides le client n'envoie plus de clic droit dans le vide : on remet la main de volley. */
    @EventHandler
    public void onHeld(PlayerItemHeldEvent e) {
        Player p = e.getPlayer();
        if (gameOf(p) == null) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (p.isOnline() && gameOf(p) != null) {
                HandItem.giveIfHandsEmpty(p, plugin.handKey());
            }
        });
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        if (!e.hasChangedPosition()) {
            return;
        }
        Game g = gameOf(e.getPlayer());
        if (g != null) {
            g.onMove(e.getPlayer(), e.getFrom(), e.getTo());
        }
    }

    // ------------------------------------------------------------------ cycle de vie des joueurs

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Game g = plugin.games().gameOf(id);
        if (g != null) {
            g.leave(id, true);
        }
        plugin.wand().clear(id);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // nettoyage après un crash : on retire toute main de volley oubliée
        HandItem.removeAll(e.getPlayer(), plugin.handKey());
        giveMenuItemsLater(e.getPlayer(), 10L);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        giveMenuItemsLater(e.getPlayer(), 2L);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        // les objets de menu ne tombent jamais au sol
        e.getDrops().removeIf(i -> plugin.menuItems().isMenuItem(i));
    }

    /** Léger délai : laisse d'abord passer les plugins de hub qui vident/remplissent l'inventaire à la connexion. */
    private void giveMenuItemsLater(Player p, long delay) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                plugin.menuItems().give(p);
            }
        }, delay);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g != null && !e.getPlayer().getWorld().getName().equals(g.arena().worldName())) {
            g.leave(e.getPlayer().getUniqueId(), false);
            Msg.send(e.getPlayer(), "Tu as quitté la partie de volley (changement de monde).");
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Game g = gameOf(e.getPlayer());
        if (g == null || e.getTo() == null) {
            return;
        }
        Location center = g.arena().center();
        Location lobby = g.arena().lobby();
        Location to = e.getTo();
        if (to.getWorld() != null && center.getWorld() != null && lobby.getWorld() != null
                && (!to.getWorld().equals(center.getWorld())
                || (to.distanceSquared(center) > 100 * 100 && to.distanceSquared(lobby) > 100 * 100))) {
            g.leave(e.getPlayer().getUniqueId(), false);
            Msg.send(e.getPlayer(), "Tu as quitté la partie de volley (téléportation).");
        }
    }

    // ------------------------------------------------------------------ protections ciblées

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        Entity ent = e.getEntity();
        if (plugin.isOurEntity(ent)) {
            e.setCancelled(true);
        } else if (ent instanceof Player p && gameOf(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFood(FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && gameOf(p) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (plugin.isOurEntity(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onManipulate(PlayerArmorStandManipulateEvent e) {
        if (plugin.isOurEntity(e.getRightClicked())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (plugin.wand().isWand(p.getInventory().getItemInMainHand()) && p.hasPermission(ADMIN)) {
            e.setCancelled(true);
            return;
        }
        if (protectedBlock(p, e.getBlock().getWorld().getName(), e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (protectedBlock(p, e.getBlock().getWorld().getName(), e.getBlock().getX(), e.getBlock().getY(), e.getBlock().getZ())) {
            e.setCancelled(true);
        }
    }

    /** Les terrains sont protégés, sauf pour un admin en créatif. */
    private boolean protectedBlock(Player p, String world, int x, int y, int z) {
        if (p.getGameMode() == GameMode.CREATIVE && p.hasPermission(ADMIN)) {
            return false;
        }
        int ceiling = plugin.settings().ceilingHeight;
        for (Arena a : plugin.arenas().all()) {
            if (a.containsBlock(world, x, y, z, ceiling)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity ent : e.getEntities()) {
            if (plugin.isOurEntity(ent)) {
                ent.remove();
            }
        }
    }

    // ------------------------------------------------------------------ chat open gym

    @EventHandler
    public void onChat(AsyncChatEvent e) {
        Player sender = e.getPlayer();
        Game g = gameOf(sender);
        if (g == null || g.arena().mode() != Arena.Mode.OPEN) {
            return;
        }
        double radius = plugin.settings().chatRadius;
        if (radius <= 0) {
            return;
        }
        Location from = sender.getLocation();
        double r2 = radius * radius;
        e.viewers().removeIf(a -> {
            if (a instanceof Player v) {
                if (v.getUniqueId().equals(sender.getUniqueId())) {
                    return false;
                }
                return !v.getWorld().equals(from.getWorld()) || v.getLocation().distanceSquared(from) > r2;
            }
            return false;
        });
    }

    // ------------------------------------------------------------------ inventaires

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Inventory top = e.getInventory();
        if (top.getHolder() instanceof Menu menu) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
                menu.handle(p, e);
            }
            return;
        }
        if (top.getHolder() instanceof CosmeticsGui.Holder holder) {
            e.setCancelled(true);
            if (e.getClickedInventory() != null && e.getClickedInventory().equals(top)) {
                CosmeticsGui.click(plugin, p, holder, e.getRawSlot());
            }
            return;
        }
        if (lockedItem(e.getCurrentItem()) || lockedItem(e.getCursor())
                || (e.getClick() == ClickType.SWAP_OFFHAND && lockedItem(p.getInventory().getItemInOffHand()))
                || (e.getClick() == ClickType.NUMBER_KEY && e.getHotbarButton() >= 0
                && lockedItem(p.getInventory().getItem(e.getHotbarButton())))) {
            e.setCancelled(true);
        }
    }

    /** Main de volley et objets de menu : jamais déplaçables ni lâchables. */
    private boolean lockedItem(ItemStack item) {
        return HandItem.isMarker(item, plugin.handKey()) || plugin.menuItems().isMenuItem(item);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof Menu
                || e.getInventory().getHolder() instanceof CosmeticsGui.Holder
                || lockedItem(e.getOldCursor())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        if (lockedItem(e.getItemDrop().getItemStack())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlaceMenuItem(BlockPlaceEvent e) {
        if (lockedItem(e.getItemInHand())) {
            e.setCancelled(true);
        }
    }
}
