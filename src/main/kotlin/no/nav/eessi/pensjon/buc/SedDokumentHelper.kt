package no.nav.eessi.pensjon.buc

import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.KravType
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.models.sed.SedTypeUtils
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(
    private val fagmodulKlient: FagmodulKlient,
    private val euxService: EuxService
) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)

    private val sedTypeRef = typeRefs<SED>()

    fun hentAlleGydligeDokumenter(rinaSakId: String): List<ForenkletSED> {
        val ugyldigeSedTyper: Set<SedType> = SedTypeUtils.ugyldigeTyper

        return euxService.hentBucDokumenter(rinaSakId)
            .filterNot { sed -> sed.type == null }
            .filterNot { sed -> sed.type in ugyldigeSedTyper }
            .also { logger.info("Fant ${it.size} dokumenter i Fagmodulen: $it") }
    }

    fun hentAlleSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<SED> {
        return documents
            .filter(ForenkletSED::harGyldigStatus)
            .mapNotNull { sed -> euxService.hentSed(rinaSakId , sed.id, sedTypeRef) }
    }

    fun hentAlleKansellerteSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<SED> {
        return documents
                .filter(ForenkletSED::erKansellert)
                .mapNotNull { sed -> euxService.hentSed(rinaSakId, sed.id, sedTypeRef) }
    }

    fun hentYtelseType(sedHendelse: SedHendelseModel, alleSedIBuc: List<SED>): YtelseType? {
        //hent ytelsetype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            return alleSedIBuc
                    .firstOrNull { it.type == SedType.R005 }
                    ?.let { filterYtelseTypeR005(it) }

        } else if (sedHendelse.bucType == BucType.P_BUC_10) {
            //hent ytelsetype fra P15000 overgang fra papir til rina. (saktype)
            val sed = alleSedIBuc.firstOrNull { it.type == SedType.P15000 }
            if (sed != null) {
                return when (sed.nav?.krav?.type) {
                    KravType.ETTERLATTE -> YtelseType.GJENLEV
                    KravType.UFORE -> YtelseType.UFOREP
                    else -> YtelseType.ALDER
                }
            }
        }
        return null
    }

    fun hentPensjonSakFraSED(aktoerId: String, alleSedIBuc: List<SED>): SakInformasjon? {
        return hentSakIdFraSED(alleSedIBuc)?.let { sakId ->
            validerSakIdFraSEDogReturnerPensjonSak(aktoerId, sakId)
        }
    }

    private fun filterYtelseTypeR005(sed: SED): YtelseType {
        val type = sed.tilbakekreving?.feilutbetaling?.ytelse?.type

        /*
        eux-acl - /codes/mapping/tilbakekrevingfeilutbetalingytelsetypekoder.properties
        uførepensjon=01
        alderspensjon=02
        etterlattepensjon_enke=03
        etterlattepensjon_enkemann=04
        barnepensjon=05
        andre_former_for_etterlattepensjon=99
        */

        logger.info("Henter ytelse fra R005: $type")

        return when (type) {
            "alderspensjon" -> YtelseType.ALDER
            "uførepensjon" -> YtelseType.UFOREP
            "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> YtelseType.GJENLEV
            "barnepensjon" -> YtelseType.BARNEP
            else -> throw RuntimeException("Klarte ikke å finne ytelsetype for R_BUC_02")
        }
    }

    private fun hentSakIdFraSED(sedListe: List<SED>): String? {
        return sedListe
                .mapNotNull { sed -> filterEESSIsak(sed) }
                .map { id -> trimSakidString(id) }
                .distinct()
                .singleOrNull()
                .also { sakId -> logger.debug("Fant sakId i SED: $sakId") }
    }

    private fun filterEESSIsak(sed: SED): String? {
        val sak = sed.nav?.eessisak ?: return null

        return sak.filter { it.land == "NO" }
                .mapNotNull { it.saksnummer }
                .lastOrNull()
    }

    private fun trimSakidString(saknummerAsString: String) = saknummerAsString.replace("[^0-9]".toRegex(), "")

    private fun validerSakIdFraSEDogReturnerPensjonSak(aktoerId: String, sedSakId: String): SakInformasjon? {
        val saklist: List<SakInformasjon> = try {
            fagmodulKlient.hentPensjonSaklist(aktoerId)
        } catch (e: Exception) {
            logger.warn("Feil ved henting av saker på aktørId=$aktoerId – Returnerer tom liste. ", e)
            return null
        }
        logger.info("aktoerid: $aktoerId sedSak: $sedSakId Pensjoninformasjon: ${saklist.toJson()}")

        val gyldigSak = saklist.firstOrNull { it.sakId == sedSakId }
        return if (saklist.size > 1)
            gyldigSak?.copy(tilknyttedeSaker = saklist.filterNot { it.sakId == gyldigSak.sakId })
        else
            gyldigSak
    }

}
