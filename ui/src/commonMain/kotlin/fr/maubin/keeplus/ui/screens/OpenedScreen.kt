package fr.maubin.keeplus.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import fr.maubin.keeplus.ui.icons.LineIcons
import fr.maubin.keeplus.ui.theme.KeeColors
import fr.maubin.keeplus.ui.theme.keeType
import fr.maubin.keeplus.vault.Vault

/*
	Coffre ouvert, version provisoire : nom du coffre, nombre d'entrées et
	bouton pour verrouiller. Remplacé par la liste des entrées à l'étape
	suivante.
	inVault  : coffre ouvert
	inDemo   : coffre de démonstration (badge « Démonstration »)
	inOnLock : verrouiller le coffre
*/
@Composable
fun OpenedScreen(
	inVault  : Vault,
	inDemo   : Boolean,
	inOnLock : () -> Unit
)
{
	val vType  = keeType()																	// styles de texte
	val vCount = inVault.items().size														// nombre d'entrées
	Column(
		Modifier
			.fillMaxSize()
			.background(Brush.verticalGradient(0f to KeeColors.mistMid.copy(alpha = 0.35f), 0.18f to KeeColors.ground))
			.safeDrawingPadding()
			.verticalScroll(rememberScrollState())
			.padding(24.dp),
		verticalArrangement = Arrangement.spacedBy(8.dp)
	) {
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			BasicText(inVault.name, Modifier.weight(1f), style = vType.display)
			Icon(
				LineIcons.Lock, "Verrouiller",
				Modifier
					.size(44.dp)
					.clip(CircleShape)
					.clickable(role = Role.Button, onClick = inOnLock)
					.padding(10.dp),
				tint = KeeColors.text
			)
		}
		if (inDemo)
		{
			BasicText(
				"Démonstration · données inventées, jamais enregistrées",
				Modifier
					.clip(CircleShape)
					.border(1.dp, KeeColors.rule, CircleShape)
					.padding(horizontal = 12.dp, vertical = 6.dp),
				style = vType.bodySmall
			)
		}
		BasicText(
			when (vCount)
			{
				0    -> "Aucune entrée pour l'instant."
				1    -> "1 entrée."
				else -> "$vCount entrées, ${inVault.groups().size} groupes, ${inVault.trashItems().size} à la corbeille."
			},
			style = vType.bodySmall
		)
		Spacer(Modifier.height(16.dp))
		for (vItem in inVault.items())
		{
			BasicText(vItem.title, style = vType.title)
			BasicText(vItem.userName, style = vType.bodySmall)
		}
	}
}
