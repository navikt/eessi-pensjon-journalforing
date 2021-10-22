package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val logger: Logger = LoggerFactory.getLogger(Relasjon::class.java)

abstract class AbstractRelasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) {

    val forsikretPerson = sed.nav?.bruker?.person

    abstract fun hentRelasjoner(): List<SEDPersonRelasjon>

    fun hentForsikretPerson(saktype: Saktype?): List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        forsikretPerson?.let { person ->
            val sokPersonKriterie =  opprettSokKriterie(person)
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato = mapFdatoTilLocalDate(person.foedselsdato)

            logger.debug("Legger til person ${Relasjon.FORSIKRET} og sedType: ${sed.type}")
            return listOf(
                SEDPersonRelasjon(
                    fodselnummer,
                    Relasjon.FORSIKRET,
                    saktype,
                    sed.type,
                    sokPersonKriterie,
                    fdato,
                    rinaDocumentId
                )
            )
        }

        logger.warn("Ingen forsikret person funnet")
        throw RuntimeException("Ingen forsikret person funnet")
    }

    fun opprettSokKriterie(person: Person) : SokKriterier? {
        val fdatotmp: String = person.foedselsdato ?: return null
        val fornavn: String = person.fornavn ?: return null
        val etternavn: String = person.etternavn ?: return null

        val fodseldato: LocalDate = mapFdatoTilLocalDate(fdatotmp)!!
        val sokKriterier = SokKriterier(
            fornavn,
            etternavn,
            fodseldato
        )
        logger.debug("Oppretter sokKriterier: ${sokKriterier.fornavn}, ${sokKriterier.etternavn}, ${sokKriterier.foedselsdato}")
        return sokKriterier
    }

    fun mapFdatoTilLocalDate(fdato: String?) : LocalDate? = fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }

    fun bestemSaktype(bucType: BucType): Saktype? {
        return when(bucType) {
            BucType.P_BUC_01 -> Saktype.ALDER
            BucType.P_BUC_02 -> Saktype.GJENLEV
            BucType.P_BUC_03 -> Saktype.UFOREP
            else -> null
        }
    }

}
