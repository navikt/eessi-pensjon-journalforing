package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val mapper = jacksonObjectMapper()

    fun hentAlleSeds(seds: Map<String,String?>): List<String?> {
        return seds.map { it.value }.toList()
    }

    fun hentAlleSedIBuc(rinaSakId: String): Map<String,String?> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            val sedType = pair.second
            sedType to euxKlient.hentSed(rinaSakId, sedDocumentId)
        }.toMap()
    }

    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: Map<String,String?>): String? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            val r005Sed = alleSedIBuc[SedType.R005.name]
            if (r005Sed != null){
                val sedRootNode = mapper.readTree(r005Sed)
                return when (filterYtelseTypeR005(sedRootNode)) {
                    "alderspensjon" -> "AP"
                    "uføretrygd" -> "UT"
                    else -> "GP"
                }
            }
        //hent ytelsetype fra P15000 overgang fra papir til rina. (saktype)
        } else if (sedHendelse.sedType == SedType.P15000) {
            val sed = alleSedIBuc[SedType.P15000.name]
            if (sed != null) {
                val sedRootNode = mapper.readTree(sed)
                val krav = sedRootNode.get("nav").get("krav").get("type").textValue()
                return when (krav) {
                    "02" -> "GP"
                    "03" -> "UT"
                    else -> "AP"
                }
            }
        }
        return null
    }

    private fun filterYtelseTypeR005(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/tilbakekreving")
                .findValue("feilutbetaling")
                .findValue("type")
                .textValue()
    }

}
