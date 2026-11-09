package fr.maubin.keeplus.ui.platform

import androidx.compose.runtime.Composable

/*
	Vibration d'un choc lourd (bouclier qui s'écrase sur le métal) : vibration
	sourde sur Android, rien sur PC.
*/
@Composable
expect fun rememberImpactFeedback(): () -> Unit
