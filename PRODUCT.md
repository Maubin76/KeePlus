# Product

<!-- impeccable:product-schema 1 -->

## Platform

android, desktop (Windows, Compose Desktop)

## Users
Un seul utilisateur, Mathieu, propriétaire et développeur de l'application. Un seul coffre, un seul mot de passe maître. Deux situations d'usage :
- **au téléphone** : retrouver un identifiant en quelques secondes, souvent d'une main, parfois en public ;
- **au PC** : créer et ranger les entrées, générer des mots de passe, faire le ménage dans le coffre.

## Product Purpose
Un gestionnaire de mots de passe partagé entre le PC et le téléphone, sans serveur ni compte : les données ne quittent jamais la maison. Le succès : retrouver et copier un mot de passe prend moins de cinq secondes, et le PC et le téléphone sont toujours à jour sans y penser.

## Positioning
Outil personnel de la gamme Plus (PatrimoinePlus, MemoPlus, SportPlus). Le coffre est un fichier **KDBX 4** standard, lisible par KeePassXC et KeePassDX : aucune dépendance à KeePlus. La synchronisation passe par le Wi-Fi de la maison, sur le même principe que PatrimoinePlus (appairage par QR code, le PC fait serveur, le téléphone fait relais).

## Operating Context
- Téléphone Android, utilisé en public : écrans protégés (`FLAG_SECURE`), mots de passe masqués par défaut.
- PC Windows : l'application reste dans la zone de notification pour répondre à la synchronisation.
- Aucune requête internet : ni icônes de sites, ni télémétrie.

## Capabilities and Constraints
- Format KDBX 4 (Argon2id, AES-256 ou ChaCha20) via la bibliothèque kotpass.
- Déverrouillage par mot de passe maître ; par empreinte sur Android (clé du Keystore).
- Verrouillage automatique, presse-papiers vidé après 30 s.
- Synchro : seul le fichier chiffré circule ; fusion entrée par entrée au déverrouillage si les deux côtés ont changé, la version perdante va dans l'historique.
- Sauvegardes : copies datées du fichier chiffré uniquement, jamais d'export en clair automatique.
- Textes d'interface en français, vouvoiement.
- Hors version 1 : remplissage automatique (v1.1), passkeys, TOTP, pièces jointes, Windows Hello, extension de navigateur.

## Evidence on Hand
Aucune donnée : l'application démarre sans coffre. Les écrans ne montrent jamais de faux identifiants présentés comme réels.

## Product Principles
1. **Sûr avant pratique** : aucun raccourci qui affaiblit le chiffrement ou laisse un secret en clair sur le disque.
2. **Trouvé en cinq secondes** : la recherche est toujours à portée, le mot de passe se copie d'un geste.
3. **Aucune prison** : le coffre reste un fichier KeePass standard, ouvrable sans KeePlus.
4. **Rien ne sort de la maison** : pas de cloud, pas de requête réseau hors du Wi-Fi familial.

## Accessibility & Inclusion
Contrastes suffisants malgré le monochrome (texte secondaire au moins 4.5:1 sur le fond). Aucune information portée par la seule couleur : erreurs et succès ont toujours une icône et un texte. Les animations marquées sont coupées si le système demande de réduire les animations.
