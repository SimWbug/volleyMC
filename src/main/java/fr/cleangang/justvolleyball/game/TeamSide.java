package fr.cleangang.justvolleyball.game;

/** Les deux camps d'un terrain. A = côté des coordonnées basses, B = côté des coordonnées hautes. */
public enum TeamSide {
    A("Rouge", "red"),
    B("Bleu", "blue");

    public final String label;
    public final String color;

    TeamSide(String label, String color) {
        this.label = label;
        this.color = color;
    }

    public TeamSide other() {
        return this == A ? B : A;
    }

    /** Nom du camp avec balises MiniMessage. */
    public String colored() {
        return "<" + color + ">" + label + "</" + color + ">";
    }

    public static TeamSide parse(String text) {
        if (text == null) {
            return null;
        }
        switch (text.toLowerCase()) {
            case "a":
            case "rouge":
            case "red":
                return A;
            case "b":
            case "bleu":
            case "blue":
                return B;
            default:
                return null;
        }
    }
}
