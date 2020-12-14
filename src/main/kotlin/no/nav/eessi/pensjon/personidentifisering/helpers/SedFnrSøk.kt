package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService.Companion.erFnrDnrFormat
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Går gjennom SED og søker etter fnr/dnr
 *
 * Denne koden søker etter kjente keys som kan inneholde fnr/dnr i P og H-SED
 *
 * Kjente keys: "Pin", "kompetenteuland"
 *
 */
@Component
// TODO: Hele klassen må ryddes i. Er det også mulig å traversere SED-objektet? Eller er det best å gjøre det på JSON?
class SedFnrSøk {

    private val logger = LoggerFactory.getLogger(SedFnrSøk::class.java)

    /**
     * Finner alle fnr i SED
     *
     * @param sed SED i json format
     * @return distinkt set av fnr
     */
    fun finnAlleFnrDnrISed(sed: SED): Set<String> {
        logger.info("Søker etter fnr i SED")
        try {
            val sedJson = sed.toJson()

            val sedRootNode = jacksonObjectMapper().readTree(sedJson)
            val funnedeFnr = mutableSetOf<String>()

            traverserNode(sedRootNode, funnedeFnr)
            return funnedeFnr.toSet()
        } catch (ex: Exception) {
            logger.info("En feil oppstod under søk av fødselsnummer i SED", ex)
            throw ex
        }
    }

    /**
     * Rekursiv traversering av json noden
     */
    private fun traverserNode(jsonNode: JsonNode, funnedeFnr: MutableSet<String>) {
        val fødselsnummere = finnFnr(jsonNode)

        when {
            jsonNode.isObject -> {
                if(fødselsnummere.isEmpty()) {
                    jsonNode.forEach { node -> traverserNode(node, funnedeFnr) }
                } else {
                    leggTilFunnedeFnr(fødselsnummere, jsonNode, funnedeFnr)
                }
            }
            jsonNode.isArray -> {
                jsonNode.forEach { node -> traverserNode(node, funnedeFnr) }
            }
            else -> {
                leggTilFunnedeFnr(fødselsnummere, jsonNode, funnedeFnr)
            }
        }
    }

    private fun leggTilFunnedeFnr(fnre: List<String>, jsonNode: JsonNode, funnedeFnr: MutableSet<String>) {
        fnre.forEach { fnr ->
            if (erPinNorsk(jsonNode)) {
                funnedeFnr.add(fnr)
            }
        }
    }

    /**
     * Sjekker om noden inneholder norsk fnr/dnr
     *
     * Eksempel node:
     *  {
     *       sektor" : "pensjoner",
     *       identifikator" : "09809809809",
     *       land" : "NO"
     *  }
     */
    private fun erPinNorsk(jsonNode: JsonNode) : Boolean {
        val land = jsonNode.get("land")
        return land == null || land.textValue() == "NO"
    }

    /**
     * Finner fnr i identifikator, kompetenteuland og oppholdsland feltene
     *
     * Eksempel node:
     *  pin: {
     *          sektor" : "pensjoner",
     *          identifikator" : "12345678910",
     *          land" : "NO"
     *       }
     *
     *  eller:
     *  pin: {
     *          "identifikator" : "12345678910"
     *       }
     *
     *  eller:
     *  pin: {
     *          "kompetenteuland": "12345678910",
     *          "oppholdsland": "12345678910"
     *       }
     */
    private fun finnFnr(jsonNode: JsonNode): List<String> {
        val pin = jsonNode.get("pin")
        pin?.let {
            val idNode = pin.get("identifikator")
            if (idNode != null && erFnrDnrFormat(idNode.asText())) {
                return listOf(idNode.textValue())
            }

            val fnre = mutableListOf<String>()

            val kompetenteulandNode = pin.findValue("kompetenteuland")
            if (kompetenteulandNode != null && erFnrDnrFormat(kompetenteulandNode.asText())) {
                fnre.add(kompetenteulandNode.textValue())
            }

            val oppholdslandNode = pin.findValue("oppholdsland")
            if (oppholdslandNode != null && erFnrDnrFormat(oppholdslandNode.asText())) {
                fnre.add(oppholdslandNode.textValue())
            }
            if(fnre.isNotEmpty()) return fnre
        }

        val idNode = jsonNode.get("identifikator")
        if (idNode != null && erFnrDnrFormat(idNode.asText())) {
            return listOf(idNode.asText())
        }
        return emptyList()
    }
}