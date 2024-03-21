package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@Service
class EuxService( private val euxCacheableKlient: EuxCacheableKlient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(javaClass) }

    fun hentSed(rinaSakId: String, dokumentId: String): SED = euxCacheableKlient.hentSed(rinaSakId, dokumentId)

    fun hentBuc(rinaSakId: String): Buc = euxCacheableKlient.hentBuc(rinaSakId)

    fun hentAlleDokumentfiler(rinaSakId: String, dokumentId: String): SedDokumentfiler? = euxCacheableKlient.hentAlleDokumentfiler(rinaSakId, dokumentId)

    /**
     * Henter alle dokumenter (SEDer) i en Buc.
     */
    fun hentAlleGyldigeDokumenter(buc: Buc): List<ForenkletSED> {
        val documents = buc.documents ?: return emptyList()
        return documents
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
            .filterNot { it.status == SedStatus.EMPTY }
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
    }

    @OptIn(ExperimentalTime::class)
    fun hentSedMedGyldigStatus(rinaSakId: String, buc: Buc): List<Pair<String, SED>> {
         val documents = hentAlleGyldigeDokumenter(buc)
         return measureTimedValue {
             documents.filter(ForenkletSED::harGyldigStatus)
                 .map { sed -> sed.id to euxCacheableKlient.hentSed(rinaSakId, sed.id) }
                 .also { logger.info("Fant ${it.size} SED i BUCid: $rinaSakId") }
         }.also {
             logger.info("hentSed for rinasak:$rinaSakId tid: ${it.duration.inWholeSeconds}")
         }.value
    }

    fun isNavCaseOwner(buc: Buc): Boolean {
        val caseOwner = buc.participants?.filter {
            part -> part.role == "CaseOwner"
        }?.filter {
            part -> part.organisation?.countryCode == "NO"
        }?.mapNotNull { it.organisation?.countryCode }?.singleOrNull()

        return (caseOwner == "NO").also {
            if (it) { logger.info("NAV er CaseOwner i BUCid: ${buc.id} BUCtype: ${buc.processDefinitionName}")
            } else { logger.info("NAV er IKKE CaseOwner i BUCid: ${buc.id} BUCtype: ${buc.processDefinitionName}") }
        }
    }

    fun hentAlleKansellerteSedIBuc(rinaSakId: String, buc: Buc): List<SED> {
        val documents = hentAlleGyldigeDokumenter(buc)
        return documents
            .filter(ForenkletSED::erKansellert)
            .map { sed -> euxCacheableKlient.hentSed(rinaSakId, sed.id) }
            .also { logger.info("Fant ${it.size} kansellerte SED ") }
    }

    fun hentSaktypeType(sedHendelse: SedHendelse, alleSedIBuc: List<SED>): SakType? {
        //hent saktype fra R_BUC_02 - R005 sed
        if (sedHendelse.bucType == R_BUC_02) {
            return alleSedIBuc
                    .firstOrNull { it.type == R005 }
                    ?.let { filterSaktypeR005(it as R005) }

        //hent saktype fra P15000 overgang fra papir til rina. (saktype)
        } else if (sedHendelse.bucType == P_BUC_10) {
            val sed = alleSedIBuc.firstOrNull { it.type == P15000 }
            if (sed != null) {
                return when (sed.nav?.krav?.type) {
                    "02" -> GJENLEV
                    "03" -> UFOREP
                    else -> ALDER
                }
            }
        }
        return null
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
    private fun filterSaktypeR005(sed: R005): SakType {
        return when (sed.tilbakekreving?.feilutbetaling?.ytelse?.type) {
            "alderspensjon" -> ALDER
            "uførepensjon" -> UFOREP
            "etterlattepensjon_enke", "etterlattepensjon_enkemann", "andre_former_for_etterlattepensjon" -> GJENLEV
            "barnepensjon" -> BARNEP
            else -> throw RuntimeException("Klarte ikke å finne saktype for R_BUC_02")
        }
    }
}
