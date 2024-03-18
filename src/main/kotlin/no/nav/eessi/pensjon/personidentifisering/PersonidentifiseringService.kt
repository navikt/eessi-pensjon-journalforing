package no.nav.eessi.pensjon.personidentifisering

import io.micrometer.core.instrument.Metrics
import no.nav.eessi.pensjon.eux.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType.GJENLEV
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.oppgaverouting.HendelseType
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.MOTTATT
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.SENDT
import no.nav.eessi.pensjon.personidentifisering.helpers.FodselsdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.PersonSok
import no.nav.eessi.pensjon.personidentifisering.helpers.SedFnrSok
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.Ident
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonidentifiseringService(
    @Autowired private val personSok: PersonSok,
    private val personService: PersonService
) {
    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")
    private val brukForikretPersonISed = listOf(H121, H120, H070)

    fun validateIdentifisertPerson(
        identifisertPerson: IdentifisertPDLPerson,
        hendelsesType: HendelseType
    ): IdentifisertPDLPerson? {
        val personRelasjon = identifisertPerson.personRelasjon
        val sedFdato = personRelasjon?.fdato
        val pdlFdato = identifisertPerson.fdato
        val fnr = personRelasjon?.fnr

        secureLog.info("Validering av identifisert person $identifisertPerson ")
        return when (hendelsesType) {
            MOTTATT -> {
                val isFnrDnrFdatoLikSedFdato = if (personRelasjon?.fnr?.erNpid != true) {
                    personRelasjon?.isFnrDnrSinFdatoLikSedFdato()
                } else pdlFdato == sedFdato

                if (isFnrDnrFdatoLikSedFdato == true) {
                    logger.info("valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${fnr?.value?.substring(0, 6)}, sed-fdato: $sedFdato, $hendelsesType")
                    validerCounter("successful")
                    identifisertPerson
                } else {
                    logger.warn("valider: $isFnrDnrFdatoLikSedFdato, fnr-dato: ${fnr?.value?.replaceRange(7, fnr.value.length, "★".repeat(fnr.value.length-7))}, sed-fdato: $sedFdato, $hendelsesType")
                    validerCounter("failed")
                    null
                }
            }
            SENDT -> {
                identifisertPerson
            }
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
        identifisertePersoner: List<IdentifisertPDLPerson>,
        potensiellePersonRelasjoner: List<SEDPersonRelasjon>
    ): IdentifisertPDLPerson? {

        val identifisertPerson = try {
            identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
        } catch (fppbe: FlerePersonPaaBucException) {
            logger.warn("Flere personer funnet i $bucType, returnerer null")
            return null
        }
        secureLog.info("Identifisert person: $identifisertPerson")
        if (identifisertPerson != null) {
            return validateIdentifisertPerson(identifisertPerson, hendelsesType)
        }

        logger.warn("Klarte ikke å finne identifisertPerson, prøver søkPerson")
        personSok.sokPersonEtterFnr(potensiellePersonRelasjoner, rinaDocumentId, bucType, sedType, hendelsesType)
            ?.let { personRelasjon -> return hentIdentifisertPersonFraPDL(personRelasjon).also {
                secureLog.info("Henter person fra PDL $it")
            } }

        return null
    }

    fun hentIdentifisertPersonFraPDL( personRelasjon: SEDPersonRelasjon ): IdentifisertPDLPerson? {

        return try {
            logger.info("Henter person info fra pdl for relasjon: ${personRelasjon.relasjon}")
            val valgtFnr = personRelasjon.fnr?.value

            if (valgtFnr == null) {
                logger.info("Ingen gyldig ident, går ut av hentIdentifisertPerson!")
                return null
            }

            val person = personService.hentPerson(Ident.bestemIdent(valgtFnr)).also {pdlPerson ->
                secureLog.info("Hent fra PDL person med fnr $valgtFnr gir resultatet: $pdlPerson")
                personRelasjon.sokKriterier
            }

            person?.let { pdlPerson ->
                logger.info("Populerer IdentifisertPerson for ${personRelasjon.relasjon} med data fra PDL sedType: ${personRelasjon.sedType}")

                IdentifisertPDLPerson(
                    aktoerId = pdlPerson.identer.firstOrNull { it.gruppe == AKTORID }?.ident ?: "",
                    landkode = pdlPerson.landkode(),
                    geografiskTilknytning = pdlPerson.geografiskTilknytning?.gtKommune ?: pdlPerson.geografiskTilknytning?.gtBydel,
                    personRelasjon = personRelasjon.copy(fnr = Fodselsnummer.fra(pdlPerson.identer.firstOrNull { it.gruppe == FOLKEREGISTERIDENT || it.gruppe == NPID }?.ident)),
                    personNavn = pdlPerson.navn?.run { "$fornavn $etternavn" },
                    fdato = pdlPerson.foedsel?.foedselsdato,
                    identer = pdlPerson.identer.map { it.ident},
                )
            }.also { logger.info("Legger til identifisert person for aktorid: ${it?.aktoerId}") }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra PDL (ep-personoppslag), fortsetter uten", ex)
            null
        }
    }

    /**
     * Validerer person fra PDL mot søkekriterer
     */
//    fun validerResultatPdlMotSokeKriterier(personNavn: Navn?, sokKriterier: SokKriterier) {
//        personNavn?.let { navn ->
//            if (erSokKriterieOgPdlNavnLikt(sokKriterier, navn)) {
//                secureLog.error("SøkPerson fra PDL gir forskjellig navn; sokKriterier: fornavn: ${sokKriterier.fornavn}, etternavn: ${sokKriterier.etternavn} " +
//                        "navn i SED: fornavn: ${navn.fornavn}, etternavn: ${navn.etternavn}"
//                )
//            }
//        }
//    }


    fun erSokKriterieOgPdlNavnLikt(sokKriterier: SokKriterier, pdlPersonNavn: Navn): Boolean {
        val sokKriterieFornavn = sokKriterier.fornavn.contains(pdlPersonNavn.fornavn, ignoreCase = true)
        val pdlPersonFornavn = pdlPersonNavn.fornavn.contains(sokKriterier.fornavn, ignoreCase = true)
        val sokKriterieEtternavn = sokKriterier.etternavn.contains(pdlPersonNavn.etternavn, ignoreCase = true)
        val pdlPersonEtternavn = pdlPersonNavn.etternavn.contains(sokKriterier.etternavn, ignoreCase = true)
        return (sokKriterieFornavn || pdlPersonFornavn) && (sokKriterieEtternavn || pdlPersonEtternavn)
    }


    fun hentIdentifisertePersoner(sedPersonRelasjoner: List<SEDPersonRelasjon>): List<IdentifisertPDLPerson> =
        sedPersonRelasjoner.distinctBy { relasjon -> relasjon.fnr }
            .mapNotNull { relasjon -> hentIdentifisertPersonFraPDL(relasjon) }
            .also { logger.info("liste over identifiserte personer etter filterering. Før:${sedPersonRelasjoner.size}, etter: ${it.size}") }

    fun finnesPersonMedAdressebeskyttelseIBuc(alleSediBuc: List<Pair<String, SED>>): Boolean {
        logger.info("Leter etter personer med adressebeskyttelse i : ${alleSediBuc.map { it.second.type }.toJson()}")
        val fnr = alleSediBuc.flatMap { SedFnrSok.finnAlleFnrDnrISed(it.second) }

        return personService.harAdressebeskyttelse(fnr)
            .also { logger.debug("Finnes adressebeskyttet person: $it") }
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(
        identifisertePersoner: List<IdentifisertPDLPerson>,
        bucType: BucType,
        sedType: SedType?,
        potensielleSEDPersonRelasjoner: List<SEDPersonRelasjon>
    ): IdentifisertPDLPerson? {
        logger.info("Antall identifisertePersoner : ${identifisertePersoner.size}, bucType: $bucType, sedType: $sedType")

        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
        if (forsikretPerson != null)
            return forsikretPerson

        secureLog.info("Identifiserte personer: $identifisertePersoner")
        secureLog.info("Potensielle relasjoner: $potensielleSEDPersonRelasjoner")

        return when {
            identifisertePersoner.isEmpty() -> null
            bucType == R_BUC_02 -> identifisertePersoner.first().apply { personListe = identifisertePersoner.filterIndexed{ index, _ -> index != 0 } }
            bucType == P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == GJENLEVENDE }
            bucType == P_BUC_05 -> {
                val erGjenlevendeRelasjon = potensielleSEDPersonRelasjoner.any { it.relasjon == GJENLEVENDE }
                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
            }

            bucType == P_BUC_06 -> {
                val erGjenlevendeRelasjon = potensielleSEDPersonRelasjoner.any { it.relasjon in listOf(GJENLEVENDE, ANNET, BARN, FORSORGER) }
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
                secureLog.info("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }

    //felles for P_BUC_05 og P_BUC_10
    private fun utvelgerPersonOgGjenlev(
        identifisertePersoner: List<IdentifisertPDLPerson>,
        erGjenlevende: Boolean
    ): IdentifisertPDLPerson? {
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
        identifisertePersoner: List<IdentifisertPDLPerson>
    ): IdentifisertPDLPerson? {
        if (sedType in brukForikretPersonISed) {
            logger.info("Henter ut forsikret person fra følgende SED $sedType")
            return identifisertePersoner.firstOrNull { it.personRelasjon?.relasjon == FORSIKRET }
                .also { logger.debug("Identifisert person sin forsikret relasjon: {}", it) }
        }
        return null
    }

    /**
     * Ser første etter fødselsdato som matcher fnr til identifisert person
     * Sjekker alle SEDer, inkl kansellerte
     *
     */
    fun hentFodselsDato(identifisertPerson: IdentifisertPDLPerson?, seder: List<SED>): LocalDate? {

        if( identifisertPerson?.personRelasjon?.fnr == null ){
            return FodselsdatoHelper.fdatoFraSedListe(seder).also { logger.info("Funnet fdato:$it fra identifisert person sin personrelasjon") }
        }

        identifisertPerson.personRelasjon?.fnr?.value?.let {
            if(identifisertPerson.identer?.contains(it) == true){
                logger.info("Fødselsdato funnet i identifisert person sin personrelasjon")
            }
            else logger.info("Fødselsdato ikke funnet i identifisert person sin personrelasjon")
        }

        return seder
            .filter { it.type.kanInneholdeIdentEllerFdato() }
            .mapNotNull { FodselsdatoHelper.filterFodselsdato(it) }
            .firstOrNull { it == identifisertPerson.personRelasjon?.fdato }
            .also { logger.info("Funnet fdato: $it i sed som matcher identifisert person sin personrelasjon") }
    }
}

class FlerePersonPaaBucException : Exception()



