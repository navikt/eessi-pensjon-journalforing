package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P10000
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.P2200
import no.nav.eessi.pensjon.eux.model.sed.P4000
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P6000
import no.nav.eessi.pensjon.eux.model.sed.P7000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.X005
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class EuxService(
    private val klient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {
    private val logger = LoggerFactory.getLogger(EuxService::class.java)

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
            val json = klient.hentSedJson(rinaSakId, dokumentId)
            val sed = json?.let { mapJsonToAny(it, typeRefs<SED>()) }

            when(sed!!.type) {
                SedType.P2000 -> mapJsonToAny(json, typeRefs<P2000>())
                SedType.P2200 -> mapJsonToAny(json, typeRefs<P2200>())
                SedType.P4000 -> mapJsonToAny(json, typeRefs<P4000>())
                SedType.P5000 -> mapJsonToAny(json, typeRefs<P5000>())
                SedType.P6000 -> mapJsonToAny(json, typeRefs<P6000>())
                SedType.P7000 -> mapJsonToAny(json, typeRefs<P7000>())
                SedType.P8000 -> mapJsonToAny(json, typeRefs<P8000>())
                SedType.P10000 -> mapJsonToAny(json, typeRefs<P10000>())
                SedType.P15000 -> {
                    val psed = mapJsonToAny(json, typeRefs<P15000>())
                    psed.pensjon = Pensjon(gjenlevende = psed.p15000Pensjon?.gjenlevende)
                    psed
                }
                SedType.X005 -> mapJsonToAny(json, typeRefs<X005>())
                SedType.R005 -> mapJsonToAny(json, typeRefs<R005>())
                else -> sed
            }
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
            klient.hentAlleDokumentfiler(rinaSakId, dokumentId)
        }
    }

    /**
     * Henter Buc fra Rina.
     *
     * @param rinaSakId: Hvilken Rina-sak (buc) som skal hentes.
     *
     * @return [Buc]
     */
    fun hentBuc(rinaSakId: String): Buc? {
        return hentBuc.measure {
            klient.hentBuc(rinaSakId)
        }
    }

    /**
     * Henter alle dokumenter (SEDer) i en Buc.
     *
     * @param buc: Hvilken Rina-sak (buc) dokumentene skal hentes fra.
     *
     * @return Liste med [ForenkletSED]
     */
    fun hentBucDokumenter(buc: Buc): List<ForenkletSED> {
        val documents = buc.documents ?: return emptyList()
        return documents
            .onEach { logger.debug("Hva er dette: ${it.id}, ${it.type}, ${it.status}") }
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
    }
}

private fun <T : Any> mapJsonToAny(json: String, typeRef: TypeReference<T>) = jacksonObjectMapper()
    .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .readValue(json, typeRef)
