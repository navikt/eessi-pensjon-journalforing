package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter

val logger: Logger = LoggerFactory.getLogger(Relasjon::class.java)
val secureLog = LoggerFactory.getLogger("secureLog")

abstract class AbstractRelasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String) {

    val forsikretPerson = sed.nav?.bruker?.person

    abstract fun hentRelasjoner(): List<SEDPersonRelasjon>

    fun hentForsikretPerson(saktype: SakType?): List<SEDPersonRelasjon> {
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}, med rinasak: $rinaDocumentId")

        forsikretPerson?.let { person ->
            val sokPersonKriterie =  opprettSokKriterie(person)
            val fodselnummer = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val fdato = mapFdatoTilLocalDate(person.foedselsdato)

            logger.info("Legger til person ${Relasjon.FORSIKRET} og sedType: ${sed.type}")
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
            ).also { logger.debug("liste over SedPersonRelasjoner for forsikret person: {}", it) }
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
        secureLog.info("Oppretter sokKriterier: ${sokKriterier.fornavn}, ${sokKriterier.etternavn}, ${sokKriterier.foedselsdato}")
        return sokKriterier
    }

    fun mapFdatoTilLocalDate(fdato: String?) : LocalDate? = fdato?.let { LocalDate.parse(it, DateTimeFormatter.ISO_DATE) }

    fun bestemSaktype(bucType: BucType): SakType? {
        return when(bucType) {
            P_BUC_01 -> ALDER
            P_BUC_02 -> GJENLEV
            P_BUC_03 -> UFOREP
            else -> null
        }
    }

}
