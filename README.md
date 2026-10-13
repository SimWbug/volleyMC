# JustVolleyballMC

Plugin Paper de volleyball (1v1 à 6v6) : physique de balle maison, mode classé (Elo), open gym, replays, skins de balle et traînées.

## Build

```
mvn package
```

Le jar sort dans `target/JustVolleyballMC-1.0.0.jar`. Le workflow GitHub Actions (`.github/workflows/build.yml`) le compile automatiquement.
La version d'API Paper utilisée pour compiler est la propriété `paper.version` du `pom.xml` (par défaut `1.21.4-R0.1-SNAPSHOT`) : adapte-la à ton serveur si besoin.

## Mise en place d'un terrain

1. `/jvb wand` : tu reçois la faux (houe en netherite).
2. Clic gauche sur un bloc du sol = point 1 (définit le niveau du sol), clic droit sur un bloc = point 2. Un contour de particules s'affiche.
3. `/jvb create <nom> <1v1|2v2|3v3|4v4|5v5|6v6> [ranked|open]` : le terrain est généré automatiquement
   (sol rouge/bleu par camp, lignes blanches, lignes d'attaque, filet de 3 blocs avec poteaux, vide dégagé au-dessus).
4. `/jvb setlobby <nom>` pour fixer le lobby, puis `/jvb join <nom>` pour tester.

Tailles minimales : 1v1 = 9x5, 2v2 = 11x5, 3v3 = 13x6, 4v4 = 15x7, 5v5 = 17x8, 6v6 = 19x9 (longueur x largeur). La longueur est automatiquement rendue impaire pour centrer le filet.

## Jeu

| Action | Contrôle |
|---|---|
| Service | Maintenir clic droit (charge), relâcher pour tirer. La visée suit ton regard, trajectoire en particules |
| Passe / bump | Clic droit près de la balle |
| Smash | Clic gauche en l'air (saut de 1,2 bloc minimum) |
| Block | Shift + saut près du filet, puis toucher la balle avec les mains en l'air |
| Dash | Touche F (Shift + F = glissade), recharge 2,5 s |

Règles : 3 touches max par camp, pas de double touche (si l'équipe a plus d'un joueur), balle au sol dans un camp = point pour l'autre, balle dehors = faute du dernier toucheur. Classé : 25 points, 2 d'écart. Open gym : jeu libre jusqu'à 24 joueurs (les surnuméraires sont sur le banc), chat limité à 30 blocs.

## Commandes

Joueurs : `/jvb join|leave|list|stats|top|replay|cosmetics|skin|trail|help`
Admin (`justvolleyball.admin`) : `wand, create, build, delete [clear], info, setlobby, setmode, settype, forcestart, forcestop, tp, kick, reload`

## Compatibilité avec les autres plugins

- Une seule commande `/jvb`, permissions `justvolleyball.*`, données dans des fichiers séparés (`arenas.yml`, `stats.yml`, `players.yml`).
- Aucun scoreboard ni team touché : l'affichage passe par une bossbar. Les collisions entre joueurs sont celles de Minecraft (aucune team modifiée).
- Aucune modification d'inventaire, sauf une « main de volley » en main secondaire quand les deux mains du joueur sont vides (nécessaire pour que le client envoie le clic droit dans le vide). Elle est retirée à la sortie ou à la connexion suivante.
- Les événements ne concernent que : la faux d'un admin, les joueurs inscrits dans une partie, et nos propres entités (armor stands taguées `jvb_ball` / `jvb_ghost`, non persistantes).
- Le mode de jeu, la position et le gamemode du joueur sont restaurés à la sortie. Les terrains sont protégés (casse/pose) sauf pour un admin en créatif.

## La balle

La balle est un armor stand invisible (marker) qui porte une tête, déplacé par la physique du plugin (gravité 0.04, frottement 0.98, rebond 0.6).
Sans texture, les skins utilisent un bloc. Pour une vraie balle ronde, colle la valeur base64 d'une tête de ballon (champ « Value » sur minecraft-heads.com) dans `ball_skins.<id>.texture` de `config.yml`, puis ajuste `ball_physics.render_offset_y` si la balle est trop haute ou trop basse par rapport à sa position réelle.
