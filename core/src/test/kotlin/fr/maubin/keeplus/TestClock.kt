package fr.maubin.keeplus

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/*
	Horloge des tests, avancée à la main.
	fNow : date actuelle
*/
class TestClock(private var fNow: Instant = Instant.parse("2026-01-01T10:00:00Z")) : Clock()
{
	/*
		Avance l'horloge.
		inSeconds : secondes à ajouter
	*/
	fun tick(inSeconds: Long = 60)
	{
		fNow = fNow.plus(Duration.ofSeconds(inSeconds))
	}

	/*
		Fuseau de l'horloge : UTC.
	*/
	override fun getZone(): ZoneId = ZoneOffset.UTC

	/*
		Même horloge (le fuseau est ignoré).
		inZone : fuseau demandé
	*/
	override fun withZone(inZone: ZoneId): Clock = this

	/*
		Date actuelle.
	*/
	override fun instant(): Instant = fNow
}
