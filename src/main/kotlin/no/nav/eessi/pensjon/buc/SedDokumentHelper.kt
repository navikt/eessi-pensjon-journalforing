package no.nav.eessi.pensjon.buc

import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.erGyldig
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(
    private val fagmodulKlient: FagmodulKlient,
    private val euxService: EuxService
) {

    private val logger = LoggerFactory.getLogger(SedDokumentHelper::class.java)

    fun hentAlleGydligeDokumenter(rinaSakId: String): List<ForenkletSED> {
        return euxService.hentBucDokumenter(rinaSakId)
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i Fagmodulen: $it") }
    }

    fun hentAlleSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<Pair<String, SED>> {
        return documents
            .filter(ForenkletSED::harGyldigStatus)
            .map { sed -> Pair(sed.id, euxService.hentSed(rinaSakId, sed.id)) }
            .also { logger.info("Fant ${it.size} SED ") }
    }

    fun hentAlleKansellerteSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<SED> {
        return documents
            .filter(ForenkletSED::erKansellert)
            .map { sed -> euxService.hentSed(rinaSakId, sed.id) }
            .also { logger.info("Fant ${it.size} kansellerte SED ") }
    }

    fun hentSaktypeType(sedHendelse: SedHendelseModel, alleSedIBuc: List<SED>): Saktype? {
        //hent saktype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == BucType.R_BUC_02) {
            return alleSedIBuc
                    .firstOrNull { it.type == SedType.R005 }
                    ?.let { filterSaktypeR005(it as R005) }

        //hent saktype fra P15000 overgang fra papir til rina. (saktype)
        } else if (sedHendelse.bucType == BucType.P_BUC_10) {
            val sed = alleSedIBuc.firstOrNull { it.type == SedType.P15000 }
            if (sed != null) {
                return when (sed.nav?.krav?.type) {
                    "02" -> Saktype.GJENLEV
                    "03" -> Saktype.UFOREP
                    else -> Saktype.ALDER
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


    /**
     * eux-acl - /codes/mapping/tilbakekrevingfeilutbetalingytelsetypekoder.properties
     * uførepensjon=01
     * alderspensjon=02
     * etterlattepensjon_enke=03
     * etterlattepensjon_enkemann=04
     * barnepensjon=05
     * andre_former_for_etterlattepensjon=99
     *
     * */
    private fun filterSaktypeR005(sed: R005): Saktype {
        return when (sed.tilbakekreving?.feilutbetaling?.ytelse?.type) {
            "alderspensjon" -> Saktype.ALDER
            "uførepensjon" -> Saktype.UFOREP
            "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> Saktype.GJENLEV
            "barnepensjon" -> Saktype.BARNEP
            else -> throw RuntimeException("Klarte ikke å finne saktype for R_BUC_02")
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
