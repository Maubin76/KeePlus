package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.components.GhostButton
import fr.maubin.keeplus.ui.components.SilverButton
import fr.maubin.keeplus.ui.components.TextLink
import fr.maubin.keeplus.ui.theme.keeType

/*
	Accueil du premier lancement : créer un coffre, ou reprendre un coffre
	KeePass existant (KeePassXC, KeePassDX…).
	inError    : message d'erreur (fichier choisi illisible) ; null si aucun
	inBusy     : opération en cours
	inOnCreate : aller à la création du coffre
	inOnImport : choisir un coffre existant
	inOnDemo   : ouvrir le coffre de démonstration
	inFolder   : dossier où le coffre sera créé ou repris ; null s'il ne peut pas être choisi
	inOnFolder : choisir un autre dossier
*/
@Composable
fun WelcomeScreen(
	inError    : String?,
	inBusy     : Boolean,
	inOnCreate : () -> Unit,
	inOnImport : () -> Unit,
	inOnDemo   : () -> Unit,
	inFolder   : String?     = null,
	inOnFolder : () -> Unit  = {}
)
{
	KeyScreen {
		HeroTitle("Vos mots de passe,\n", "bien ", "gardés.")
		BasicText(
			"Un coffre chiffré qui reste chez vous, partagé entre votre PC et votre téléphone par le Wi-Fi de la maison. Sans cloud, sans compte.",
			style = keeType().bodySmall
		)
		ErrorLine(inError)
		Spacer(Modifier.height(8.dp))
		SilverButton("Créer mon coffre", inOnCreate, inEnabled = !inBusy)
		GhostButton("Reprendre un coffre KeePass", inOnImport, inEnabled = !inBusy)
		TextLink(if (inBusy) "Préparation de la démonstration…" else "Découvrir avec des données de démo", inOnDemo, Modifier.align(Alignment.CenterHorizontally))
		inFolder?.let { vFolder ->
			Spacer(Modifier.height(4.dp))
			BasicText("Emplacement du coffre\n$vFolder", Modifier.fillMaxWidth(), style = keeType().bodySmall.copy(textAlign = TextAlign.Center))
			TextLink("Choisir un autre dossier", inOnFolder, Modifier.align(Alignment.CenterHorizontally))
		}
	}
}
