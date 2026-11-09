package fr.maubin.keeplus.ui.platform

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/*
	Presse-papiers Android : un secret est marqué sensible (pas d'aperçu dans
	la bulle du système, Android 13 et plus) et effacé au bout du délai.
	L'appli ne peut pas relire le presse-papiers en arrière-plan : il est
	effacé seulement si aucune autre copie n'a été faite par KeePlus
	entre-temps.
	fBoard   : presse-papiers du système
	fHandler : minuteur des effacements, sur le fil principal
	fLast    : numéro de la dernière copie
*/
private class AndroidClipboard(inContext: Context) : SecretClipboard
{
	private val fBoard   = inContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
	private val fHandler = Handler(Looper.getMainLooper())
	private var fLast    = 0

	/*
		Copie un texte, et programme son effacement si c'est un secret.
		inLabel  : nom de la donnée
		inText   : texte copié
		inSecret : marquer sensible et effacer après le délai
		inDelay  : délai d'effacement, en secondes
	*/
	override fun copy(
		inLabel  : String,
		inText   : String,
		inSecret : Boolean,
		inDelay  : Int
	)
	{
		val vClip = ClipData.newPlainText(inLabel, inText)									// donnée copiée
		if (inSecret)
		{
			val vKey = if (Build.VERSION.SDK_INT >= 33) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"	// marque « sensible »
			vClip.description.extras = PersistableBundle().apply { putBoolean(vKey, true) }
		}
		fBoard.setPrimaryClip(vClip)
		val vNumber = ++fLast																// numéro de cette copie
		if (inSecret)
		{
			fHandler.postDelayed({ if (fLast == vNumber) runCatching { fBoard.clearPrimaryClip() } }, inDelay * 1000L)
		}
	}
}

/*
	Presse-papiers des secrets du téléphone.
*/
@Composable
actual fun rememberSecretClipboard(): SecretClipboard
{
	val vContext = LocalContext.current.applicationContext								// contexte de l'appli
	return remember(vContext) { AndroidClipboard(vContext) }
}
