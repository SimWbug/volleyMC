package fr.cleangang.justvolleyball.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Petits helpers de messages (MiniMessage). */
public final class Msg {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    public static final String PREFIX = "<gradient:#ffb347:#ffe066><bold>VOLLEY</bold></gradient> <dark_gray>»</dark_gray> ";

    private Msg() {
    }

    public static Component c(String miniMessage) {
        return MM.deserialize(miniMessage);
    }

    public static void send(CommandSender to, String miniMessage) {
        to.sendMessage(c(PREFIX + miniMessage));
    }

    public static void raw(CommandSender to, String miniMessage) {
        to.sendMessage(c(miniMessage));
    }

    public static void bar(Player to, String miniMessage) {
        to.sendActionBar(c(miniMessage));
    }

    public static String esc(String text) {
        return MM.escapeTags(text);
    }
}
