---
name: KeePlus
description: Coffre de mots de passe en noir et argent ; une brume grise qui tombe sur une grille fine, un bouclier flou en décor, des titres fins en très grand.
colors:
  void: "#050505"
  ground: "#0B0B0C"
  mist-top: "#9A9A9C"
  mist-mid: "#3A3A3C"
  surface: "#141415"
  surface-high: "#1C1C1E"
  surface-highest: "#262628"
  rule: "#2A2A2C"
  grid-line: "#FFFFFF0F"
  grid-line-lit: "#FFFFFF2E"
  emblem: "#2E2E30"
  text: "#F5F5F5"
  text-muted: "#9C9C9E"
  text-faint: "#6A6A6C"
  pass-letter: "#F5F5F5"
  pass-digit: "#C8C8CA"
  pass-symbol: "#8A8A8C"
  silver-light: "#F2F2F2"
  silver-dark: "#B8B8BA"
  silver-ink: "#111111"
  danger: "#D9645F"
  success: "#6FB592"
typography:
  hero:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "44sp"
    fontWeight: 300
    lineHeight: "50sp"
    letterSpacing: "-0.025em"
  display:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "34sp"
    fontWeight: 300
    lineHeight: "40sp"
    letterSpacing: "-0.02em"
  headline:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "24sp"
    fontWeight: 400
    lineHeight: "30sp"
    letterSpacing: "-0.01em"
  title:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "17sp"
    fontWeight: 500
    lineHeight: "24sp"
    letterSpacing: "0"
  body:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "15sp"
    fontWeight: 400
    lineHeight: "22sp"
    letterSpacing: "0"
  body-small:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "13sp"
    fontWeight: 400
    lineHeight: "18sp"
    letterSpacing: "0.005em"
  label:
    fontFamily: "Satoshi, sans-serif"
    fontSize: "14sp"
    fontWeight: 500
    lineHeight: "20sp"
    letterSpacing: "0.01em"
  secret:
    fontFamily: "JetBrains Mono, monospace"
    fontSize: "17sp"
    fontWeight: 400
    lineHeight: "24sp"
    letterSpacing: "0.02em"
radii:
  pill: "999dp"
  card: "20dp"
  field: "16dp"
  monogram: "12dp"
  logo: "10dp"
---

# KeePlus — direction artistique

## En une phrase
Du noir profond et de l'argent : une brume grise descend du haut de l'écran sur une grille très fine, un grand bouclier flou flotte en arrière-plan, et le texte, fin et grand, alterne le gris et le blanc. Référence : la page d'accueil de Proton Pass.

## Thème
**Sombre uniquement**, sur Android comme sur PC. Il n'y a pas de thème clair.

## Couleurs
- **Monochrome** : tout en noir, gris, blanc et argent.
- **Deux couleurs d'état seulement**, sourdes : `danger` (erreur, suppression, mot de passe faible) et `success` (synchro réussie, mot de passe fort). Elles s'accompagnent toujours d'une icône et d'un texte.
- Le texte secondaire (`text-muted`) atteint au moins 4.5:1 sur `ground`. `text-faint` est réservé aux indications non essentielles.

## Typographie
- **Satoshi** (Fontshare, licence gratuite) pour toute l'interface. Graisse Light (300) dans les grands corps, Regular et Medium ailleurs.
- **Mots en deux tons** : dans les grands titres, le mot qui pose le contexte est en `text-muted` et le mot fort en `text`. Exemple : « Vos mots de passe, *bien* **gardés**. »
- **Secrets** : JetBrains Mono. Les lettres sont en `pass-letter`, les chiffres en `pass-digit` et les symboles en `pass-symbol`, pour distinguer 0/O et l/1 sans couleur.

## Le décor (écrans clés seulement)
Il s'applique à l'**accueil, au déverrouillage et à l'appairage**. Il se compose de trois couches :
1. **Brume** : dégradé vertical de `mist-top` (en haut) vers `mist-mid` (vers 40 %), puis `void` (à partir de 70 %).
2. **Grille** : traits de 1 px en `grid-line`, avec un pas de 48 dp, estompés vers le bas.
3. **Emblème** : le bouclier en `emblem`, très agrandi (environ 1,6 fois la largeur de l'écran), décentré à droite, flou de 24 à 40 dp, coupé par les bords. Un grain léger (environ 3 %) couvre l'ensemble.

Les **écrans de travail** (liste, fiche, édition, réglages) ont un fond `ground` uni et seulement un léger dégradé de brume sur les 120 premiers dp, pour la lisibilité.

## Logo
Un **bouclier** plein, évidé d'un « + », dans un carré arrondi argenté (rayon `logo`). Le logo apparaît en petit en haut à gauche des écrans clés, à côté du mot « KeePlus » en `title`.

## Composants
- **Bouton principal** : pilule pleine largeur, dégradé vertical `silver-light` → `silver-dark`, liseré clair de 1 px en haut, texte `silver-ink` en `label`. Un seul par écran.
- **Bouton secondaire** : pilule avec un contour `rule`, texte `text`.
- **Champs** : fond `surface`, rayon `field`, sans contour au repos, contour `text-muted` au focus.
- **Barre de recherche** : pilule `surface-high`, toujours en haut de la liste.
- **Filtres par groupe** : pastilles en pilule. La pastille active a un fond `text` et un texte `void`.
- **Monogramme d'entrée** : initiale du titre en `title`, dans un carré `monogram`. Le gris du fond est tiré de façon stable du titre, entre `surface-high` et `surface-highest`, avec quatre nuances.
- **Ligne d'entrée** : monogramme, titre et identifiant en `text-muted`, avec une action de copie à droite. Pas de séparateur : seul l'espacement sépare les lignes.
- **Fiche** : cartes `surface` de rayon `card`, un champ par ligne. Le mot de passe est masqué par des puces, avec des boutons pour copier et pour afficher.

## Mise en page
- **Android** : un seul écran principal. Recherche en haut, pastilles de groupes, liste, et un bouton « + » flottant en pilule argentée. Réglages et appareils sont accessibles par une icône dans l'en-tête.
- **PC** : 3 colonnes. Groupes à gauche (avec Appareils et Réglages en bas), liste au centre, fiche à droite. Le décor n'apparaît que sur l'écran de déverrouillage, en plein écran.

## Mouvement (niveau marqué)
- Les transitions entre écrans se font en fondu avec un léger déplacement vertical (220 ms, décélération).
- Un **reflet argent** traverse lentement le bouton principal toutes les 6 s.
- **Parallaxe de l'emblème** : il suit l'inclinaison du téléphone (capteur de rotation, amplitude de 12 dp au maximum), ou la souris sur PC.
- **Au déverrouillage**, la grille s'illumine en vague du centre vers les bords (`grid-line` → `grid-line-lit`, 600 ms), avec une vibration courte, puis la brume se dissipe vers l'écran de travail.
- **Mot de passe faux** : le champ tremble légèrement, avec une vibration double.
- **Tout est coupé** si le système demande de réduire les animations : il ne reste que des fondus.

## Ton des textes
Français, vouvoiement, sobre. Exemples : « Déverrouillez votre coffre », « Copié — effacé dans 30 s », « Synchronisé avec Bureau il y a 2 min ».

## À éviter
- Toute couleur décorative : pas d'accent, pas de dégradé coloré.
- Les icônes de sites téléchargées.
- Le décor complet derrière les listes.
- Plus d'un bouton argenté par écran.
- Le gras dans les grands titres : la force vient de la taille et du contraste gris/blanc.
