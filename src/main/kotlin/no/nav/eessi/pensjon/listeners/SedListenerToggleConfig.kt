package no.nav.eessi.pensjon.listeners

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SedListenerToggleConfig {

    @Value("\${namespace}")
    private lateinit var nameSpace: String

    @Bean
    fun gyldigeHendelser() = GyldigeHendelser(nameSpace)

}

class GyldigeHendelser(private val nameSpace: String) {

    fun gyldigeInnkommendeHendelser(): List<String> {
        return if (nameSpace == "P") {
            listOf("P", "H_BUC_07")
        } else {
            listOf("P", "H_BUC_07", "R_BUC_02")
        }
    }

    fun gyldigeUtgaendeHendelser(): List<String> {
        return if (nameSpace == "P") {
            listOf("P")
        } else {
            listOf("P", "R_BUC_02")
        }

    }

}
