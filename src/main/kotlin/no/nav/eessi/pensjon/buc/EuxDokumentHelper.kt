package no.nav.eessi.pensjon.buc

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.sed.erGyldig
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class EuxDokumentHelper(
    private val euxKlient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

    private val logger = LoggerFactory.getLogger(EuxDokumentHelper::class.java)

    private lateinit var hentSed: MetricsHelper.Metric
    private lateinit var sendSed: MetricsHelper.Metric
    private lateinit var hentBuc: MetricsHelper.Metric
    private lateinit var hentPdf: MetricsHelper.Metric
    private lateinit var settSensitiv: MetricsHelper.Metric
    private lateinit var hentBucDeltakere: MetricsHelper.Metric
    private lateinit var hentInstitusjoner: MetricsHelper.Metric

    @PostConstruct
    fun initMetrics() {
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
    fun hentAlleDokumentfiler(rinaSakId: String, dokumentId: String): SedDokumentfiler? {
        return hentPdf.measure {
            euxKlient.hentAlleDokumentfiler(rinaSakId, dokumentId)
        }
    }

    /**
     * Henter Buc fra Rina.
     */
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
            .filter { it.harGyldigStatus() }
    }

    fun hentAlleGyldigeDokumenter(buc: Buc): List<ForenkletSED> {
        return hentBucDokumenter(buc)
            .filter { it.type.erGyldig() }
            .also { logger.info("Fant ${it.size} dokumenter i BUC: $it") }
    }

    fun hentAlleSedIBuc(rinaSakId: String, documents: List<ForenkletSED>): List<Pair<String, SED>> {
        return documents
            .filter(ForenkletSED::harGyldigStatus)
            .map { sed -> Pair(sed.id, hentSed(rinaSakId, sed.id)) }
            .also { logger.info("Fant ${it.size} SED i BUCid: $rinaSakId") }
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
}
