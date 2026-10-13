package fr.cleangang.justvolleyball.game;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Mémorise les derniers ticks d'un échange (balle + joueurs) pour pouvoir les rejouer. */
public final class ReplayRecorder {

    public static final class Frame {
        public final Vector ball;
        public final Map<UUID, Vector> players;

        Frame(Vector ball, Map<UUID, Vector> players) {
            this.ball = ball;
            this.players = players;
        }
    }

    public static final class Data {
        public final List<Frame> frames;
        public final ItemStack head;
        public final String worldName;

        Data(List<Frame> frames, ItemStack head, String worldName) {
            this.frames = frames;
            this.head = head;
            this.worldName = worldName;
        }
    }

    private final ArrayDeque<Frame> frames = new ArrayDeque<>();
    private int max = 100;

    public void setMaxFrames(int max) {
        this.max = Math.max(20, max);
        while (frames.size() > this.max) {
            frames.pollFirst();
        }
    }

    public void record(Vector ball, Map<UUID, Vector> players) {
        Map<UUID, Vector> copy = new HashMap<>();
        for (Map.Entry<UUID, Vector> e : players.entrySet()) {
            copy.put(e.getKey(), e.getValue().clone());
        }
        frames.addLast(new Frame(ball.clone(), copy));
        while (frames.size() > max) {
            frames.pollFirst();
        }
    }

    public Data snapshot(ItemStack head, String worldName) {
        if (frames.isEmpty()) {
            return null;
        }
        return new Data(new ArrayList<>(frames), head, worldName);
    }

    public void clear() {
        frames.clear();
    }
}
