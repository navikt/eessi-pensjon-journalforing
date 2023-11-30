package no.nav.eessi.pensjon.buc

import no.nav.eessi.pensjon.eux.klient.EuxKlient
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
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.sed.erGyldig
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException
import kotlin.time.ExperimentalTime
import kotlin.time.measureTimedValue

@Service
class EuxService(
    private val euxKlient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger = LoggerFactory.getLogger(EuxService::class.java)

    private lateinit var hentSed: MetricsHelper.Metric
    private lateinit var sendSed: MetricsHelper.Metric
    private lateinit var hentBuc: MetricsHelper.Metric
    private lateinit var hentPdf: MetricsHelper.Metric
    private lateinit var settSensitiv: MetricsHelper.Metric
    private lateinit var hentBucDeltakere: MetricsHelper.Metric
    private lateinit var hentInstitusjoner: MetricsHelper.Metric

    init {
        hentSed = metricsHelper.init("hentSed", alert = MetricsHelper.Toggle.OFF)
        sendSed = metricsHelper.init("hentSed", alert = MetricsHelper.Toggle.OFF)
        hentBuc = metricsHelper.init("hentBuc", alert = MetricsHelper.Toggle.OFF)
        hentPdf = metricsHelper.init("hentpdf", alert = MetricsHelper.Toggle.OFF)
        settSensitiv = metricsHelper.init("settSensitiv", alert = MetricsHelper.Toggle.OFF)
        hentBucDeltakere = metricsHelper.init("hentBucDeltakere", alert = MetricsHelper.Toggle.OFF)
        hentInstitusjoner = metricsHelper.init("hentInstitusjoner", alert = MetricsHelper.Toggle.OFF)
    }

    /**
     * Henter SED fra Rina EUX API.
     *
     * @param rinaSakId: Hvilken Rina-sak SED skal hentes fra.
     * @param dokumentId: Hvilket SED-dokument som skal hentes fra spesifisert sak.
     *
     * @return Objekt av type <T : Any> som spesifisert i param typeRef.
     */
    @Retryable(
        include = [HttpStatusCodeException::class],
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0)
    )
    fun hentSed(rinaSakId: String, dokumentId: String): SED {
        return hentSed.measure {
            val json = euxKlient.hentSedJson(rinaSakId, dokumentId)
            SED.fromJsonToConcrete(json)
        }
    }


    /**
     * Henter alle filer/vedlegg tilknyttet en SED fra Rina EUX API.
     *
     * @param rinaSakId: Hvilken Rina-sak filene skal hentes fra.
     * @param dokumentId: SED-dokumentet man vil hente vedleggene til.
     *
     * @return [SedDokumentfiler] som inneholder hovedfil, samt vedlegg.
     */
    @Retryable(
        include = [HttpStatusCodeException::class],
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0)
    )
    fun hentAlleDokumentfiler(rinaSakId: String, dokumentId: String): SedDokumentfiler? {
        return hentPdf.measure {
            euxKlient.hentAlleDokumentfiler(rinaSakId, dokumentId)
        }
    }

    /**
     * Henter Buc fra Rina.
     */
    @Retryable(
        include = [HttpStatusCodeException::class],
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0)
    )
    fun hentBuc(rinaSakId: String): Buc {
        return hentBuc.measure {
            euxKlient.hentBuc(rinaSakId) ?: throw RuntimeException("Ingen BUC")
        }
    }

    /**
     * Henter alle dokumenter (SEDer) i en Buc.
     */
    fun hentBucDokumenter(buc: Buc): List<ForenkletSED> {
        val documents = buc.documents ?: return emptyList()
        return documents
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
            .filterNot { it.status == SedStatus.EMPTY }
    }

    fun hentAlleGyldigeDokumenter(buc: Buc): List<ForenkletSED> {
        return hentBucDokumenter(buc)
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
    }

    @OptIn(ExperimentalTime::class)
    fun hentAlleSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<Pair<String, SED>> {
         return measureTimedValue {
             documents.filter(ForenkletSED::harGyldigStatus)
                 .map { sed -> sed.id to hentSed(rinaSakId, sed.id) }
                 .also { logger.info("Fant ${it.size} SED i BUCid: $rinaSakId") }
         }.also {
             logger.info("hentAlleSedIBuc for rinasak:$rinaSakId tid: ${it.duration.inWholeSeconds}")
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

    fun hentAlleKansellerteSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<SED> {
        return documents
            .filter(ForenkletSED::erKansellert)
            .map { sed -> hentSed(rinaSakId, sed.id) }
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
