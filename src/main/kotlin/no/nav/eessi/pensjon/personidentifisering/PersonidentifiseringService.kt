package no.nav.eessi.pensjon.personidentifisering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.sed.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.personidentifisering.helpers.FodselsdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSok
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.FORSIKRET
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.GJENLEVENDE
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonidentifiseringService(
    @Autowired private val personSok: PersonSok,
    @Suppress("SpringJavaInjectionPointsAutowiringInspection") private val personService: PersonService,
) {
    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(H121, H120, H070)

    fun validateIdentifisertPerson(
        identifisertPerson: IdentifisertPersonPDL,
        hendelsesType: HendelseType
    ): IdentifisertPersonPDL? {
        val personRelasjon = identifisertPerson.personRelasjon
        val sedFnr = personRelasjon?.fdato
        val pdlFdato = identifisertPerson.fdato
        val fnr = personRelasjon?.fnr

        return if (hendelsesType == MOTTATT) {
            val isFnrDnrFdatoLikSedFdato = if (personRelasjon?.fnr?.erNpid != true) {
                personRelasjon?.isFnrDnrSinFdatoLikSedFdato()
            } else {
                pdlFdato == sedFnr
            }
            if (isFnrDnrFdatoLikSedFdato == true) {
                logger.info(
                    "valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${fnr?.value?.substring(0, 6)}, sed-fdato: $sedFnr, $hendelsesType"
                )
                validerCounter("successful")
                identifisertPerson
            } else {
                logger.warn(
                    "valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${fnr?.value?.replaceRange(7, fnr.value.length, "★".repeat(fnr.value.length-7))}, sed-fdato: $sedFnr, $hendelsesType"
                )
                validerCounter("failed")
                null
            }
        } else {
            identifisertPerson
        }
    }

    private fun validerCounter(typeValue: String) {
        try {
            Metrics.counter("validerPerson", "hendelse", "MOTTATT", "type", typeValue).increment()
        } catch (ex: Exception) {
            logger.warn("Metrics feilet på validerPerson value: $typeValue")
        }
    }

    fun hentIdentifisertPerson(
        bucType: BucType,
        sedType: SedType?,
        hendelsesType: HendelseType,
        rinaDocumentId: String,
        identifisertePersoner: List<IdentifisertPersonPDL>,
        potensiellePersonRelasjoner: List<SEDPersonRelasjon>
    ): IdentifisertPersonPDL? {

        val identifisertPerson = try {
            identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
        } catch (fppbe: FlerePersonPaaBucException) {
            logger.warn("Flere personer funnet i $bucType, returnerer null")
            //flere personer på buc return null for id og fordeling
            return null
        }

        if (identifisertPerson != null) {
            return validateIdentifisertPerson(identifisertPerson, hendelsesType)
        }

        logger.warn("Klarte ikke å finne identifisertPerson, prøver søkPerson")
        personSok.sokPersonEtterFnr(potensiellePersonRelasjoner, rinaDocumentId, bucType, sedType, hendelsesType)
            ?.let { personRelasjon -> return hentIdentifisertPerson(personRelasjon, hendelsesType) }

        return null
    }

    fun hentIdentifisertePersoner(
        alleSediBuc: List<Pair<String, SED>>,
        bucType: BucType,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>,
        hendelsesType: HendelseType,
        rinaDocumentId: String
    ): List<IdentifisertPersonPDL> {

        val distinctByPotensielleSEDPersonRelasjoner = potensielleSEDPersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }
        logger.info("Forsøker å identifisere personer ut fra følgende SED: ${distinctByPotensielleSEDPersonRelasjoner.map { "Relasjon: ${it.relasjon}, SED: ${it.sedType}" }}, BUC: $bucType")

        return distinctByPotensielleSEDPersonRelasjoner
            .mapNotNull { relasjon -> hentIdentifisertPerson(relasjon, hendelsesType) }
            .distinctBy { it.aktoerId }
    }

    fun hentIdentifisertPerson(
        personRelasjon: SEDPersonRelasjon, hendelsesType: HendelseType
    ): IdentifisertPersonPDL? {

        return try {
            val valgtFnr = personRelasjon.fnr?.value

            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            logger.debug("Henter person med fnr. $valgtFnr fra PDL")
            val person = personService.hentPerson(Ident.bestemIdent(valgtFnr))

            person?.let {
                populerIdentifisertPerson(
                    person,
                    personRelasjon,
                    hendelsesType
                )
            }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(
        person: Person,
        sedPersonRelasjon: SEDPersonRelasjon,
        hendelsesType: HendelseType
    ): IdentifisertPersonPDL {
        logger.debug("Populerer IdentifisertPerson med data fra PDL hendelseType: $hendelsesType")

        val personNavn = person.navn?.run { "$fornavn $etternavn" }
        val identer = person.identer
        val aktoerId = identer.firstOrNull { it.gruppe == AKTORID }?.ident ?: ""
        val personFnrEllerNpid = identer.firstOrNull { it.gruppe == FOLKEREGISTERIDENT || it.gruppe == NPID }?.ident
        val geografiskTilknytning = person.geografiskTilknytning?.gtKommune ?: person.geografiskTilknytning?.gtBydel
        val landkode = person.landkode()
        val newPersonRelasjon = sedPersonRelasjon.copy(fnr = Fodselsnummer.fra(personFnrEllerNpid))

        return IdentifisertPersonPDL(
            aktoerId = aktoerId,
            landkode = landkode,
            geografiskTilknytning = geografiskTilknytning,
            personRelasjon = newPersonRelasjon,
            personNavn = personNavn,
            fdato = person.foedsel?.foedselsdato
        )
    }

    fun finnesPersonMedAdressebeskyttelseIBuc(alleSediBuc: List<Pair<String, SED>>): Boolean {
        val alleSedTyper = alleSediBuc.map { it.second.type }.toJson()
        logger.info("Leter etter personer med adressebeskyttelse i : $alleSedTyper")
        val fnr = alleSediBuc.flatMap { SedFnrSok.finnAlleFnrDnrISed(it.second) }
        val gradering =
            listOf(AdressebeskyttelseGradering.STRENGT_FORTROLIG_UTLAND, AdressebeskyttelseGradering.STRENGT_FORTROLIG)

        return personService.harAdressebeskyttelse(fnr, gradering)
            .also { logger.debug("Finnes adressebeskyttet person: $it") }
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(
        identifisertePersoner: List<IdentifisertPersonPDL>,
        bucType: BucType,
        sedType: SedType?,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>
    ): IdentifisertPersonPDL? {
        logger.info("Antall identifisertePersoner : ${identifisertePersoner.size} ")

        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
        if (forsikretPerson != null)
            return forsikretPerson

        return when {
            identifisertePersoner.isEmpty() -> null
            bucType == R_BUC_02 -> identifisertePersoner.first().apply { personListe = identifisertePersoner }
            bucType == P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == GJENLEVENDE }
            bucType == P_BUC_05 -> {
                val erGjenlevendeRelasjon = potensielleSEDPersonRelasjoner.any { it.relasjon == GJENLEVENDE }
                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
            }

            bucType == P_BUC_10 -> {
                val erGjenlevendeYtelse = potensielleSEDPersonRelasjoner.any { it.saktype == GJENLEV }

                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeYtelse)
            }

            bucType == P_BUC_07 && (identifisertePersoner.size > 1) -> {
                identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == GJENLEVENDE }
            }

            bucType == P_BUC_07 -> identifisertePersoner.firstOrNull()

            //buc_01,buc_03 hvis flere enn en forsikret person så sendes til id_og_fordeling
            bucType == P_BUC_01 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()
            bucType == P_BUC_03 && (identifisertePersoner.size > 1) -> throw FlerePersonPaaBucException()

            identifisertePersoner.size == 1 -> identifisertePersoner.first()
            else -> {
                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }

    //felles for P_BUC_05 og P_BUC_10
    private fun utvelgerPersonOgGjenlev(
        identifisertePersoner: List<IdentifisertPersonPDL>,
        erGjenlevende: Boolean
    ): IdentifisertPersonPDL? {
        val forsikretPerson = identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == FORSIKRET }
        val gjenlevendePerson = identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == GJENLEVENDE }
        logger.info("forsikretAktoerid: ${forsikretPerson?.aktoerId}, gjenlevAktoerid: ${gjenlevendePerson?.aktoerId}, harGjenlvRelasjon: $erGjenlevende")

        return when {
            gjenlevendePerson != null -> gjenlevendePerson
            erGjenlevende -> null
            else -> {
                forsikretPerson?.apply {
                    personListe = identifisertePersoner.filterNot { it.personRelasjon?.relasjon == FORSIKRET }
                }
            }
        }
    }

    /**
     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
     */
    private fun brukForsikretPerson(
        sedType: SedType?,
        identifisertePersoner: List<IdentifisertPersonPDL>
    ): IdentifisertPersonPDL? {
        if (sedType in brukForikretPersonISed) {
            logger.info("Henter ut forsikret person fra følgende SED $sedType")
            return identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == FORSIKRET }
        }
        return null
    }

    /**
     * Ser første etter fødselsdato som matcher fnr til identifisert person
     * Sjekker alle SEDer, inkl kansellerte
     *
     */
    fun hentFodselsDato(
        identifisertPerson: IdentifisertPerson?, seder: List<SED>, kansellerteSeder: List<SED>): LocalDate? {

        if( identifisertPerson?.personRelasjon?.fnr == null ){
            return FodselsdatoHelper.fdatoFraSedListe(seder, kansellerteSeder)
        }

        return seder.plus(kansellerteSeder)
            .filter { it.type.kanInneholdeIdentEllerFdato() }
            .mapNotNull { FodselsdatoHelper.filterFodselsdato(it) }
            .firstOrNull { it == identifisertPerson.personRelasjon?.fdato }
    }
}

class FlerePersonPaaBucException() : Exception()



