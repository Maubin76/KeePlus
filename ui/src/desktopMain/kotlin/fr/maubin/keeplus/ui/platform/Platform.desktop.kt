package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset

/*
	Boutons des réglages à leur taille normale sur PC.
*/
actual fun settingsButtonScale(): Float = 1f

/*
	Animations toujours actives sur PC.
*/
@Composable
actual fun animationsEnabled(): Boolean = true

/*
	Pas de capteur d'inclinaison sur PC : la parallaxe suit la souris.
*/
@Composable
actual fun rememberTilt(): State<Offset>? = null
