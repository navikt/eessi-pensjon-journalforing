package no.nav.eessi.pensjon.services.norg2

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.metrics.MetricsHelper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpEntity
import org.springframework.http.HttpMethod
import org.springframework.web.client.RestTemplate

class Norg2Service(private val norg2OidcRestTemplate: RestTemplate,
        @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper(SimpleMeterRegistry())) {

    private val logger = LoggerFactory.getLogger(Norg2Service::class.java)

//        Bosatt_utland("ae0107"), 2018-11-02
//        Bosatt_Norge("ae0104")  2017-09-30
    fun hentArbeidsfordelingEnheter(behandsligType: String, geografiskOmraade: String = "ANY", tema: String = "PEN", gyldigFra: String ="2017-09-30",  diskresjonskoder: String? = "ANY") : List<Norg2ArbeidsfordelingItem>? {

            val request = Norg2ArbeidsfordelingRequest(
                    behandlingstype = behandsligType,
                    geografiskOmraade =  geografiskOmraade,
                    tema = tema,
                    gyldigFra = gyldigFra
            )

            val httpEntity = HttpEntity(request.toJson())
            val responseEntity = norg2OidcRestTemplate.exchange(
                    "/api/v1/arbeidsfordeling",
                    HttpMethod.POST,
                    httpEntity,
                    String::class.java)

            val fordelingEnheter = mapJsonToAny(responseEntity.body!!, typeRefs<List<Norg2ArbeidsfordelingItem>>())

        return fordelingEnheter
    }

    fun finnKorrektArbeidsfordelingEnheter(behandsligType: String, geografiskOmraade: String = "ANY", tema: String = "PEN",  gyldigFra: String ="2017-09-30",  diskresjonskoder: String? = "ANY", list: List<Norg2ArbeidsfordelingItem>?): String? {
        return list
                ?.asSequence()
                ?.filter { it.behandlingstype == behandsligType }
                ?.filter { it.gyldigFra == gyldigFra }
                ?.filter { it.geografiskOmraade == geografiskOmraade }
                ?.filter { it.tema == "PEN" }
                ?.map { it.enhetNr }
                ?.lastOrNull()
    }

    //fun hentNavkontorGeografiskOmraade(geografiskOmraade: String) = null

    fun hentorganiseringEnhet(enhet: String) : List<Norg2OrganiseringItem>? {
        val responseEntity = norg2OidcRestTemplate.exchange(
                "api/v1/enhet/$enhet/organisering",
                HttpMethod.GET,
                null,
                String::class.java
        )

        return mapJsonToAny(responseEntity.body!!, typeRefs<List<Norg2OrganiseringItem>>())
    }

    fun finnFylke(enhetsList: List<Norg2OrganiseringItem>?): String? {
        logger.debug("list: $enhetsList")
        return enhetsList
                ?.filter { it.orgType == "FYLKE" }
                ?.map { it.organiserer?.nr }
                ?.firstOrNull()
    }

    fun fylke2siffer(fylkesnr: String) = fylkesnr.dropLast(2)

}

data class Norg2ArbeidsfordelingRequest(
    val tema: String = "PEN",
    val diskresjonskode: String = "ANY",
    val behandlingstema: String = "ANY",
    val behandlingstype: String = "ANY",
    val geografiskOmraade: String = "ANY",
    val skalTilLokalkontor: Boolean = false,
    val oppgavetype: String = "ANY",
    val gyldigFra: String = "2017-09-30",
    val temagruppe: String = "ANY"
)

data class Norg2ArbeidsfordelingItem(
        val oppgavetype: String? = null,
        val enhetNr: String? = null,
        val behandlingstema: String? = null,
        val temagruppe: String? = null,
        val skalTilLokalkontor: Boolean? = null,
        val behandlingstype: String? = null,
        val geografiskOmraade: String? = null,
        val tema: String? = null,
        val enhetNavn: String? = null,
        val diskresjonskode: String? = null,
        val gyldigFra: String? = null,
        val enhetId: Int? = null,
        val id: Int? = null
)

data class Norg2NavkontorResponse(
        val enhetNr: String? = null,
        val sosialeTjenester: String? = null,
        val oppgavebehandler: Boolean? = null,
        val orgNrTilKommunaltNavKontor: String? = null,
        val underAvviklingDato: Any? = null,
        val type: String? = null,
        val versjon: Int? = null,
        val aktiveringsdato: String? = null,
        val underEtableringDato: String? = null,
        val navn: String? = null,
        val enhetId: Int? = null,
        val nedleggelsesdato: Any? = null,
        val organisasjonsnummer: String? = null,
        val kanalstrategi: String? = null,
        val antallRessurser: Int? = null,
        val status: String? = null,
        val orgNivaa: String? = null
)

//{
//    companion object {
//        private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)
//
//        fun fromJson(json: String): JournalpostRequest = mapper.readValue(json, JournalpostRequest::class.java)
//    }
//
//    override fun toString(): String {
//        return mapAnyToJson(this,true)
//    }
//}


data class Norg2OrganiseringItem(
        val orgType: String? = null,
        val organiserer: Organiserer? = null,
        val fra: String? = null,
        val til: Any? = null,
        val id: Int? = null,
        val organisertUnder: OrganisertUnder? = null
)

data class Organiserer(
        val nr: String? = null,
        val navn: String? = null,
        val gyldigFra: Any? = null,
        val id: Int? = null
)

data class OrganisertUnder(
        val nr: String? = null,
        val navn: String? = null,
        val gyldigFra: Any? = null,
        val id: Int? = null
)
