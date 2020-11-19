package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.SediBuc
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun hentAlleSedIBuc(rinaSakId: String): List<SediBuc> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)

        val gyldigeSeds = filterUtGyldigSedId(alleDokumenter)

        gyldigeSeds.forEach {
            logger.debug("id: ${it.id} status: ${it.status} type: ${it.type}")
            it.sedjson = euxKlient.hentSed(rinaSakId, it.id)
        }

        return gyldigeSeds
    }


    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: List<SediBuc>): YtelseType? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            val r005Sed = SediBuc.getValuesOf(SedType.R005, alleSedIBuc)
            //val r005Sed = alleSedIBuc[SedType.R005.name]
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
            val sed = SediBuc.getValuesOf(SedType.P15000, alleSedIBuc)
            //val sed = alleSedIBuc[SedType.P15000.name]
            if (sed != null) {
                val sedRootNode = mapper.readTree(sed)
                return when (sedRootNode.get("nav").get("krav").get("type").textValue()) {
                    "02" -> YtelseType.GJENLEV
                    "03" -> YtelseType.UFOREP
                    else -> YtelseType.ALDER
                }
            }
        }
        return null
    }

    fun hentPensjonSakFraSED(aktoerId: String, alleSedIBuc: List<String?>): SakInformasjon? {
        val list = hentSakIdFraSED(alleSedIBuc)
        return if (list.isNotEmpty()) {
            validerSakIdFraSEDogReturnerPensjonSak(aktoerId, list)
        } else {
            null
        }
    }

    private fun filterYtelseTypeR005(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/tilbakekreving")
                .findValue("feilutbetaling")
                .findValue("type")
                .textValue()
    }

    private fun filterUtGyldigSedId(alleDokumenter: String?): List<SediBuc> {
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)
        val validSedtype = listOf("P2000","P2100","P2200","P1000",
                "P5000","P6000","P7000", "P8000", "P9000",
                "P10000","P1100","P11000","P12000","P14000","P15000", "H070", "R005")

        return alleDokumenterJsonNode
                .asSequence()
                .filterNot { rootNode -> rootNode.get("status").textValue() =="empty" }
//                .filter { rootNode-> rootNode.get("status").textValue() == "received" || rootNode.get("status").textValue() == "sent" }
                .filter { rootNode ->  validSedtype.contains(rootNode.get("type").textValue()) }
                .map { validSeds -> SediBuc(id = validSeds.get("id").textValue(),type = SedType.valueOf(validSeds.get("type").textValue()), status = validSeds.get("status").textValue()) }
                .sortedBy { it.type }
                .toList()
    }

    private fun hentSakIdFraSED(alleSedIBuc: List<String?>): List<String> {
        val list = mutableListOf<String>()

        alleSedIBuc.forEach { sed ->
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

    private fun validerSakIdFraSEDogReturnerPensjonSak(aktoerId: String, list: List<String>): SakInformasjon? {
        val saklist = fagmodulKlient.hentPensjonSaklist(aktoerId)
        logger.debug("aktoerid: $aktoerId sedSak: $list penSak: ${saklist.toJson()}")
        return if (saklist.size == 1) saklist.firstOrNull{ it.sakId in list }
        else saklist.firstOrNull {  it.sakId in list && it.sakType != YtelseType.GENRL }
    }

}
