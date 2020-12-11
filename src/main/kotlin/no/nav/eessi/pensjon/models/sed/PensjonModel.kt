package no.nav.eessi.pensjon.models.sed

class MeldingOmPensjon(
        val melding: String?,
        val pensjon: Pensjon
)

data class Pensjon(
        val reduksjon: List<ReduksjonItem>? = null,
        val vedtak: List<VedtakItem>? = null,
        val sak: Sak? = null,
        val gjenlevende: Bruker? = null,
        val bruker: Bruker? = null,
        val tilleggsinformasjon: Tilleggsinformasjon? = null,

        //p2000 - p2200
        val ytterligeinformasjon: String? = null,
        val etterspurtedokumenter: String? = null,
        val ytelser: List<YtelserItem>? = null,
        val forespurtstartdato: String? = null,

        //P3000
        val landspesifikk: Landspesifikk? = null,

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

        //P110000
        val requestForPensionAmount: RequestForPensionAmount? = null,

        //P12000
        val anmodning13000verdi: String? = null,
        //P13000
        val pensjoninfo: List<PensjoninfoItem?>? = null,
        val andrevedlegg: String? = null,
        val foresporsel: Foresporsel? = null,
        val pensjoninfotillegg: Pensjoninfotillegg? = null
)

//P10000 -- innholder ytteligere informasjon om person (se kp. 5.1) som skal oversendes tilbake
data class Merinformasjon(
        val referansetilperson: String? = null, // kp.5.1 visr til om følgende informasjon gjelder hovedperson eller annenperson. - 01 - hoved -02 er annen

        val infoskolegang: List<InfoskolegangItem?>? = null,
        val overfoertedok: Overfoertedok? = null,
        val tilbaketrekkingpensjonskrav: List<TilbaketrekkingpensjonskravItem?>? = null,

        val yrkesaktiv: List<YrkesaktivItem?>? = null,

        val egenerklaering: Egenerklaering? = null,

        val infoarbeidsledig: List<InfoarbeidsledigItem?>? = null,

        val person: Person? = null, //fjenws...

        val bruker: Bruker? = null, //la stå?


        val livdoedinfo: Livdoedinfo? = null,

        val aktivitetsinfo: List<AktivitetsinfoItem?>? = null,
        val aktivitetsinfotilleggsopplysning: String? = null,

        val infoinntekt: List<InfoinntektItem>? = null,

        val relasjonforsoerget: Relasjonforsorget? = null, // endres i eux.
        val kravomgjennomgang: Kravomgjennomgang? = null,

        //merinfo, tillegginformajon for P10000
        val tilleggsinformasjon: String? = null,

        val ytelser: List<YtelserItem>? = null

)

//P11000
data class RequestForPensionAmount(
        val additionalInformation: String? = null,
        val fixedPeriodEndDate: String? = null,
        val requestForAmountType: String? = null,
        val listOfAttachments: String? = null,
        val fixedPeriodStartDate: String? = null,
        val referenceToPersonNamedInSection: String? = null
)

//P12000
data class Pensjonsavslag(
        val tekstfelt: String? = null,
        val pensjonstype: String? = null
)

data class Betalingsdetaljer(
        val pensjonstype: String? = null,
        val arbeidstotal: String? = null,
        val valuta: String? = null,
        val fradato: String? = null,
        val betaldato: String? = null,
        val effektueringsdato: String? = null,
        val basertpaa: String? = null,
        val bosattotal: String? = null,
        val belop: String? = null,
        val annenutbetalingshyppighet: String? = null,
        val utbetalingshyppighet: String? = null
)

data class Pensjonsopphoring(
        val pensjonstype: String? = null,
        val tekstfelt: String? = null
)

//P12000-P13000
data class PensjoninfoItem(
        val pensjonsavslag: Pensjonsavslag? = null,
        val betalingsdetaljer: Betalingsdetaljer? = null,
        val pensjonsopphoring: Pensjonsopphoring? = null,
        val tillegg: Tillegg? = null
)

data class Foresporsel(
        val referanseTilPerson: String? = null
)

data class Pensjoninfotillegg(
        val endret: List<EndretItem?>? = null,
        val avslagsgrunn: String? = null,
        val opphoraarsak: String? = null,
        val opphorsdato: String? = null,
        val sluttdato: String? = null
)

data class EndretItem(
        val endringsdato: String? = null,
        val belopetterendring: String? = null,
        val belopforendring: String? = null
)

//P13000
data class Tillegg(

        val betalingsfradato: String? = null,
        val valuta: String? = null,
        val betalingstildato: String? = null,
        val belop: String? = null,
        val annenutbetalingshyppighet: String? = null,
        val effektuertfradato: String? = null,
        val utbetalingshyppighet: String? = null
)

data class Egenerklaering(
        val egenerklaering: String? = null,
        val dato: String? = null
)

//P10000 //P9000
data class YrkesaktivItem(
        val startdato: String? = null,
        val yrkesstatus: String? = null,
        val sluttdato: String? = null,

        val yrke: String? = null,
        val ansettelseforhold: String? = null,
        val timerpruke: String? = null
)

//P10000
data class Ytelsesdatoer(
        val annenytelse: String? = null,
        val datotyper: Datotyper? = null,
        val personkravstatus: String? = null,
        val beloep: List<BeloepItem>? = null
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

//P10000 //P9000
data class InfoskolegangItem(
        val typeskolegang: String? = null,
        val startdatoskolegang: String? = null,
        val sluttdatoskolegang: String? = null,
        val skolenavn: String? = null,
        val merinfoomskolegang: String? = null
)

//P10000
data class Overfoertedok(
        val vedlagtedok: List<String?>? = null,
        val informasjonomdok: String? = null
)

//P10000
data class TilbaketrekkingpensjonskravItem(
        val landkode: String? = null,
        val datofortilbakekalling: String? = null,
        val kommentar: String? = null
)

//P10000
data class Kravomgjennomgang(
        //Art482RegECNo9872009
        val vedlagtkravomgjennomgangfremsatt: String? = null,
        val framsettelsesdatokrav: String? = null,
        val p7000datert: String? = null
)

//P10000
data class InfoinntektItem(

        val inntektellerikke: String? = null, //16.1.1
        val inntektskilde: String? = null, //16.1.2

        val startdatomottakinntekt: String? = null, //16.1.3
        val sluttdatomottakinntekt: String? = null, //16.1.4

        val inntektbelop: List<InntektBelopItem>? = null,

        val ytterligereinfoinntekt: String? = null, //16.1.6
        val tilleggsytelserutbetalingeritilleggtilinntekt: String? = null //16.1.7
)

//P10000
data class InntektBelopItem(
        val beloepgjeldendesiden: String? = null,
        val betalingshyppighet: String? = null,
        val annenbetalingshyppighet: List<String?>? = null,
        val valuta: String? = null,
        val beloep: String? = null
)

//P10000
data class Relasjonforsorget(
        val relasjonforsikretperson: String? = null,
        val beskrivelseannenslektning: String? = null
)

//P10000
data class Livdoedinfo(
        val dodsdato: String? = null,
        val by: String? = null,
        val land: String? = null,
        val lever: String? = null,
        val region: String? = null
)

//P10000
data class AktivitetsinfoItem(
        val antalltimer: String? = null,
        val startdato: String? = null,
        val sted: String? = null,
        val ansattype: String? = null,
        val sluttdato: String? = null
)

//P10000
data class InfoarbeidsledigItem(
        val startdato: String? = null,
        val arbeidsledig: String? = null,
        val sluttdato: String? = null
)

//P7000
data class SamletMeldingVedtak(
        val avslag: List<PensjonAvslagItem>? = null,
        val vedtaksammendrag: String? = null,
        val tildeltepensjoner: TildeltePensjoner? = null,
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
        val periode: Periode? = null,
        val enkeltkrav: KravtypeItem? = null

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
        val totalbruttobeloeparbeidsbasert: String? = null,
        val institusjon: Institusjon? = null,
        val pin: PinItem? = null,
        val startdatoutbetaling: String? = null,
        val mottasbasertpaa: String? = null,
        val mottasbasertpaaitem: List<MottasBasertPaaItem>? = null,
        val ytelse: String? = null,
        val totalbruttobeloepbostedsbasert: String? = null,
        val startdatoretttilytelse: String? = null,
        val beloep: List<BeloepItem>? = null,
        val sluttdatoretttilytelse: String? = null,
        val sluttdatoutbetaling: String? = null,
        val status: String? = null,
        val ytelseVedSykdom: String? = null, //7.2 //P2100

        //P10000
        val annenbetalingshyppighet: String? = null,
        val datotyper: Datotyper? = null,
        val anneninfoomytelsertekst: String? = null,
        val tilleggsytelserutbetalingitilleggtilpensjon: String? = null,
        val ytelsesdatoer: Ytelsesdatoer? = null,
        val ytelsestype: String? = null

)

data class MottasBasertPaaItem(
        val verdi: String? = null,
        val totalbruttobeloepbostedsbasert: String? = null,
        val totalbruttobeloeparbeidsbasert: String? = null
)

data class BeloepItem(
        val annenbetalingshyppighetytelse: String? = null,
        val betalingshyppighetytelse: String? = null,
        val valuta: String? = null,
        val beloep: String? = null,
        val gjeldendesiden: String? = null
)

data class Sak(
        val artikkel54: String? = null,
        val reduksjon: List<ReduksjonItem>? = null,
        val kravtype: List<KravtypeItem>? = null,
        val enkeltkrav: KravtypeItem? = null
)

data class KravtypeItem(
        val datoFrist: String? = null,
        val krav: String? = null
)

data class VedtakItem(
        val trekkgrunnlag: List<String>? = null,
        val mottaker: List<String>? = null,
        val grunnlag: Grunnlag? = null,
        val begrunnelseAnnen: String? = null,
        val artikkel: String? = null,
        val virkningsdato: String? = null,
        val ukjent: Ukjent? = null,
        val type: String? = null,
        val resultat: String? = null,
        val beregning: List<BeregningItem>? = null,
        val avslagbegrunnelse: List<AvslagbegrunnelseItem>? = null,
        val kjoeringsdato: String? = null,
        val basertPaa: String? = null,
        val basertPaaAnnen: String? = null,
        val delvisstans: Delvisstans? = null
)

data class Tilleggsinformasjon(
        val annen: Annen? = null,
        val anneninformation: String? = null,
        val saksnummer: String? = null,
        val person: Person? = null,
        val dato: String? = null,
        val andreinstitusjoner: List<AndreinstitusjonerItem>? = null,
        val saksnummerAnnen: String? = null,
        val artikkel48: String? = null,
        val opphoer: Opphoer? = null,
        val tilleggsopplysning: String? = null,

        //P9000
        val p8000: RefP8000? = null,
        val bekreftelseSed: List<BekreftelseSedItem>? = null,

        val ikkeyrkesaktiv: String? = null,
        val arbeidsledig: String? = null,
        val negativtsvar: Negativtsvar? = null,

        val vedlegginfo: String? = null,
        val vedlegg: List<VedleggItem>? = null,

        val yrkesaktivitet: Yrkesaktivitet? = null
)

data class VedleggItem(
        val dokument: String? = null
)

data class BekreftelseSedItem(
        val aarsak: String? = null,
        val p8000ref: String? = null,
        val grunn: String? = null,
        //verder? 01,02 ??
        val info: String? = null
)

//P9000
data class RefP8000(
        //verdier? 01, 02, 03???
        val henvisningperson: String? = null,
        val dato: String? = null
)

//P9000
data class Negativtsvar(
        val aarsakgrunn: String? = null,
        val aarsakannen: String? = null,
        val aarsakikkesendsed: String? = null,
        val dokument: String? = null,
        val informasjon: String? = null,
        val bekreftelseinformasjon: String? = null,
        val sed: String? = null
)

data class Yrkesaktivitet(
        val ingenaktivtetinformasjon: String? = null,
        val tilleggsopplysning: String? = null
)

data class AndreinstitusjonerItem(
        val institusjonsid: String? = null,
        val institusjonsnavn: String? = null,
        val institusjonsadresse: String? = null,
        val postnummer: String? = null,
        val bygningsnr: String? = null,
        val land: String? = null,
        val region: String? = null,
        val poststed: String? = null
)

data class Annen(
        val institusjonsadresse: Institusjonsadresse? = null
)

data class Delvisstans(
        val utbetaling: Utbetaling? = null,
        val indikator: String? = null
)

data class Ukjent(
        val beloepBrutto: BeloepBrutto? = null
)

data class ReduksjonItem(
        val type: String? = null,
        val virkningsdato: List<VirkningsdatoItem>? = null,
        val aarsak: Arsak? = null,
        val artikkeltype: String? = null
)

data class VirkningsdatoItem(
        val startdato: String? = null,
        val sluttdato: String? = null
)

data class Arsak(
        val inntektAnnen: String? = null,
        val annenytelseellerinntekt: String? = null
)

data class Opphoer(
        val dato: String? = null,
        val annulleringdato: String? = null
)

data class Utbetaling(
        val begrunnelse: String? = null,
        val valuta: String? = null,
        val beloepBrutto: String? = null
)

data class Grunnlag(
        val medlemskap: String? = null,
        val opptjening: Opptjening? = null,
        val framtidigtrygdetid: String? = null
)

data class Opptjening(
        val forsikredeAnnen: String? = null
)

data class AvslagbegrunnelseItem(
        val begrunnelse: String? = null,
        val annenbegrunnelse: String? = null
)

data class BeregningItem(
        val beloepNetto: BeloepNetto? = null,
        val valuta: String? = null,
        val beloepBrutto: BeloepBrutto? = null,
        val utbetalingshyppighetAnnen: String? = null,
        val periode: Periode? = null,
        val utbetalingshyppighet: String? = null
)

data class BeloepNetto(
        val beloep: String? = null
)

data class BeloepBrutto(
        val ytelseskomponentTilleggspensjon: String? = null,
        val beloep: String? = null,
        val ytelseskomponentGrunnpensjon: String? = null,
        val ytelseskomponentAnnen: String? = null
)

data class Periode(
        val fom: String? = null,
        val tom: String? = null,
        val extra: String? = null
)

//P7000 4. Tildelte pensjoner
data class TildeltePensjoner(
        val pensjonType: String? = null, //4.1.2
        val vedtakPensjonType: String? = null, //4.1.1
        val tildeltePensjonerLand: String? = null,   //4.1.2.1.1.
        val addressatForRevurdering: String? = null,   //4.1.8.2.1.
        val institusjonPensjon: PensjonsInstitusjon? = null,
        val institusjon: Institusjon? = null
)

data class PensjonsInstitusjon(
        val sektor: String? = null
)

data class Institusjonsadresse(
        val poststed: String? = null,
        val postnummer: String? = null,
        val land: String? = null
)
