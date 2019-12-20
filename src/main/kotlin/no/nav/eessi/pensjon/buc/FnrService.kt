package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.buc.BucHelper.Companion.filterUtGyldigSedId
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FnrService(private val fagmodulService: FagmodulService,
        private val euxService: EuxService) {

    private val logger = LoggerFactory.getLogger(FnrService::class.java)

    private val mapper = jacksonObjectMapper()

    fun getFodselsnrFraSed(euxCaseId: String): String? {
        val alleDokumenter = fagmodulService.hentAlleDokumenter(euxCaseId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)
        val gyldigeSeds = filterUtGyldigSedId(alleDokumenterJsonNode)
        return getFodselsnrFraSed(euxCaseId, gyldigeSeds)
    }

    fun getFodselsnrFraSed(euxCaseId: String, gyldigeSeds: List<Pair<String, String>>): String? {
        var fnr: String? = null

        gyldigeSeds.forEach { pair ->
            val sedDocumentId =  pair.first
            val sedType = pair.second
            logger.info("leter igjennom sedType: $sedType etter norsk pin fra euxCaseId og sedDocid: $euxCaseId / $sedDocumentId ")
            try {
                val sedJson = euxService.hentSed(euxCaseId, sedDocumentId)
                val sedRootNode = mapper.readTree(sedJson)

                when (sedType) {
                    SEDType.P2100.name -> {
                        fnr = filterGjenlevendePinNode(sedRootNode)
                    }
                    SEDType.P15000.name -> {
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
                logger.error("Noe gikk galt under henting av fnr fra buc: $euxCaseId", ex.message)
                throw RuntimeException("Noe gikk galt under henting av fnr fra buc: rinaNr: $euxCaseId")
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
