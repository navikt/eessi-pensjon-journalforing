package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class FdatoHelper {

    private val logger = LoggerFactory.getLogger(FdatoHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun finnEnFdatoFraSEDer(seder: List<String?>): LocalDate {
        var fdato: String?

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)
                val sedType = SedType.valueOf(sedRootNode.get("sed").textValue())

                if(sedType.kanInneholdeFnrEllerFdato) {
                    when (sedType) {
                        SedType.P2100 -> {
                            fdato = filterGjenlevendeFDatoNode(sedRootNode)
                        }
                        SedType.P15000 -> {
                            val krav = sedRootNode.get("nav").get("krav").textValue()
                            fdato = if (krav == "02") {
                                filterGjenlevendeFDatoNode(sedRootNode)
                            } else {
                                filterPersonFDatoNode(sedRootNode)
                            }
                        }
                        SedType.R005 -> {
                            fdato = filterPersonR005DatoNode(sedRootNode)
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
                        return LocalDate.parse(fdato, DateTimeFormatter.ISO_DATE)
                    }
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt ved henting av fødselsdato fra SED", ex)
            }
        }
        // Fødselsnummer er ikke nødvendig for å fortsette journalføring men fødselsdato er obligatorisk felt i alle krav SED og bør finnes for enhver BUC
        throw RuntimeException("Fant ingen fødselsdato i listen av SEDer")
    }


    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fdato
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fdato
     *
     * * hvis ingen intreffer returnerer vi null
     */
    private fun filterPersonR005DatoNode(sedRootNode: JsonNode): String? {
        val subnode = sedRootNode.at("/nav/bruker").toList()
        return if ( subnode.size == 1 ) {
            subnode.first()
                .get("person")
                .get("foedselsdato").textValue()
        } else {
            //lete etter  -- status 07 (avdød) status 03 enke?
            subnode.filter { node -> node.get("tilbakekreving").get("status").get("type").textValue() == "avdød_mottaker_av_ytelser" }
                .map { node -> node.get("person").get("foedselsdato").textValue() }
                .first()
        }

    }

    /**
     * P10000 - [01] Søker til etterlattepensjon
     * P10000 - [02] Forsørget/familiemedlem
     * P10000 - [03] Barn
     */
    private fun filterAnnenPersonFDatoNode(sedRootNode: JsonNode): String? {
        val subNode = sedRootNode.at("/nav/annenperson") ?: return null
        if (subNode.at("/person/rolle").textValue() == "01") {
            return subNode.get("person")
                    .get("foedselsdato").textValue()
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
