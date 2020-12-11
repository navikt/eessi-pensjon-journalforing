package no.nav.eessi.pensjon.models.sed

data class Landspesifikk(
        val norge: Norge? = null
)

data class Norge(
        val alderspensjon: Alderspensjon? = null,
        val etterlatte: Etterlatte? = null,
        val ufore: Ufore? = null
)

data class Ufore(
        val nasjonaliteter: List<Nasjonalitet>? = null,
        val brukerInfo: BrukerInfo? = null,
        val andreRessurserInntektskilder: AndreRessurserInntektskilder? = null,
        val ytelseInfo: YtelseInfo? = null,
        val ektefelleInfo: EktefelleInfo? = null,
        val barnInfo: List<BarnInfo>? = null,
        val tilgjengeligInfo: String? = null,
        val grunn: String? = null
)

data class Alderspensjon(
        val brukerInfo: BrukerInfo? = null,
        val ansettelsesforhold: Ansettelsesforhold? = null,
        val ytelseInfo: YtelseInfo? = null,
        val ektefelleInfo: EktefelleInfo? = null,
        val barnInfo: List<BarnInfo>? = null,
        val pensjonsgrad: String? = null,
        val tilgjengeligInfo: String? = null,
        val grunn: String? = null
)

data class Etterlatte(
        val sosken: Sosken? = null,
        val ansettelsesforhold: Ansettelsesforhold? = null,
        val sokersYtelser: YtelseInfo? = null,
        val avdod: Avdod? = null,
        val tilgjengeligInfo: String? = null,
        val grunn: String? = null
)

data class Arbeidsgiver(
        val ansattIdentifikasjonAvArbeidsgiver: AnsattIdentifikasjonAvArbeidsgiver? = null,
        val arbeidsgiverIdentifikasjonAvArbeidsgiver: ArbeidsgiverIdentifikasjonAvArbeidsgiver? = null //arbeidsgiverIdentifikasjon
)

data class AnsattIdentifikasjonAvArbeidsgiver(
        val ansattIndikator: String? = null,
        val navn: String? = null,
        val adresse: Adresse? = null,
        val arbeidsgiverIndikator: String? = null
)

data class ArbeidsgiverIdentifikasjonAvArbeidsgiver(
        val arbeidsgiverIndikator: String? = null,
        val registreringsNummer: String? = null,
        val personNummer: String? = null,
        val skatteNummer: String? = null,
        val bedriftsRegister: String? = null
)

data class Avdod(
        val nasjonaliteter: List<Nasjonalitet>? = null,
        val pensjonsMottaker: PensjonsMottaker? = null,
        val inntektsgivendeArbeid: String? = null
)

data class Sosken(
        val nasjonaliteter: List<Nasjonalitet>? = null,
        val forsorgelsesplikt: String? = null,
        val arbeidsforhet: String? = null,
        val arbeidsufor: String? = null,
        val soskenNavn: List<SoskenNavn>? = null
)

data class SoskenNavn(
        val navn: String? = null,
        val personnummer: String? = null,
        val borMedSosken: String? = null
)

data class BrukerInfo(
        val borMedEktefelleEllerPartner: String? = null,
        val boddFrahverandreSiden: String? = null,
        val samboer: Samboer? = null,
        val arbeidsAvklaringskurs: String? = null,
        val yrke: String? = null
)

data class Ansettelsesforhold(
        val ansettelsesforholdType: String? = null,
        val lonnsInntekt: List<Belop>? = null,
        val andreInntektskilder: AndreInntektskilder? = null,
        val ingenInntektOppgitt: String? = null,
        val obligatoriskPensjonsDekning: String? = null,
        val inntektsType: String? = null
)

data class YtelseInfo(
        val kontantYtelserSykdom: String? = null,
        val hjelpestonad: String? = null,
        val grunnleggendeYtelser: String? = null,
        val ytelserTilUtdanning: String? = null,
        val forsorgelseBarn: String? = null,
        val frivilligInnskudd: String? = null
)

data class EktefelleInfo(
        val familiestatus: Familiestatus? = null,
        val nasjonalitet: List<Nasjonalitet>? = null,
        val inntektsgivedeArbeid: InntektsgivendeArbeid? = null,
        val arbeidsfor: String? = null,
        val pensjonsmottaker: List<PensjonsMottaker>? = null,
        val ikkeYrkesaktiv: String? = null,
        val andreYtelser: AndreYtelser? = null,
        val andreRessurserInntektskilder: AndreRessurserInntektskilder? = null
)

data class BarnInfo(
        val etternavn: String? = null,
        val fornavn: String? = null,
        val familiestatus: List<Familiestatus>? = null,
        val barnetrygd: Barnetrygd? = null,
        val adresse: Adresse? = null,
        val borMedBeggeForeldre: String? = null,
        val forsikredeForsorgerBarnet: String? = null,
        val barnetBorHosForsikrede: String? = null
)

data class Barnetrygd(
        val barnetrygd: String? = null,
        val typeBarnetrygd: String? = null,
        val belop: List<Belop>? = null
)

data class AndreRessurserInntektskilder(
        val andreRessurserInntektskilder: String? = null,
        val typeAndreRessurserInntektskilder: String? = null,
        val belop: List<Belop>? = null,
        val oppgirIngenInntekt: String? = null,
        val arbeidsgiver: List<Arbeidsgiver>? = null,
        val startDatoAnsettelse: String? = null
)

data class AndreYtelser(
        val andreYtelser: String? = null,
        val andreYtelserType: String? = null,
        val belop: List<Belop>? = null
)

//4.4.5 Pensjonsmottaker
data class PensjonsMottaker(
        val erPensjonsmottaker: String? = null,
        val typePensjon: String? = null,
        val pensjonsnummer: String? = null,
        val institusjonsopphold: Institusjonsopphold? = null,
        val startDatoYtelse: String? = null,
        val sluttDatoYtelse: String? = null,
        val pensjonBasertPa: String? = null
)

data class Institusjonsopphold(
        val land: String? = null,
        val personNummer: String? = null,
        val sektor: String? = null,
        val institusjon: Institusjon? = null,
        val belop: List<Belop>? = null
)

data class InntektsgivendeArbeid(
        val inntektsgivendeArbeid: String? = null,
        val belop: List<Belop>? = null
)

data class Familiestatus(
        val familiestatus: String? = null,
        val datoFamilieStatus: String? = null
)

data class AndreInntektskilder(
        val andreInntektskilderIndikator: String? = null,
        val typeAndreInntektskilder: String? = null,
        val andreInntektskilderBelop: List<Belop>? = null
)

data class Belop(
        val belop: String? = null,
        val valuta: String? = null,
        val belopGjelderFra: String? = null,
        val betalingshyppighet: String? = null,
        val annenBetalingshyppighet: String? = null
)

data class Samboer(
        val boddSammenSiden: String? = null,
        val barnMedSamboer: String? = null,
        val tidligereGiftMedSamboer: String? = null,
        val nasjonaliteter: List<Nasjonalitet>? = null,
        val forsikringMotArbeidsuforhet: String? = null
)

data class Nasjonalitet(
        val nasjonalitet: String? = null
)
