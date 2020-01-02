package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FdatoHelper {

    private val logger = LoggerFactory.getLogger(FdatoHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun finnFDatoFraSeder(seder: List<String?>): String {
        var fdato: String?

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)

                when (SEDType.valueOf(sedRootNode.get("sed").textValue())) {
                    SEDType.P2100 -> {
                        fdato = filterGjenlevendeFDatoNode(sedRootNode)
                    }
                    SEDType.P15000 -> {
                        val krav = sedRootNode.get("nav").get("krav").textValue()
                        fdato = if (krav == "02") {
                            filterGjenlevendeFDatoNode(sedRootNode)
                        } else {
                            filterPersonFDatoNode(sedRootNode)
                        }
                    }
                    else -> {
                        fdato = filterAnnenPersonFDatoNode(sedRootNode)
                        if (fdato == null) {
                            //P2000 - P2200 -- andre..
                            fdato = filterPersonFDatoNode(sedRootNode)
                        }
                    }
                }
                if(fdato != null) {
                    return fdato!!
                }
                throw RuntimeException("Fant ingen fdato i listen av SEDer")
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra liste av SEDer, ${ex.message}")
                throw RuntimeException("Noe gikk galt ved henting av fødselsdato fra liste av SEDer")
            }
        }
        // Fødselsnummer er ikke nødvendig for å fortsette journalføring men fødselsdato er obligatorisk felt i alle krav SED og bør finnes for enhver BUC
        throw RuntimeException("Fant ikke fødselsdato i BUC")
    }

    private fun filterAnnenPersonFDatoNode(sedRootNode: JsonNode): String? {
        val subNode = sedRootNode.at("/nav/annenperson") ?: return null
        if (subNode.at("/person/rolle").textValue() == "01") {
            return subNode.get("person")
                    .map { node -> node.get("foedselsdato").textValue() }
                    .lastOrNull()
        }
        return null
    }

    private fun filterGjenlevendeFDatoNode(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/pensjon/gjenlevende/person")
                .get("foedselsdato").textValue()
    }

    private fun filterPersonFDatoNode(sedRootNode: JsonNode): String? {
        return sedRootNode.at("/nav/bruker/person")
                .get("foedselsdato").textValue()
    }
}
