package no.nav.eessi.pensjon.journalforing.services.journalpost

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.journalforing.utils.mapAnyToJson

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
    val avsenderMottaker: AvsenderMottaker? = null,
    val behandlingstema: String? = null,
    val bruker: Bruker? = null,
    val dokumenter: List<Dokument>, //REQUIRED
    val eksternReferanseId: String? = null,
    val journalfoerendeEnhet: String? = null,
    val journalpostType: String = "UTGAAENDE", //REQUIRED
    val kanal: String? = null,
    val sak: Sak? = null,
    val tema: String = "PEN", //REQUIRED
  //  val tilleggsopplysninger: List<Tilleggsopplysninger>? = null,
    val tittel: String //REQUIRED
){
    override fun toString(): String {
        return mapAnyToJson(this,true)
    }
}

data class Dokument(
    val brevkode: String? = null,
    val dokumentKategori: String? = "SED",
    val dokumentvarianter: List<Dokumentvarianter>, //REQUIRED
    val tittel: String? = null
)

data class Dokumentvarianter(
    val filtype: String, //REQUIRED
    val fysiskDokument: String, //REQUIRED
    val variantformat: String = "ARKIV" //REQUIRED
)

data class Sak(
    val arkivsaksnummer: String, //REQUIRED
    val arkivsaksystem: String //REQUIRED
)

data class AvsenderMottaker(
    val id: String? = null,
    val land: String? = null,
    val navn: String //REQUIRED
)

//data class Tilleggsopplysninger(
//    val nokkel: String, //REQUIRED
//    val verdi: String //REQUIRED
//)

data class Bruker(
    val id: String, //REQUIRED
    val idType: String = "FNR" //REQUIRED
)

// https://confluence.adeo.no/display/BOA/Behandlingstema
enum class Behandlingstema : Code {
    GJENLEVENDEPENSJON {
        override fun toString() = "ab0011"
        override fun decode() = "Gjenlevendepensjon"
    },
    ALDERSPENSJON {
        override fun toString() = "ab0254"
        override fun decode() = "Alderspensjon"
    },
    UFOREPENSJON {
        override fun toString() = "ab0194"
        override fun decode() = "Uførepensjon"
    }
}

// https://confluence.adeo.no/display/BOA/Tema
enum class Tema : Code {
    PENSJON {
        override fun toString() = "PEN"
        override fun decode() = "Pensjon"
    },
    UFORETRYGD {
        override fun toString() = "UFO"
        override fun decode() = "Uføretrygd"
    }
}

enum class BUCTYPE (val BEHANDLINGSTEMA: String, val TEMA: String){
    P_BUC_01(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_02(Behandlingstema.GJENLEVENDEPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_03(Behandlingstema.UFOREPENSJON.toString(), Tema.UFORETRYGD.toString()),
    P_BUC_04(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_05(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_06(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_07(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_08(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_09(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString()),
    P_BUC_10(Behandlingstema.ALDERSPENSJON.toString(), Tema.PENSJON.toString())
}

interface Code {
    fun decode(): String
}