package no.nav.eessi.pensjon.services.journalpost

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.json.mapAnyToJson
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.IOException


@JsonIgnoreProperties(ignoreUnknown = true)
class JournalPostResponse(
    val journalpostId: String,
    val journalstatus: String,
    val melding: String? = null,
    val journalpostferdigstilt: Boolean
){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}

class JournalpostRequest(
        val avsenderMottaker: AvsenderMottaker,
        val behandlingstema: String? = null,
        val bruker: Bruker? = null,
        @JsonDeserialize(using = JsonAsStringDeserializer::class)
        @JsonRawValue
        val dokumenter: String, //REQUIRED
        val eksternReferanseId: String? = null,
        val journalfoerendeEnhet: String? = null,
        val journalpostType: JournalpostType, //REQUIRED
        val kanal: String? = "EESSI",
        val sak: Sak? = null,
        val tema: String = "PEN", //REQUIRED
        val tilleggsopplysninger: List<Tilleggsopplysning>? = null,
        val tittel: String //REQUIRED
){
    override fun toString(): String {
        return mapAnyToJson(this,true)
    }
}

enum class JournalpostType: Code {
    UTGAAENDE {
        override fun decode() = "Utgående"
    },
    INNGAAENDE {
        override fun decode() = "Inngående"
    }
}

class Sak(
    val arkivsaksnummer: String, //REQUIRED
    val arkivsaksystem: String //REQUIRED
)

/**
 * Avsender eller mottaker informasjon for personen eller organisasjonen som enten sender eller mottar dokumentet
 *
 * https://confluence.adeo.no/display/BOA/Type%3A+AvsenderMottaker
 */
class AvsenderMottaker(
    val id: String?,
    val idType: IdType?,
    val navn: String?,
    val land: String?
)

enum class IdType {
    FNR,
    ORGNR
}

class Bruker(
    val id: String, //REQUIRED
    val idType: String = "FNR" //REQUIRED
)

class Tilleggsopplysning(
        val nokkel: String, //REQUIRED
        val verdi: String //REQUIRED
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
