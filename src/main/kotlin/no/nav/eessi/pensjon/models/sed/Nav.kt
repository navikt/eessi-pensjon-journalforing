package no.nav.eessi.pensjon.models.sed

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonValue

data class Nav(
        @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
        val bruker: List<Bruker>? = null,

        val eessisak: List<EessisakItem>? = null,
        val ektefelle: Ektefelle? = null,
        val barn: List<BarnItem>? = null, //pkt 6 og 8
        val verge: Verge? = null,
        val krav: Krav? = null,

        //X005
        val sak: Navsak? = null,
        //P10000 hvordan få denne til å bli val?
        val annenperson: Bruker? = null,

        //H120
        val endredeforhold: Endredeforhold? = null
) {
        /**
         * Forenklet uthenting forsikret (hovedperson)
         */
        fun forsikret(): Person? = bruker?.firstOrNull()?.person

        /**
         * Forenklet uthenting forsikret (hovedperson) sin identifikator (fnr)
         */
        fun forsikretIdent(): String? =
                bruker?.firstOrNull()?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator

        /**
         * Forenklet uthenting av annen person
         */
        fun annenPerson(): Person? = annenperson?.person

        /**
         * Forenklet uthenting annen person sin identifikator (fnr)
         */
        fun annenPersonIdent(): String? =
                annenperson?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator
}

data class Bruker(
        val person: Person? = null,
        val mor: Person? = null,
        val far: Person? = null,

        val tilbakekreving: Tilbakekreving? = null
) {
        fun ident(): String? = person?.pin?.firstOrNull { it.land == "NO" }?.identifikator
}

data class Person(
        @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
        val pin: List<PinItem>? = null,

        val pinland: PinLandItem? = null, //for H020 og H021
        val pinannen: PinItem? = null,
        val foedselsdato: String? = null,

        val relasjontilavdod: RelasjonAvdodItem? = null, //5.2.5 P2100

        val rolle: Rolle? = null  //3.1 i P10000
) {
        fun ident(): String? = pin?.firstOrNull { it.land == "NO" }?.identifikator
}

enum class Rolle(@JsonValue val kode: String) {
        ETTERLATTE("01"),
        FORSORGER("02"),
        BARN("03");
}

//H121
data class Endredeforhold(
        val bruker: Bruker? = null
)

//X005
data class Navsak(
        val kontekst: Kontekst? = null
)

//X005
data class Kontekst(
        val bruker: Bruker? = null
)

data class Krav(
        val dato: String? = null,
        val type: KravType? = null
)

@Suppress("unused") // val kode (jsonvalue) brukes av jackson
enum class KravType(@JsonValue private val kode: String?) {
        ALDER("01"),
        ETTERLATTE("02"),
        UFORE("03")
}

data class BarnItem(
        val mor: Person? = null,
        val person: Person? = null,
        val far: Person? = null
)

data class Ektefelle(
        val mor: Person? = null,
        val person: Person? = null,
        val far: Person? = null,
        val type: String? = null
)

data class Verge(
        val person: Person? = null
)

data class PinLandItem(
        val oppholdsland: String? = null,
        val kompetenteuland: String? = null
)

data class RelasjonAvdodItem(val relasjon: RelasjonTilAvdod?)

@Suppress("unused")
enum class RelasjonTilAvdod(@JsonValue private val kode: String?) {
        EKTEFELLE("01"),
        PART_I_ET_REGISTRERT_PARTNERSKAP("02"),
        SAMBOER("03"),
        TIDLIGERE_EKTEFELLE("04"),
        TIDLIGERE_PARTNER_I_ET_REGISTRERT_PARTNERSKAP("05"),
        EGET_BARN("06"),
        ADOPTIVBARN("07"),
        FOSTERBARN("08"),
        STEBARN("09"),
        BARNEBARN("10"),
        SØSKEN("11"),
        ANNEN_SLEKTNING("12");

        fun erGjenlevendeBarn(): Boolean = this in listOf(EGET_BARN, ADOPTIVBARN, FOSTERBARN, STEBARN)
}

data class PinItem(
        val institusjonsnavn: String? = null,
        val institusjonsid: String? = null,
        val sektor: String? = null,
        val identifikator: String? = null,  //rename? f.eks personnummer
        val land: String? = null,
        //P2000, P2100, P2200
        val institusjon: Institusjon? = null,

        val oppholdsland: String? = null,
        val kompetenteuland: String? = null
)

data class EessisakItem(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val land: String? = null
)

data class Tilbakekreving(
        val feilutbetaling: Feilutbetaling? = null,
        val status: Status? = null
)

data class Feilutbetaling(val ytelse: Ytelse?)

data class Ytelse(val type: String?)
data class Status(val type: String?)
