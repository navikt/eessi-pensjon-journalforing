package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus

@Service
class FnrService(private val fagmodulService: FagmodulService,
        private val euxService: EuxService) {

    private val logger = LoggerFactory.getLogger(FnrService::class.java)

    private val mapper = jacksonObjectMapper()

    fun getFodselsnrFraSedPaaVagtBuc(euxCaseId: String): String? {
        val allDocuments = fagmodulService.hentAlleDokumenterFraRinaSak(euxCaseId)
        val gyldigeSeds = filterUtGyldigSedId( allDocuments )
        return getFodselsnrFraSed(euxCaseId, gyldigeSeds)
    }

    fun getFodselsnrFraSed(euxCaseId: String, gyldigeSeds: List<Pair<String, String>>): String? {
        var fnr: String?

        gyldigeSeds.forEach { pair ->
            val sedDocumentId =  pair.first
            val sedType = pair.second
            logger.debug("leter igjennom sedType: $sedType etter norsk pin fra euxCaseId og sedDocid: $euxCaseId / $sedDocumentId ")
            try {
                val sedJson = euxService.hentSed(euxCaseId, sedDocumentId)
                val sedRootNode = mapper.readTree(sedJson)

                when (sedType) {
                    SEDType.P2100.name -> {
                        fnr = filterGjenlevendePinNode(sedRootNode)
                    }
                    SEDType.P15000.name -> {
                        val krav = sedRootNode.get("nav").get("krav").textValue()
                        if (krav == "02") {
                            fnr = filterGjenlevendePinNode(sedRootNode)
                        } else {
                            fnr = filterPersonPinNode(sedRootNode)
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
                logger.error("Feil ved henting av fødselnr buc: $euxCaseId", ex.message)
                throw IkkeFunnetException("Feil ved henting av fødselsnr på buc med rinaNr: $euxCaseId")
            }
            if (fnr != null) return fnr
        }
        throw IkkeFunnetException("Ingen fødselsnr funnet på buc med rinaNr: $euxCaseId")
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

    private fun filterGjenlevendePinNode(node: JsonNode): String? {
        return node
                .at("/pensjon/gjenlevende")
                .findValue("pin")
                .filter{ node -> node.get("land").textValue() =="NO" }
                .map { node -> node.get("identifikator").textValue() }
                .lastOrNull()
    }

    private fun filterPersonPinNode(node: JsonNode): String? {
        return node
                .at("/nav/bruker")
                .findValue("pin")
                .filter{ node -> node.get("land").textValue() =="NO" }
                .map { node -> node.get("identifikator").textValue() }
                .lastOrNull()
    }

    private fun filterUtGyldigSedId(sedJson: String?): List<Pair<String, String>> {
        val validSedtype = listOf("P2000","P2100","P2200","P1000",
                "P5000","P6000","P7000", "P8000",
                "P10000","P1100","P11000","P12000","P14000","P15000")
        val sedRootNode = mapper.readTree(sedJson)
        return sedRootNode
                .filterNot { node -> node.get("status").textValue() =="empty" }
                .filter { node ->  validSedtype.contains(node.get("type").textValue()) }
                .map { node -> Pair(node.get("id").textValue(), node.get("type").textValue()) }
                .sortedBy { (_, sorting) -> sorting }
                .toList()
    }

}

//--- Disse er benyttet av restTemplateErrorhandler  -- start
@ResponseStatus(value = HttpStatus.NOT_FOUND)
class IkkeFunnetException(message: String) : RuntimeException(message)
