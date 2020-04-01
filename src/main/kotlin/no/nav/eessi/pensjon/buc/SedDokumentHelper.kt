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

    fun hentAlleSedIBuc2(seds: Map<String,String?>) = seds.values.toList()


//    fun hentAlleSedIBuc3(rinaSakId: String): List<String?> = hentAlleSedIBucPair(rinaSakId).values.toList()

    fun hentAlleSedIBucPair2(rinaSakId: String): List<Pair<String?,String>> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            Pair(euxKlient.hentSed(rinaSakId, sedDocumentId),pair.second)
        }
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

    fun hentYtelseType(sedHendelse: SedHendelseModel, seds: Map<String,String?>): String? {
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            var r005Sed = seds[SedType.R005.name]

            if (r005Sed != null){
                val sedRootNode = no.nav.eessi.pensjon.pdf.mapper.readTree(r005Sed)
                return filterYtelseType(sedRootNode)

            }
        } else {
            return hentYtelseKravType(sedHendelse)
        }

        return null
    }

    private fun hentYtelseKravType(sedHendelse: SedHendelseModel): String? {
        if (sedHendelse.sedType == SedType.P2100 || sedHendelse.sedType == SedType.P15000) {
            return try {
                fagmodulKlient.hentYtelseKravType(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
            } catch (ex: Exception) {
                null
            }
        }
        return null
    }


    private fun filterYtelseType(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/tilbakekreving")
                .findValue("feilutbetaling")
                .findValue("type")
                .textValue()
    }
}

//"tilbakekreving": {
//    "anmodning":
//
//    { "type": "foreløpig" }
//    ,
//    "feilutbetaling": {
//        "ytelse":
//
//        { "type": "alderspensjon" }