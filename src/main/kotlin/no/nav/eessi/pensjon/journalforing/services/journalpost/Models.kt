package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.annotation.JsonRawValue
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.json.mapAnyToJson
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.core.TreeNode
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import java.io.IOException



data class JournalPostResponse(
    val journalpostId: String,
    val journalstatus: String,
    val melding: String? = null
){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}

data class JournalpostRequest(
        val avsenderMottaker: AvsenderMottaker,
        val behandlingstema: String? = null,
        val bruker: Bruker? = null,
        @JsonDeserialize(using = JsonAsStringDeserializer::class)
        @JsonRawValue
        val dokumenter: String, //REQUIRED
        val eksternReferanseId: String? = null,
        val journalfoerendeEnhet: String? = null,
        val journalpostType: JournalpostType, //REQUIRED
        val kanal: String? = null,
        val sak: Sak? = null,
        val tema: String = "PEN", //REQUIRED
  //  val tilleggsopplysninger: List<Tilleggsopplysninger>? = null,
        val tittel: String //REQUIRED
){
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

        fun fromJson(json: String): JournalpostRequest = mapper.readValue(json, JournalpostRequest::class.java)
    }

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

data class Sak(
    val arkivsaksnummer: String, //REQUIRED
    val arkivsaksystem: String //REQUIRED
)

/**
 * Avsender eller mottaker informasjon for personen eller organisasjonen som enten sender eller mottar dokumentet
 */
data class AvsenderMottaker(
    val id: String, //REQUIRED
    val idType: IdType, //REQUIRED
    val navn: String //REQUIRED
)

enum class IdType {
    FNR,
    ORGNR,
    HPRNR,
    UTL_ORG
}

data class Bruker(
    val id: String, //REQUIRED
    val idType: String = "FNR" //REQUIRED
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