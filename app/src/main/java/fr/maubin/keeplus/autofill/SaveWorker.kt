package fr.maubin.keeplus.autofill

import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.withTimeoutOrNull

private const val kWorkName   = "autofill-enregistrement"								// tâche unique
private const val kMaxWaitMs  = 120_000L												// attente maximale (ms)

/*
	Garde le processus en vie pendant les enregistrements lancés par une carte
	d'auto-remplissage : une fois la carte fermée, KeePlus n'a plus rien à
	l'écran et Android peut geler ou tuer le processus avant la fin de
	l'écriture (Argon2, quelques secondes). Tant qu'une tâche WorkManager
	tourne, il ne le fait pas. La tâche attend seulement la fin des
	enregistrements en cours (voir VaultSession.editLater).
	inContext : contexte
	inParams  : paramètres de WorkManager
*/
class SaveWorker(
	inContext : Context,
	inParams  : WorkerParameters
) : CoroutineWorker(inContext, inParams)
{
	/*
		Attend la fin des enregistrements en cours (deux minutes au plus).
	*/
	override suspend fun doWork(): Result
	{
		withTimeoutOrNull(kMaxWaitMs) { VaultSession.awaitEdits() }
		return Result.success()
	}

	companion object
	{
		/*
			Lance la tâche si elle ne tourne pas déjà : prioritaire (Android 12
			et plus) pour démarrer aussitôt.
			inContext : contexte
		*/
		fun start(inContext: Context)
		{
			val vRequest = OneTimeWorkRequestBuilder<SaveWorker>()
				.apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST) }
				.build()																		// tâche
			WorkManager.getInstance(inContext).enqueueUniqueWork(kWorkName, ExistingWorkPolicy.KEEP, vRequest)
		}
	}
}
