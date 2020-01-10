package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.buc.SEDType
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun getFodselsnrFraSeder(seder: List<String?>): String? {
        var fnr: String? = null

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)

                when (SEDType.valueOf(sedRootNode.get("sed").textValue())) {
                    SEDType.P2100 -> {
                        fnr = filterGjenlevendePinNode(sedRootNode)
                    }
                    SEDType.P15000 -> {
                        val krav = sedRootNode.get("nav").get("krav").textValue()
                        fnr = if (krav == "02") {
                            filterGjenlevendePinNode(sedRootNode)
                        } else {
                            filterPersonPinNode(sedRootNode)
                        }
                    }
                    else -> {
                        fnr = filterAnnenpersonPinNode(sedRootNode)
                        if (fnr == null) {
                            //P2000 - P2200 -- andre..
                            fnr =  filterPersonPinNode(sedRootNode)
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt under henting av fnr fra buc", ex.message)
                throw RuntimeException("Noe gikk galt under henting av fnr fra buc")
            }
            if(fnr != null)
                return fnr
        }
        return fnr
    }

    fun filterAnnenpersonPinNode(node: JsonNode): String? {
        val subNode = node.at("/nav/annenperson") ?: return null
        if (subNode.at("/person/rolle").textValue() == "01") {
            return subNode.get("person")
                    .findValue("pin")
                    .filter { it.get("land").textValue() == "NO" }
                    .map { it.get("identifikator").textValue() }
                    .lastOrNull()
        }
        return null
    }

    private fun filterGjenlevendePinNode(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/pensjon/gjenlevende")
                .findValue("pin")
                .filter{ pin -> pin.get("land").textValue() =="NO" }
                .map { pin -> pin.get("identifikator").textValue() }
                .lastOrNull()
    }

    private fun filterPersonPinNode(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/nav/bruker")
                .findValue("pin")
                .filter{ pin -> pin.get("land").textValue() =="NO" }
                .map { pin -> pin.get("identifikator").textValue() }
                .lastOrNull()
    }
}
