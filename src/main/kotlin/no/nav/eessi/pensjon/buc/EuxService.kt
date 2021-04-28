package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedDokumentfiler
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class EuxService(
    private val klient: EuxKlient,
    @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())
) {

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
                SedType.P2200 -> mapJsonToAny(json, typeRefs<P2200>())
                SedType.P4000 -> mapJsonToAny(json, typeRefs<P4000>())
                SedType.P5000 -> mapJsonToAny(json, typeRefs<P5000>())
                SedType.P6000 -> mapJsonToAny(json, typeRefs<P6000>())
                SedType.P7000 -> mapJsonToAny(json, typeRefs<P7000>())
                SedType.P8000 -> mapJsonToAny(json, typeRefs<P8000>())
                SedType.P10000 -> mapJsonToAny(json, typeRefs<P10000>())
                SedType.P15000 -> mapJsonToAny(json, typeRefs<P15000>())
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
     * @param rinaSakId: Hvilken Rina-sak (buc) dokumentene skal hentes fra.
     *
     * @return Liste med [ForenkletSED]
     */
    fun hentBucDokumenter(rinaSakId: String): List<ForenkletSED> {
        val documents = hentBuc(rinaSakId)?.documents ?: return emptyList()

        return documents
            .filter { it.id != null }
            .map { ForenkletSED(it.id!!, it.type, SedStatus.fra(it.status)) }
    }
}

private fun <T : Any> mapJsonToAny(json: String, typeRef: TypeReference<T>) = jacksonObjectMapper()
    .configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    .readValue(json, typeRef)


@JsonIgnoreProperties(ignoreUnknown = true)
data class SedWithOnlySedType(val sedType: SedType)