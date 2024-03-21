package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.config.SED_CACHE
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpStatusCodeException

@Service
class EuxCacheableKlient(
    private val euxKlientLib: EuxKlientLib,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(javaClass) }

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
    @Cacheable(cacheNames = [SED_CACHE], key = "#rinaSakId + '-' +  #dokumentId", cacheManager = "euxCacheManager")
    internal fun hentSed(rinaSakId: String, dokumentId: String): SED {
        return hentSed.measure {
            val json = euxKlientLib.hentSedJson(rinaSakId, dokumentId)
            SED.fromJsonToConcrete(json)
        }
    }

    internal fun hentSedJson(rinaSakId: String, dokumentId: String): String? {
        return euxKlientLib.hentSedJson(rinaSakId, dokumentId)
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
    internal fun hentAlleDokumentfiler(rinaSakId: String, dokumentId: String): SedDokumentfiler? {
        return hentPdf.measure {
            euxKlientLib.hentAlleDokumentfiler(rinaSakId, dokumentId)
        }
    }

    @Retryable(
        include = [HttpStatusCodeException::class],
        exclude = [HttpClientErrorException.NotFound::class],
        backoff = Backoff(delay = 30000L, maxDelay = 3600000L, multiplier = 3.0)
    )
    internal fun hentBuc(rinaSakId: String): Buc {
        return hentBuc.measure {
            euxKlientLib.hentBuc(rinaSakId) ?: throw RuntimeException("Ingen BUC")
        }
    }
}
