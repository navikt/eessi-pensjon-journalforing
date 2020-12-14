package no.nav.eessi.pensjon.models.sed

import com.fasterxml.jackson.annotation.JsonFormat

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
         * Forenklet uthenting forsikret (hovedperson) sin rolle
         */
        fun forsikretRolle(): String? =
                bruker?.firstOrNull()?.person?.rolle

        /**
         * Forenklet uthenting av annen person
         */
        fun annenPerson(): Person? = annenperson?.person

        /**
         * Forenklet uthenting annen person sin identifikator (fnr)
         */
        fun annenPersonIdent(): String? =
                annenperson?.person?.pin?.firstOrNull { it.land == "NO" }?.identifikator

        /**
         * Forenklet uthenting forsikret (hovedperson) sin rolle
         */
        fun annenPersonRolle(): String? = annenperson?.person?.rolle
}

data class Bruker(
        val mor: Foreldre? = null,
        val far: Foreldre? = null,
        val person: Person? = null,

        //H120?
        val bostedsadresse: Adresse? = null,
        val status: BrukerStatus? = null,
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

        // TODO: Could be enum ?
        val rolle: String? = null  //3.1 i P10000
) {
        fun ident(): String? = pin?.firstOrNull { it.land == "NO" }?.identifikator
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
        val type: String? = null
)

//H070
data class Sted(
        val adresse: Adresse? = null
)

data class BrukerStatus(
        val id: String? = null
)

data class Foreldre(
        val person: Person
)

data class BarnItem(
        val mor: Foreldre? = null,
        val person: Person? = null,
        val far: Foreldre? = null
)

data class Ektefelle(
        val mor: Foreldre? = null,
        val person: Person? = null,
        val far: Foreldre? = null,
        val type: String? = null
)

data class Verge(
        val person: Person? = null
)

data class PinLandItem(
        val oppholdsland: String? = null,
        val kompetenteuland: String? = null
)

data class RelasjonAvdodItem(
        val relasjon: String? = null
)

data class StatsborgerskapItem(
        val land: String? = null
)

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

data class Adresse(
        val gate: String? = null,
        val bygning: String? = null,
        val by: String? = null,
        val postnummer: String? = null,
        val region: String? = null,
        val land: String? = null,
        val kontaktpersonadresse: String? = null,
        val datoforadresseendring: String? = null,
        val postadresse: String? = null,
        val startdato: String? = null
)

data class Foedested(
        val by: String? = null,
        val land: String? = null,
        val region: String? = null
)

data class EessisakItem(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val land: String? = null
)

data class Tilbakekreving(
        val anmodning: Anmodning?,
        val feilutbetaling: Feilutbetaling?,
        val status: Status?
) {
        class Status(val type: String?)
}

data class Anmodning(val type: String?)
data class Feilutbetaling(val ytelse: Ytelse?) {
        class Ytelse(val type: String?)
}
