package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.PensjonSak
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun hentAlleSeds(seds: Map<String, String?>): List<String?> {
        return seds.map { it.value }.toList()
    }

    fun hentAlleSedIBuc(rinaSakId: String): Map<String, String?> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            val sedType = pair.second
            sedType to euxKlient.hentSed(rinaSakId, sedDocumentId)
        }.toMap()
    }

    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: Map<String, String?>): YtelseType? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            val r005Sed = alleSedIBuc[SedType.R005.name]
            if (r005Sed != null) {
                val sedRootNode = mapper.readTree(r005Sed)
                return when (filterYtelseTypeR005(sedRootNode)) {
                    "alderspensjon" -> YtelseType.ALDER
                    "uførepensjon" -> YtelseType.UFOREP
                    "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> YtelseType.GJENLEV
                    else -> throw RuntimeException("Klarte ikke å finne ytelsetype for R_BUC_02")
                }
            }
            //hent ytelsetype fra P15000 overgang fra papir til rina. (saktype)
        } else if (sedHendelse.sedType == SedType.P15000) {
            val sed = alleSedIBuc[SedType.P15000.name]
            if (sed != null) {
                val sedRootNode = mapper.readTree(sed)
                val krav = sedRootNode.get("nav").get("krav").get("type").textValue()
                return when (krav) {
                    "02" -> YtelseType.GJENLEV
                    "03" -> YtelseType.UFOREP
                    else -> YtelseType.ALDER
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

    fun hentPensjonSakFraSED(aktoerId: String, alleSedIBuc: Map<String, String?>): PensjonSak? {
        val list = hentSakIdFraSED(alleSedIBuc)
        return if (list.isNotEmpty()) {
            validerSakIdFraSEDogReturnerEnValid(aktoerId, list)
        } else {
            null
        }
    }

    private fun hentSakIdFraSED(alleSedIBuc: Map<String, String?>): List<String> {
        val list = mutableListOf<String>()

        alleSedIBuc.forEach { (type, sed) ->
            val sedRootNode = mapper.readTree(sed)
            val eessi = filterEESSIsak(sedRootNode.get("nav"))
            logger.debug("eessi saknummer: $eessi")
            val sakid = eessi?.let { trimSakidString(it) }
            logger.debug("trimmet saknummer: $sakid")
            if (sakid != null && sakid.isNotBlank()) {
                list.add(sakid)
                logger.debug("legger sakid til liste...")
            }
        }

        return list.distinct()
    }

    private fun filterEESSIsak(navNode: JsonNode): String? {
        val essiSakSubNode = navNode.findValue("eessisak") ?: return null
        return essiSakSubNode
                .filter { pin -> pin.get("land").textValue() == "NO" }
                .map { pin -> pin.get("saksnummer").textValue() }
                .lastOrNull()
    }

    fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")

    private fun validerSakIdFraSEDogReturnerEnValid(aktoerId: String, list: List<String>): PensjonSak? {
        val saklist = fagmodulKlient.hentPensjonSaklist(aktoerId)
        return saklist.filter { sak -> list.contains(sak.sakid.toString()) }.lastOrNull().also {
            logger.debug("Funnet og validert sak: $it")
        }
    }

}
