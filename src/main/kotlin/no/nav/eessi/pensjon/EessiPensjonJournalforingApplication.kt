package no.nav.eessi.pensjon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class EessiPensjonJournalforingApplication

fun main(args: Array<String>) {
	runApplication<EessiPensjonJournalforingApplication>(*args)
}
