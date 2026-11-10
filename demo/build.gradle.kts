// Appli de démonstration « Démo Connexion » : une appli tierce ordinaire (création
// de compte, connexion, changement de mot de passe) pour essayer l'auto-remplissage
// de KeePlus sur le téléphone. Jamais publiée : seule sa version de debug existe,
// installée sur les appareils branchés à chaque build debug de KeePlus (voir
// installOnDevices et app/build.gradle.kts).
plugins {
	alias(libs.plugins.android.application)
}

android {
	namespace  = "fr.maubin.democonnexion"
	compileSdk = 37

	defaultConfig {
		applicationId = "fr.maubin.democonnexion"
		minSdk        = 30
		targetSdk     = 36
		versionCode   = 1
		versionName   = "1.0"
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

/*
	Installe la version de debug sur chaque appareil branché (téléphone,
	émulateur). Sans appareil, ou si l'installation échoue, le build continue :
	la démo ne doit jamais empêcher de construire KeePlus.
*/
tasks.register("installOnDevices") {
	group       = "install"
	description = "Installe Démo Connexion (debug) sur les appareils branchés, sans échouer."
	dependsOn("assembleDebug")
	val vApk = layout.buildDirectory.file("outputs/apk/debug/demo-debug.apk")				// appli construite
	val vAdb = androidComponents.sdkComponents.adb											// outil adb du SDK
	doLast {
		val vTool    = vAdb.get().asFile.path													// chemin d'adb
		val vDevices = runCatching {
			ProcessBuilder(vTool, "devices").start().inputStream.bufferedReader().readLines()
				.drop(1)
				.filter { it.endsWith("\tdevice") }
				.map { it.substringBefore('\t') }
		}.getOrDefault(emptyList())																// appareils prêts
		if (vDevices.isEmpty()) logger.lifecycle("Démo Connexion : aucun appareil branché, rien d'installé.")
		for (vSerial in vDevices)
		{
			val vRun    = ProcessBuilder(vTool, "-s", vSerial, "install", "-r", vApk.get().asFile.path).redirectErrorStream(true).start()	// installation
			val vOutput = vRun.inputStream.bufferedReader().readText().trim()						// réponse d'adb
			if (vRun.waitFor() == 0) logger.lifecycle("Démo Connexion installée sur $vSerial.")
			else logger.warn("Démo Connexion non installée sur $vSerial : $vOutput")
		}
	}
}
