package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personidentifisering.helpers.FdatoHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerregisterService
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class PersonidentifiseringService(private val aktoerregisterService: AktoerregisterService,
                                  private val personV3Service: PersonV3Service,
                                  private val diskresjonService: DiskresjonkodeHelper,
                                  private val fnrHelper: FnrHelper,
                                  private val fdatoHelper: FdatoHelper) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)

    companion object {
        fun trimFnrString(fnrAsString: String) = fnrAsString.replace("[^0-9]".toRegex(), "")

        fun erFnrDnrFormat(id: String?): Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

    fun hentIdentifisertPerson(navBruker: String?, alleSediBuc: List<String?>, bucType: BucType, sedType: SedType?): IdentifisertPerson? {
        val potensiellePersonRelasjoner = potensiellePersonRelasjonfraSed(alleSediBuc)
        val identifisertePersoner = hentIdentifisertePersoner(navBruker, alleSediBuc, bucType, potensiellePersonRelasjoner)
        return identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
    }

    fun hentIdentifisertePersoner(navBruker: String?, alleSediBuc: List<String?>, bucType: BucType?, potensiellePersonRelasjoner: List<PersonRelasjon>): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")
        val trimmetNavBruker = navBruker?.let { trimFnrString(it) }

        val personForNavBruker = when {
            bucType == BucType.P_BUC_02 -> null
            bucType == BucType.P_BUC_05 -> null
            erFnrDnrFormat(trimmetNavBruker) -> personV3Service.hentPerson(trimmetNavBruker!!)
            else -> null
        }

        if (personForNavBruker != null) {
            return listOf(populerIdentifisertPerson(personForNavBruker, alleSediBuc, PersonRelasjon(trimmetNavBruker!!, Relasjon.FORSIKRET)))

        } else {
            // Leser inn fnr fra utvalgte seder
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC")
            val identifisertePersonRelasjoner = mutableListOf<IdentifisertPerson>()
            try {
                //val potensiellePersonRelasjoner = potensiellePersonRelasjonfraSed(alleSediBuc)
                logger.debug("funnet antall fnr fra SED : ${potensiellePersonRelasjoner.size}")
                potensiellePersonRelasjoner.forEach { personRelasjon ->
                    val fnr = personRelasjon.fnr
                    logger.debug("Relasjon fnr : $fnr")

                    val trimmetfnr = trimFnrString(fnr)
                    val personen = if (erFnrDnrFormat(trimmetfnr)) {
                            logger.debug("henter Person med fnr fra SED")
                            personV3Service.hentPerson(trimmetfnr)
                        } else {
                            logger.warn("ingen gyldig fnr fra SED")
                            null
                        }

                    logger.debug("PersonV3 person: $personen")
                    if (personen != null) {
                        val identifisertPerson = populerIdentifisertPerson(
                                personen,
                                alleSediBuc,
                                personRelasjon)
                        identifisertePersonRelasjoner.add(identifisertPerson)
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Feil ved henting av person / fødselsnummer fra SEDer, fortsetter uten", ex)
            }
            return identifisertePersonRelasjoner
        }
    }

    fun potensiellePersonRelasjonfraSed(alleSediBuc: List<String?>): List<PersonRelasjon> {
        return fnrHelper.getPotensielleFnrFraSeder(alleSediBuc)
    }

    private fun populerIdentifisertPerson(person: Bruker, alleSediBuc: List<String?>, personRelasjon: PersonRelasjon): IdentifisertPerson {
        val personNavn = hentPersonNavn(person)
        val aktoerId = hentAktoerId(personRelasjon.fnr) ?: ""
        val diskresjonskode = diskresjonService.hentDiskresjonskode(alleSediBuc)
        val landkode = hentLandkode(person)
        val geografiskTilknytning = hentGeografiskTilknytning(person)

        return IdentifisertPerson(aktoerId, personNavn, diskresjonskode, landkode, geografiskTilknytning, personRelasjon)
    }

    /**
     * Forsøker å finne om identifisert person er en eller fler med avdød person
     */
    fun identifisertPersonUtvelger(identifisertePersoner: List<IdentifisertPerson>, bucType: BucType, sedType: SedType?, potensiellePersonRelasjoner: List<PersonRelasjon>): IdentifisertPerson? {
        logger.info("IdentifisertePersoner $bucType, SedType: ${sedType?.name}, antall identifisertePersoner : ${identifisertePersoner.size} ")

        val forsikretPerson = brukForsikretPerson(sedType, identifisertePersoner)
        if (forsikretPerson != null)
            return forsikretPerson

        return when {
            identifisertePersoner.isEmpty() -> null
            bucType == BucType.R_BUC_02 -> {
                return run {
                    val forstPersonIdent = identifisertePersoner.first()
                    forstPersonIdent.personListe = identifisertePersoner
                    forstPersonIdent
                }
            }
            bucType == BucType.P_BUC_02 -> {
                identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
            }
            bucType == BucType.P_BUC_05 -> {
                identifisertePersoner.forEach {
                    logger.debug(it.toJson())
                }
                val person = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
                val gjenlev = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
                val erGjenlevendeRelasjon = potensiellePersonRelasjoner.any { it.relasjon == Relasjon.GJENLEVENDE }
                logger.info("personAktoerid: ${person?.aktoerId}, gjenlevAktoerid: ${gjenlev?.aktoerId} harGjenlvrelasjon: $erGjenlevendeRelasjon")

                when {
                    gjenlev != null -> gjenlev
                    erGjenlevendeRelasjon ->  null
                    else -> {
                        person?.personListe = identifisertePersoner.filterNot { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
                        person
                    }
                }
            }

            bucType == BucType.P_BUC_10 -> {
                identifisertePersoner.forEach {
                    logger.debug(it.toJson())
                }
                val person = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
                val gjenlev = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }

                if (person?.personRelasjon?.ytelseType != YtelseType.GJENLEV) {
                    person
                } else {
                    gjenlev
                }
            }
            identifisertePersoner.size == 1 -> identifisertePersoner.first()
            else -> {
                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }
    /**
     * Noen Seder kan kun inneholde forsikret person i de tilfeller benyttes den forsikrede selv om andre Sed i Buc inneholder andre personer
     */
    private fun brukForsikretPerson(sedType: SedType?, identifisertePersoner: List<IdentifisertPerson>): IdentifisertPerson? {
        if (sedType in brukForikretPersonISed) {
            return identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
        }
        return null
    }

    /**
     * Henter første treff på dato fra listen av SEDer
     */
    fun hentFodselsDato(identifisertPerson: IdentifisertPerson?, seder: List<String?>?): LocalDate {
        val fnr = identifisertPerson?.personRelasjon?.fnr
        val fdatoFraFnr = if (!erFnrDnrFormat(fnr)) null else fodselsDatoFra(fnr!!)
        if (fdatoFraFnr != null) {
            return fdatoFraFnr
        }
        if (!seder.isNullOrEmpty()) {
            logger.info("Henter fdato fra SEDer")
            return fdatoHelper.finnEnFdatoFraSEDer(seder)
        }
        throw RuntimeException("Kunne ikke finne fdato i listen over SEDer")
    }

    private fun fodselsDatoFra(fnr: String): LocalDate? =
            try {
                val trimmedFnr = trimFnrString(fnr)
                Fodselsnummer.fra(trimmedFnr)?.getBirthDate()
            } catch (ex: Exception) {
                logger.error("navBruker ikke gyldig for fdato", ex)
                null
            }

    private fun hentAktoerId(navBruker: String): String? {
        if (!erFnrDnrFormat(navBruker)) return null
        return try {
            aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(navBruker))?.id
        } catch(e: Exception) {
            logger.warn("Det oppstod en feil ved henting av aktørid: ${e.cause}", e)
            null
        }
    }
}

data class IdentifisertPerson(
        val aktoerId: String,
        val personNavn: String?,
        val diskresjonskode: Diskresjonskode? = null,
        val landkode: String?,
        val geografiskTilknytning: String?,
        val personRelasjon: PersonRelasjon,
        var personListe: List<IdentifisertPerson>? = null
) {
    override fun toString(): String {
        return "IdentifisertPerson(aktoerId='$aktoerId', personNavn=$personNavn, diskresjonskode=$diskresjonskode, landkode=$landkode, geografiskTilknytning=$geografiskTilknytning, personRelasjon=$personRelasjon)"
    }

    fun flereEnnEnPerson() = personListe != null && personListe!!.size > 1
}


data class PersonRelasjon(
        val fnr: String,
        val relasjon: Relasjon,
        val ytelseType: YtelseType? = null
)

enum class Relasjon {
    FORSIKRET,
    GJENLEVENDE,
    AVDOD,
    ANNET,
    BARN,
    FORSORGER
}

fun hentLandkode(person: Person) =
        person.bostedsadresse?.strukturertAdresse?.landkode?.value

fun hentPersonNavn(person: Person) =
        person.personnavn?.sammensattNavn

fun hentGeografiskTilknytning(bruker: Bruker?) = bruker?.geografiskTilknytning?.geografiskTilknytning
