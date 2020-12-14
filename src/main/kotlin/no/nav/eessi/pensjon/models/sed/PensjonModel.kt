package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.klienter.journalpost.Sak
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Periode

class MeldingOmPensjon(
        val melding: String?,
        val pensjon: Pensjon
)

data class Pensjon(
        val sak: Sak? = null,
        val gjenlevende: Bruker? = null,
        val bruker: Bruker? = null,

        //p2000 - p2200
        val ytelser: List<YtelserItem>? = null,
        val forespurtstartdato: String? = null,

        //P5000
        val medlemskapAnnen: List<MedlemskapItem>? = null,
        val medlemskapTotal: List<MedlemskapItem>? = null,
        val medlemskap: List<MedlemskapItem>? = null,
        val trygdetid: List<MedlemskapItem>? = null,
        val institusjonennaaikkesoektompensjon: List<String>? = null,
        val utsettelse: List<Utsettelse>? = null,

        //P2000, P2100, P2200, P8000?? Noen men ikke alle
        val vedlegg: List<String>? = null,

        val vedleggandre: String? = null,
        val angitidligstdato: String? = null,

        val kravDato: Krav? = null, //kravDato pkt. 9.1 P2000
        val antallSokereKjent: String? = null, //P2100 11.7

        //P8000
        val anmodning: AnmodningOmTilleggsInfo? = null,

        //P7000
        val samletVedtak: SamletMeldingVedtak? = null,

        //P10000 //P9000
        val merinformasjon: Merinformasjon? = null,

        //P12000
        val anmodning13000verdi: String? = null
)

//P10000 -- innholder ytteligere informasjon om person (se kp. 5.1) som skal oversendes tilbake
data class Merinformasjon(
        val referansetilperson: String? = null, // kp.5.1 visr til om følgende informasjon gjelder hovedperson eller annenperson. - 01 - hoved -02 er annen

        val person: Person? = null,
        val bruker: Bruker? = null,

        val ytelser: List<YtelserItem>? = null
)

//P10000
data class Ytelsesdatoer(
        val annenytelse: String? = null,
        val datotyper: Datotyper? = null,
        val personkravstatus: String? = null
)

//P10000
data class Datotyper(
        val startdatoutbetaling: String? = null,
        val sluttdatoutbetaling: String? = null,

        val startdatoforstansytelse: String? = null,
        val sluttdatorettytelse: String? = null,

        val startdatoretttilytelser: String? = null,
        val sluttdatoredusertytelse: String? = null,

        val startdatoredusertytelse: String? = null,
        val sluttdatoforstansiytelser: String? = null,

        val datoavslagpaaytelse: String? = null,
        val datokravytelse: String? = null
)

//P7000
data class SamletMeldingVedtak(
        val avslag: List<PensjonAvslagItem>? = null,
        val vedtaksammendrag: String? = null,
        val startdatoPensjonsRettighet: String? = null,  // 4.1.5
        val reduksjonsGrunn: String? = null    // 4.1.7
)

//P7000-5
data class PensjonAvslagItem(
        val pensjonType: String? = null,
        val begrunnelse: String? = null, //5.1
        val dato: String? = null,   //5.2
        val datoFrist: String? = null,
        val pin: PinItem? = null,
        val adresse: String? = null
)

//Institusjon
data class Institusjon(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val saksnummer: String? = null,
        val sektor: String? = null,
        val land: String? = null,
        val pin: String? = null,
        val personNr: String? = null,
        val innvilgetPensjon: String? = null,  // 4.1.3.
        val utstedelsesDato: String? = null,  //4.1.4.
        val startdatoPensjonsRettighet: String? = null  //4.1.5
)

//P8000
data class AnmodningOmTilleggsInfo(
        val relasjonTilForsikretPerson: String? = null, //4.1.1
        val beskrivelseAnnenSlektning: String? = null, // 4.2.1
        val referanseTilPerson: String? = null,
        val anmodningPerson: AnmodningOmPerson? = null, //10.
        val anmodningOmBekreftelse: AnmodningOmBekreftelse? = null, //9.
        val informasjon: AnmodningOmInformasjon? = null, //8
        val ytterligereInfoOmDokumenter: String? = null, //6.1
        val begrunnKrav: String? = null,
        val seder: List<SedAnmodningItem>? = null,
        val personAktivitet: List<PersonAktivitetItem>? = null,
        val personAktivitetSom: List<PersonAktivitetSomItem>? = null,
        val personInntekt: List<PersonensInntekt>? = null,
        val annenInfoOmYtelse: String? = null    //8.5
)

// 8.4
data class PersonensInntekt(
        val oppgiInntektFOM: String? = null,
        val personInntekt: List<PersonInntektItem>? = null
)

data class AnmodningOmInformasjon(
        val generellInformasjon: List<GenerellInfo>? = null, // 8.1
        val infoOmPersonYtelse: List<InfoOmPersonYtelse>? = null, // 8.2
        val annenEtterspurtInformasjon: String? = null,
        val begrunnelseKrav: String? = null //8.6
)

// Alt i denne blokken er 8.2
data class InfoOmPersonYtelse(
        val informerOmPersonFremsattKravEllerIkkeEllerMottattYtelse: String? = null, // 8.2.1
        val annenYtelse: String? = null, //8.2.2.1
        val sendInfoOm: List<SendInfoOm>? = null // 8.2.3.1
)

data class SendInfoOm(
        val sendInfoOm: String? = null,
        val annenInfoOmYtelser: String? = null // 8.2.3.2.1
)

data class AnmodningOmPerson(
        val egenerklaering: String? = null, //10.1
        val begrunnelseKrav: String? = null //10.2
)

data class AnmodningOmBekreftelse(
        val bekreftelseInfo: String? = null,
        val bekreftelsesGrunn: String? = null //9.2
)

data class GenerellInfo(
        val generellInfoOmPers: String? = null
)

data class SedAnmodningItem(
        val begrunnelse: String? = null,
        val andreEtterspurteSEDer: String? = null,
        val sendFolgendeSEDer: List<String>? = null //7.1.1
)

data class PersonAktivitetItem(
        val persAktivitet: String? = null
)

data class PersonAktivitetSomItem(
        val persAktivitetSom: String? = null
)

data class PersonInntektItem(
        val persInntekt: String? = null
)

//P2000
data class Utsettelse(
        val institusjonsnavn: String? = null,
        val institusjonsid: String? = null,
        val land: String? = null,
        val institusjon: Institusjon? = null,
        val tildato: String? = null
)

//P5000
data class MedlemskapItem(
        val relevans: String? = null,
        val ordning: String? = null,
        val land: String? = null,
        val sum: TotalSum? = null,
        val yrke: String? = null,
        val gyldigperiode: String? = null,
        val type: String? = null,
        val beregning: String? = null,
        val informasjonskalkulering: String? = null,
        val periode: Periode? = null
)

//P5000
data class Dager(
        val nr: String? = null,
        val type: String? = null
)

//P5000
data class TotalSum(
        val kvartal: String? = null,
        val aar: String? = null,
        val uker: String? = null,
        val dager: Dager? = null,
        val maaneder: String? = null
)

//P2000 - P2200 - //P10000
data class YtelserItem(
        val annenytelse: String? = null,
        val pin: PinItem? = null,
        val ytelse: String? = null,
        val status: String? = null,

        val ytelsestype: String? = null
)
