package no.nav.eessi.pensjon

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Profile

@SpringBootApplication
@Profile("integrationtest")
class EessiPensjonJournalforingTestApplication

fun main(args: Array<String>) {
	runApplication<EessiPensjonJournalforingTestApplication>(*args)
}

