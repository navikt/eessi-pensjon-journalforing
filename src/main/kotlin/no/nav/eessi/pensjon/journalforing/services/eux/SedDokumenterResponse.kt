package no.nav.eessi.pensjon.journalforing.services.eux


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class SedDokumenterResponse(
        val sed: Dokument,
        val vedlegg: List<Dokument>?
){
    companion object {
        private val mapper: ObjectMapper = jacksonObjectMapper().configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true)

        fun fromJson(json: String): SedDokumenterResponse = mapper.readValue(json, SedDokumenterResponse::class.java)
    }

    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}

data class Dokument(val filnavn: String,
                    val mimeType: MimeType?,
                    val innhold: String)


enum class MimeType  : Code {
    @JsonProperty("application/pdf")
    PDF {
        override fun toString() = "application/pdf"
        override fun decode() = "PDF"
    },
    PDFA {
        override fun toString() = "application/pdfa"
        override fun decode() = "PDFA"
    },
    @JsonProperty("image/jpg")
    JPG {
        override fun toString() = "image/jpg"
        override fun decode() = "JPG"
    },
    @JsonProperty("image/jpeg")
    JPEG {
        override fun toString() = "image/jpeg"
        override fun decode() = "JPEG"
    },
    @JsonProperty("image/tiff")
    TIFF {
        override fun toString() = "image/tiff"
        override fun decode() = "TIFF"
    },
    @JsonProperty("image/tif")
    TIF {
        override fun toString() = "image/tif"
        override fun decode() = "TIF"
    },
    @JsonProperty("image/png")
    PNG {
        override fun toString() = "image/png"
        override fun decode() = "PNG"
    }
}

interface Code {
    fun decode(): String
}

