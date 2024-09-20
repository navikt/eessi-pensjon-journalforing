package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.journalforing.journalpost.OpprettJournalpostRequestBase
import no.nav.eessi.pensjon.journalforing.saf.SafDokument
import no.nav.eessi.pensjon.journalforing.saf.SafSak
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.oppgaverouting.Enhet
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import java.io.IOException
import java.time.LocalDateTime

/**
 * /rest/journalpostapi/v1/journalpost
 *
 * Oppretter en journalpost i fagarkivet, med eller uten dokumenter
 */
data class OpprettJournalpostRequest(
    val avsenderMottaker: AvsenderMottaker?,
    val behandlingstema: Behandlingstema? = null,
    override val bruker: Bruker? = null,
    @JsonDeserialize(using = JsonAsStringDeserializer::class)
    @JsonRawValue
    val dokumenter: String,
    val journalfoerendeEnhet: Enhet? = null,
    val journalpostType: JournalpostType,
    val sak: Sak? = null,
    override val tema: Tema = Tema.PENSJON,
    val tilleggsopplysninger: List<Tilleggsopplysning>? = null,
    val tittel: String
): OpprettJournalpostRequestBase()

data class OpprettJournalpostRequestGjenny(
    override val bruker: Bruker? = null,
    override val tema: Tema,
) : OpprettJournalpostRequestBase()

data class JournalpostMedSedInfo(
    val journalpostRequest: OpprettJournalpostRequest,
    val sedHendelse: SedHendelse,
    val sedHendelseType: HendelseType
)

enum class JournalpostType: Code {
    UTGAAENDE {
        override fun decode() = "Utgående"
    },
    INNGAAENDE {
        override fun decode() = "Inngående"
    }
}

data class Sak(
    val sakstype: String,
    val fagsakid: String,
    val fagsaksystem: String,
)

/**
 * Avsender eller mottaker informasjon for personen eller organisasjonen som enten sender eller mottar dokumentet
 *
 * https://confluence.adeo.no/display/BOA/Type%3A+AvsenderMottaker
 */
data class AvsenderMottaker(
    val id: String? = null,
    val idType: IdType? = IdType.UTL_ORG,
    val navn: String? = null,
    val land: String? = null
)

enum class IdType {
    UTL_ORG
}

data class Bruker(
    val id: String,
    val idType: String = "FNR"
)

data class Tilleggsopplysning(
        val nokkel: String,
        val verdi: String
)

interface Code {
    fun decode(): String
}

private class JsonAsStringDeserializer : JsonDeserializer<String>() {
    @Throws(IOException::class, JsonProcessingException::class)
    override fun deserialize(jsonParser: JsonParser, deserializationContext: DeserializationContext): String {
        val tree = jsonParser.codec.readTree<TreeNode>(jsonParser)
        return tree.toString()
    }
}
@JsonIgnoreProperties(ignoreUnknown = true)
data class JournalpostResponse(
    val journalpostId: String?,
    val eksternReferanseId: String?,
    val tema: Tema?,
    val dokumenter: List<SafDokument?>,
    val journalstatus: Journalstatus?,
    val journalpostferdigstilt: Boolean?,
    val avsenderMottaker: AvsenderMottaker?,
    val behandlingstema: Behandlingstema?,
    val journalforendeEnhet: String?,
    val temanavn: String?,
    val bruker: Bruker?,
    val sak: SafSak?,
    val datoOpprettet: LocalDateTime? = null
)

enum class Journalstatus {
    UKJENT, OPPLASTING_DOKUMENT, RESERVERT, UKJENT_BRUKER, AVBRUTT, UTGAAR, FEILREGISTRERT, UNDER_ARBEID, EKSPEDERT, FERDIGSTILT, JOURNALFOERT, MOTTATT
}

@JsonIgnoreProperties(ignoreUnknown = true)
class OpprettJournalPostResponse(
        val journalpostId: String,
        val journalstatus: String,
        val melding: String? = null,
        val journalpostferdigstilt: Boolean,
        val dokumentInfoId: String? = null

){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}
