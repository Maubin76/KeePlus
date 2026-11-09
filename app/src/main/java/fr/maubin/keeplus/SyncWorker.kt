package fr.maubin.keeplus

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

private const val kWorkName = "synchro-wifi"											// tâche périodique

/*
	Tâche de fond : synchronise avec les PC de la maison, l'appli fermée. Le
	coffre est échangé sans être déchiffré ; une fusion attend le prochain
	déverrouillage.
	inContext : contexte
	inParams  : paramètres de WorkManager
*/
class SyncWorker(
	inContext : Context,
	inParams  : WorkerParameters
) : CoroutineWorker(inContext, inParams)
{
	/*
		Exécute la synchronisation (un échec attend le passage suivant).
	*/
	override suspend fun doWork(): Result
	{
		runCatching { AndroidSync.get(applicationContext).sync() }
		return Result.success()
	}
}

/*
	Planifie la synchronisation en arrière-plan : toutes les 15 minutes au
	mieux (Android les espace en économie d'énergie), seulement sur un réseau
	non facturé à l'usage.
	inContext : contexte
*/
fun scheduleSync(inContext: Context)
{
	val vWifi = Constraints.Builder().setRequiredNetworkType(NetworkType.UNMETERED).build()	// Wi-Fi requis
	WorkManager.getInstance(inContext).enqueueUniquePeriodicWork(
		kWorkName,
		ExistingPeriodicWorkPolicy.KEEP,
		PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES).setConstraints(vWifi).build()
	)
}
