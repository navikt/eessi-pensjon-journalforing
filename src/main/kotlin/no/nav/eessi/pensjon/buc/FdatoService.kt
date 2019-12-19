package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.buc.BucHelper.Companion.filterUtGyldigSedId
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.ResponseStatus

@Service
class FdatoService(private val fagmodulService: FagmodulService,
                   private val euxService: EuxService) {

    private val logger = LoggerFactory.getLogger(FdatoService::class.java)

    private val mapper = jacksonObjectMapper()

    fun getFDatoFromSed(euxCaseId: String): String? {
        val alleDokumenter = fagmodulService.hentAlleDokumenter(euxCaseId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = filterUtGyldigSedId(alleDokumenterJsonNode)
        return finnFDatoFraSed(euxCaseId,gyldigeSeds)

    }

    fun finnFDatoFraSed(euxCaseId: String, gyldigeSeds: List<Pair<String, String>>): String? {
        var fdato: String?

        gyldigeSeds.forEach { pair ->
            try {
                val sedDocumentId =  pair.first
                val sedType = pair.second
                val sedJson = euxService.hentSed(euxCaseId, sedDocumentId)
                val sedRootNode = mapper.readTree(sedJson)

                when (sedType) {
                    SEDType.P2100.name -> {
                        fdato = filterGjenlevendeFDatoNode(sedRootNode)
                    }
                    SEDType.P15000.name -> {
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
            } catch (ex: Exception) {
                logger.error("Feil ved henting av fødseldato, ${ex.message}", ex)
                throw FdatoIkkeFunnetException("Ingen fødselsdato funnet")
            }
            print(fdato)
            if (fdato != null) return fdato
        }
        throw FdatoIkkeFunnetException("Ingen fødselsdato funnet")
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

//--- Disse er benyttet av restTemplateErrorhandler  -- start
@ResponseStatus(value = HttpStatus.NOT_FOUND)
class FdatoIkkeFunnetException(message: String) : RuntimeException(message)
