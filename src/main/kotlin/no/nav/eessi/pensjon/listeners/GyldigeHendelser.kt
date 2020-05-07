package no.nav.eessi.pensjon.listeners

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("prod")
@Component
class GyldigeHendelserProd : GyldigeHendelser {
    override fun innkommende() = listOf("P", "H_BUC_07")
    override fun utgaende() = listOf("P")
}

@Profile("!prod")
@Component
class GyldigeHendelserNonProd : GyldigeHendelser {
    override fun innkommende() = listOf("P", "H_BUC_07", "R_BUC_02")
    override fun utgaende() = listOf("P", "R_BUC_02")
}

interface GyldigeHendelser {
    fun mottattHendelse(hendelse: String) = getHendelseList(hendelse).map { innkommende().contains( it ) }.contains(true)

    fun sendtHendelse(hendelse: String) = getHendelseList(hendelse).map { utgaende().contains( it ) }.contains(true)

    private fun getHendelseList(hendelse: String): List<String> {
        val mapper = jacksonObjectMapper()
        val rootNode = mapper.readTree(hendelse)
        return listOf(rootNode.get("sektorKode").textValue(), rootNode.get("bucType").textValue())
    }

    fun innkommende(): List<String>
    fun utgaende(): List<String>
}
