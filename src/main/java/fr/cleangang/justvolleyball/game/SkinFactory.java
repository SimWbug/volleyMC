package fr.cleangang.justvolleyball.game;

import fr.cleangang.justvolleyball.config.Settings;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fabrique l'objet porté sur la tête de l'armor stand : tête à texture si fournie, sinon un bloc. */
public final class SkinFactory {

    private static final Pattern URL_PATTERN = Pattern.compile("\"url\"\\s*:\\s*\"([^\"]+)\"");

    private SkinFactory() {
    }

    public static ItemStack head(Settings.BallSkin skin) {
        String texture = skin.texture();
        if (texture != null && !texture.isBlank()) {
            try {
                String json = new String(Base64.getDecoder().decode(texture.trim()), StandardCharsets.UTF_8);
                Matcher m = URL_PATTERN.matcher(json);
                if (m.find()) {
                    PlayerProfile profile = Bukkit.createPlayerProfile(
                            UUID.nameUUIDFromBytes(("jvb-" + skin.id()).getBytes(StandardCharsets.UTF_8)), "jvb");
                    PlayerTextures textures = profile.getTextures();
                    textures.setSkin(new URL(m.group(1)));
                    profile.setTextures(textures);
                    ItemStack item = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) item.getItemMeta();
                    meta.setOwnerProfile(profile);
                    item.setItemMeta(meta);
                    return item;
                }
            } catch (Exception ignored) {
                // texture invalide : on retombe sur le matériau
            }
        }
        return new ItemStack(skin.material());
    }
}
