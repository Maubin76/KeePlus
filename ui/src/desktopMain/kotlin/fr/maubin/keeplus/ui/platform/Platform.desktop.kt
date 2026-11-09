package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.geometry.Offset

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
