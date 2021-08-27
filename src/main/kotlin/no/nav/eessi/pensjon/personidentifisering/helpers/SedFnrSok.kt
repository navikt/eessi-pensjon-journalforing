package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory

/**
 * Går gjennom SED og søker etter fnr/dnr
 *
 * Denne koden søker etter kjente keys som kan inneholde fnr/dnr i P og H-SED
 *
 * Kjente keys: "Pin", "kompetenteuland"
 *
 */
class SedFnrSok {

    companion object {
        private val logger = LoggerFactory.getLogger(SedFnrSok::class.java)

        /**
         * Finner alle fnr i SED
         *
         * @param sed SED i json format
         * @return distinkt set av fnr
         */
        fun finnAlleFnrDnrISed(sed: SED): Set<String> {
            try {
                val sedRootNode = jacksonObjectMapper().readTree(sed.toJson())

                return traverseNode(sedRootNode)
                        .map { it.value }
                        .toSet()
            } catch (ex: Exception) {
                logger.info("En feil oppstod under søk av fødselsnummer i SED", ex)
                throw ex
            }
        }

        /**
         * Rekursiv traversering av json noden
         */
        private fun traverseNode(jsonNode: JsonNode): List<Fodselsnummer> {
            val fnrFraNode = finnFnr(jsonNode)

            return when {
                jsonNode.isObject -> {
                    if (fnrFraNode.isEmpty())
                        jsonNode.flatMap { traverseNode(it) }
                    else
                        fnrFraNode
                }
                jsonNode.isArray -> {
                    jsonNode.flatMap { traverseNode(it) }
                }
                else -> fnrFraNode
            }
        }

        /**
         * Leter etter fnr i flere felter/objekter.
         * Siden et pin-objekt/identifikator skal kun gjelde én person, returnerer vi ved første gyldige treff.
         *
         * @return liste med norske fnr. Tom liste hvis null treff.
         */
        private fun finnFnr(jsonNode: JsonNode): List<Fodselsnummer> {
            val fieldNames = listOf("pin", "pinland", "pinannen")

            return fieldNames
                    .mapNotNull { jsonNode.get(it) }
                    .map { node -> fromSubNode(node) }
                    .firstOrNull { it.isNotEmpty() }
                    ?: listOfNotNull(jsonNode.get("identifikator").tilFnr())
        }

        private fun fromSubNode(node: JsonNode): List<Fodselsnummer> {
            val identifikator = node.get("identifikator").tilFnr()

            if (identifikator != null)
                return listOf(identifikator)

            val kompetenteuland = node.findValue("kompetenteuland").tilFnr()
            val oppholdsland = node.findValue("oppholdsland").tilFnr()

            return listOfNotNull(kompetenteuland, oppholdsland)
        }

        private fun JsonNode?.tilFnr(): Fodselsnummer? =
                Fodselsnummer.fra(this?.textValue())
    }
}
