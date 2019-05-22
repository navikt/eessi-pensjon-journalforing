package no.nav.eessi.pensjon.journalforing.services.eux


import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper

data class SedDokumenterResponse(
        val sed: Dokument,
        val vedlegg: List<Dokument>?
){
    override fun toString(): String {
        val mapper = jacksonObjectMapper()
        return mapper.writeValueAsString(this)
    }
}

data class Dokument(val filnavn: String,
                    val mimeType: MimeType,
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
    }
    ,
    @JsonProperty("image/tiff")
    TIFF {
        override fun toString() = "image/tiff"
        override fun decode() = "TIFF"
    }
}

interface Code {
    fun decode(): String
}

