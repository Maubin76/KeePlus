package fr.maubin.keeplus.settings

import fr.maubin.keeplus.sync.writeAtomic
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

private val kSettingsJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }	// fichier des réglages

val kIdleChoices      = listOf(1, 5, 15, 30)											// délais d'inactivité proposés (min)
val kHiddenChoices    = listOf(0, 30, 60, 300)										// délais en arrière-plan proposés (s)
val kClipboardChoices = listOf(15, 30, 60)												// durées du presse-papiers proposées (s)
val kPasswordChoices  = listOf(24, 48, 168, 0)											// fréquences du mot de passe maître avec l'empreinte (h ; 0 : jamais)

/*
	Réglages de l'appli, propres à l'appareil (non synchronisés).
	idleMinutes      : verrouillage après ce nombre de minutes sans geste
	hiddenSeconds    : verrouillage après ce nombre de secondes en arrière-plan (0 : aussitôt)
	clipboardSeconds : un secret copié est effacé du presse-papiers après ce délai
	passwordHours    : avec l'empreinte, le mot de passe maître est redemandé
	                   après ce nombre d'heures sans l'avoir saisi (0 : jamais)
	quickHotkey      : PC : raccourci global de la carte de saisie (« Ctrl+Shift+Space »)
	launchIntro      : Android : animation d'ouverture jouée au lancement
	updates          : nouvelle version cherchée au lancement
	ignoredUpdate    : version que l'utilisateur ne veut plus se voir proposer ; vide sans
*/
@Serializable
data class KeeSettings(
	val idleMinutes      : Int     = 5,
	val hiddenSeconds    : Int     = 60,
	val clipboardSeconds : Int     = 30,
	val passwordHours    : Int     = 168,
	val quickHotkey      : String  = "Ctrl+Shift+Space",
	val launchIntro      : Boolean = true,
	val updates          : Boolean = true,
	val ignoredUpdate    : String  = ""
)

/*
	Fichier des réglages (reglages.json). Un fichier absent ou illisible
	donne les réglages par défaut.
	fFile : fichier
*/
class SettingsStore(private val fFile: File)
{
	/*
		Réglages enregistrés.
	*/
	fun load(): KeeSettings = runCatching { kSettingsJson.decodeFromString(KeeSettings.serializer(), fFile.readText()) }.getOrDefault(KeeSettings())

	/*
		Enregistre les réglages.
		inSettings : réglages
	*/
	fun save(inSettings: KeeSettings) = writeAtomic(fFile, kSettingsJson.encodeToString(KeeSettings.serializer(), inSettings).toByteArray(Charsets.UTF_8))
}
