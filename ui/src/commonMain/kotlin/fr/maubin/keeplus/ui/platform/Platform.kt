package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset

/*
	Vrai si les animations du système sont actives. Quand elles sont coupées
	(Android : Accessibilité > Supprimer les animations), les effets marqués
	(parallaxe, vague, reflet) laissent place à de simples fondus. Toujours
	vrai sur PC.
*/
@Composable
expect fun animationsEnabled(): Boolean

/*
	Échelle des boutons de la page Réglages : 0,8 sur Android (écran étroit,
	boutons plus compacts), 1 sur PC.
*/
expect fun settingsButtonScale(): Float

/*
	Inclinaison de l'appareil, de -1 à 1 sur chaque axe, lissée, pour la
	parallaxe de l'emblème ; null là où il n'y a pas de capteur (PC : la
	parallaxe suit alors la souris).
*/
@Composable
expect fun rememberTilt(): State<Offset>?
