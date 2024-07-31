package no.nav.eessi.pensjon

import no.nav.security.token.support.client.spring.oauth2.EnableOAuth2Client
import no.nav.security.token.support.spring.api.EnableJwtTokenValidation
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.retry.annotation.EnableRetry
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableJwtTokenValidation
@EnableOAuth2Client(cacheEnabled = true)
@EnableScheduling
@EnableRetry
class EessiPensjonJournalforingApplication

fun main(args: Array<String>) {
	runApplication<EessiPensjonJournalforingApplication>(*args)
}

