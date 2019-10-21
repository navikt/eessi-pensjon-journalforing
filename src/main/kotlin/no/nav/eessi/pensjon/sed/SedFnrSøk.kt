package no.nav.eessi.pensjon.sed

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
class SedFnrSøk {

    private val mapper = jacksonObjectMapper()
    private val logger = LoggerFactory.getLogger(SedFnrSøk::class.java)

    /**
     * Finner alle fnr i SED
     *
     * @param sed SED i json format
     * @return distinkt set av fnr
     */
    fun finnAlleFnrDnrISed(sed: String) : Set<String> {
        logger.info("Søker etter fnr i SED")
        try {
            val sedRootNode = mapper.readTree(sed)
            val funnedeFnr = mutableSetOf<String>()

            traverserNode(sedRootNode, funnedeFnr)
            return funnedeFnr.toSet()
        } catch ( ex : Exception) {
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
        if (land == null) {
            return true
        } else if (land.textValue() == "NO") {
            return true
        }
        return false
    }

    private fun erFnrDnrFormat(id: String?) : Boolean {
        return id != null && id.length == 11
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

            val kompetenteulandNode = pin.get("kompetenteuland")
            if (kompetenteulandNode != null && erFnrDnrFormat(kompetenteulandNode.asText())) {
                fnre.add(kompetenteulandNode.textValue())
            }

            val oppholdslandNode = pin.get("oppholdsland")
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