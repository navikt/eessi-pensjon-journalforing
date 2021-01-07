package no.nav.eessi.pensjon.personidentifisering

import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.DiskresjonkodeHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personidentifisering.helpers.FnrHelper
import no.nav.eessi.pensjon.personidentifisering.helpers.FodselsdatoHelper
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
                                  private val fnrHelper: FnrHelper) {

    private val logger = LoggerFactory.getLogger(PersonidentifiseringService::class.java)
    private val brukForikretPersonISed = listOf(SedType.H121, SedType.H120, SedType.H070)

    companion object {
        fun erFnrDnrFormat(id: String?): Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

    fun hentIdentifisertPerson(navBruker: Fodselsnummer?, sedListe: List<SED>, bucType: BucType, sedType: SedType?): IdentifisertPerson? {
        val potensiellePersonRelasjoner = fnrHelper.getPotensielleFnrFraSeder(sedListe)
        val identifisertePersoner = hentIdentifisertePersoner(navBruker, sedListe, bucType, potensiellePersonRelasjoner)
        return identifisertPersonUtvelger(identifisertePersoner, bucType, sedType, potensiellePersonRelasjoner)
    }

    fun hentIdentifisertePersoner(navBruker: Fodselsnummer?, alleSediBuc: List<SED>, bucType: BucType?, potensiellePersonRelasjoner: List<PersonRelasjon>): List<IdentifisertPerson> {
        logger.info("Forsøker å identifisere personen")

        val personForNavBruker = when {
            bucType == BucType.P_BUC_02 -> null
            bucType == BucType.P_BUC_05 -> null
            bucType == BucType.P_BUC_10 -> null
            navBruker != null -> personV3Service.hentPerson(navBruker.value)
            else -> null
        }

        return if (personForNavBruker != null) {
            listOf(populerIdentifisertPerson(personForNavBruker, alleSediBuc, PersonRelasjon(navBruker!!, Relasjon.FORSIKRET)))
        } else {
            // Leser inn fnr fra utvalgte seder
            logger.info("Forsøker å identifisere personer ut fra SEDer i BUC: $bucType")
            potensiellePersonRelasjoner
                    .filterNot { it.fnr == null }
                    .mapNotNull { relasjon -> hentIdentifisertPerson(relasjon, alleSediBuc) }
        }
    }

    private fun hentIdentifisertPerson(relasjon: PersonRelasjon, alleSediBuc: List<SED>): IdentifisertPerson? {
        logger.debug("Henter ut følgende personRelasjon: ${relasjon.toJson()}")

        return try {
            personV3Service.hentPerson(relasjon.fnr!!.value)
                    ?.let { bruker -> populerIdentifisertPerson(bruker, alleSediBuc, relasjon) }
                    ?.also {
                        logger.debug("""IdentifisertPerson aktoerId: ${it.aktoerId}, landkode: ${it.landkode}, 
                                    navn: ${it.personNavn}, sed: ${it.personRelasjon.sedType?.name}""".trimIndent())
                    }
        } catch (ex: Exception) {
            logger.warn("Feil ved henting av person fra personV3Service, fortsetter uten", ex)
            null
        }
    }

    private fun populerIdentifisertPerson(person: Bruker, alleSediBuc: List<SED>, personRelasjon: PersonRelasjon): IdentifisertPerson {
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
            bucType == BucType.R_BUC_02 -> identifisertePersoner.first().apply { personListe = identifisertePersoner }
            bucType == BucType.P_BUC_02 -> identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
            bucType == BucType.P_BUC_05 -> {
                val erGjenlevendeRelasjon = potensiellePersonRelasjoner.any { it.relasjon == Relasjon.GJENLEVENDE }

                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeRelasjon)
            }
            bucType == BucType.P_BUC_10 -> {
                val erGjenlevendeYtelse = potensiellePersonRelasjoner.any { it.ytelseType == YtelseType.GJENLEV }

                utvelgerPersonOgGjenlev(identifisertePersoner, erGjenlevendeYtelse)
            }
            identifisertePersoner.size == 1 -> identifisertePersoner.first()
            else -> {
                logger.debug("BucType: $bucType Personer: ${identifisertePersoner.toJson()}")
                throw RuntimeException("Stopper grunnet flere personer på bucType: $bucType")
            }
        }
    }

    //felles for P_BUC_05 og P_BUC_10
    private fun utvelgerPersonOgGjenlev(identifisertePersoner: List<IdentifisertPerson>, erGjenlevende: Boolean): IdentifisertPerson? {
        identifisertePersoner.forEach {
            logger.debug(it.toJson())
        }
        val forsikretPerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
        val gjenlevendePerson = identifisertePersoner.firstOrNull { it.personRelasjon.relasjon == Relasjon.GJENLEVENDE }
        logger.info("personAktoerid: ${forsikretPerson?.aktoerId}, gjenlevAktoerid: ${gjenlevendePerson?.aktoerId}, harGjenlvRelasjon: $erGjenlevende")

        return when {
            gjenlevendePerson != null -> gjenlevendePerson
            erGjenlevende -> null
            else -> {
                forsikretPerson?.apply {
                    personListe = identifisertePersoner.filterNot { it.personRelasjon.relasjon == Relasjon.FORSIKRET }
                }
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
    fun hentFodselsDato(identifisertPerson: IdentifisertPerson?, seder: List<SED>, kansellerteSeder: List<SED>): LocalDate? {
        return identifisertPerson?.personRelasjon?.fnr?.getBirthDate()
                ?: FodselsdatoHelper.fraSedListe(seder, kansellerteSeder)
    }

    private fun hentAktoerId(fnr: Fodselsnummer?): String? {
        if (fnr == null) return null

        return try {
            aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr.value))?.id
        } catch (e: Exception) {
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
        val fnr: Fodselsnummer?,
        val relasjon: Relasjon,
        val ytelseType: YtelseType? = null,
        val sedType: SedType? = null
) {
    fun erGyldig(): Boolean = sedType != null && (ytelseType != null || relasjon == Relasjon.GJENLEVENDE)
}

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

