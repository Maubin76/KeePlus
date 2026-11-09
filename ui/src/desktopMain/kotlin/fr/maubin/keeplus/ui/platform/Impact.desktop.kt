package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable

/*
	Pas de vibration sur PC.
*/
@Composable
actual fun rememberImpactFeedback(): () -> Unit = {}
