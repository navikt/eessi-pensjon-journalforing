package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Bruker
import no.nav.eessi.pensjon.eux.model.sed.Brukere
import no.nav.eessi.pensjon.eux.model.sed.Krav
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.Pensjon
import no.nav.eessi.pensjon.eux.model.sed.Person
import no.nav.eessi.pensjon.eux.model.sed.PersonR005
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.RNav
import no.nav.eessi.pensjon.eux.model.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.eux.model.sed.RelasjonTilAvdod
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.Status
import no.nav.eessi.pensjon.eux.model.sed.TilbakekrevingBrukere
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.SokKriterier
import java.time.LocalDate

open class RelasjonTestBase {

    companion object {
        const val SLAPP_SKILPADDE = "09035225916"
        const val KRAFTIG_VEGGPRYD = "11067122781"
        const val LEALAUS_KAKE = "22117320034"
        const val STERK_BUSK = "12011577847"
        const val FORSIKRET_FNAVN = "OLE"
        const val GJENLEV_FNAVN = "GJENLEV"
        const val ANNEN_FNAVN = "ANNEN"
        const val ETTERNAVN = "TESTING"
    }

    fun createR005(forsikretFnr: String?,
                           forsikretTilbakekreving: String?,
                           annenPersonFnr: String? = null,
                           annenPersonTilbakekreving: String? = null): R005 {

        val annenPerson = annenPersonFnr?.let {
            Brukere(
                person = createPersonR005(it),
                tilbakekreving = annenPersonTilbakekreving?.let { type ->
                    TilbakekrevingBrukere(status = Status(type))
                }
            )
        }

        return R005(
            type = SedType.R005,
            recoveryNav = RNav(brukere = listOfNotNull(
                Brukere(
                    person = createPersonR005(forsikretFnr),
                    tilbakekreving = forsikretTilbakekreving?.let {
                        TilbakekrevingBrukere(status = Status(it))
                    }
                ),
                annenPerson
            ))
        )
    }
    private fun createPersonR005(fnr: String?, rolle: Rolle? = null): PersonR005 {
        return PersonR005(
            rolle = rolle?.name,
            foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
            pin = listOfNotNull(
                PinItem(land = "DE", identifikator = "1234567"), // Ugyldig utland
                fnr?.let { PinItem(land = "NO", identifikator = fnr) }
            )
        )
    }

    protected fun generateSED(
        sedType: SedType,
        forsikretFnr: String? = null,
        forsikretRolle: Rolle? = null,
        annenPersonFnr: String? = null,
        annenPersonRolle: Rolle? = null,
        navKrav: KravType? = null,
        // Gjenlevende (IKKE annenPerson)
        gjenlevFnr: String? = null,
        gjenlevRolle: Rolle? = null,
        gjenlevRelasjon: RelasjonTilAvdod? = null
    ): SED {
        return SED(
            type = sedType,
            nav = Nav(
                bruker = Bruker(person = createPerson(forsikretFnr, forsikretRolle, createNameIfFnr(forsikretFnr))),
                annenperson = Bruker(person = createPerson(annenPersonFnr, annenPersonRolle, createNameIfFnr(annenPersonFnr, ANNEN_FNAVN))),
                krav = navKrav?.let { Krav(type = it.kode) }
            ),
            pensjon = gjenlevFnr?.let { createPensjon(gjenlevFnr, gjenlevRelasjon, gjenlevRolle) }
        )
    }

    private fun createNameIfFnr(fnr: String?, fornavn: String = FORSIKRET_FNAVN, etternavn: String = ETTERNAVN): Pair<String, String>? {
        if (fnr != null) {
            return Pair(fornavn, etternavn)
        }
        return null
    }

    private fun createPerson(fnr: String?, rolle: Rolle? = null, navnIfFnr: Pair<String, String>? = null): Person {
        return Person(
            rolle = rolle?.name,
            fornavn = if (navnIfFnr!=null) "${navnIfFnr.first}" else null ,
            etternavn = if (navnIfFnr!=null) "${navnIfFnr.second}" else null,
            foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
            pin = listOfNotNull(
                PinItem(land = "DE", identifikator = "1234567"), // Ugyldig utland
                fnr?.let { PinItem(land = "NO", identifikator = fnr) }
            )
        )
    }

    private fun createPensjon(gjenlevFnr: String?, relasjon: RelasjonTilAvdod?, rolle: Rolle? = null): Pensjon {
        val navnIfFnr = createNameIfFnr(gjenlevFnr, GJENLEV_FNAVN)
        return Pensjon(
            gjenlevende = Bruker(
                person = Person(
                    fornavn = if (navnIfFnr!=null) "${navnIfFnr.first}" else null ,
                    etternavn = if (navnIfFnr!=null) "${navnIfFnr.second}" else null,
                    foedselsdato = Fodselsnummer.fra(gjenlevFnr)?.getBirthDateAsIso() ?: "1955-09-12",
                    pin = listOf(PinItem(land = "NO", identifikator = gjenlevFnr)),
                    relasjontilavdod = relasjon?.let { RelasjonAvdodItem(it.name) },
                    rolle = rolle?.name
                )
            )
        )
    }

    fun createSokKritere(fornavn: String = FORSIKRET_FNAVN, etternavn: String = ETTERNAVN, fdato: LocalDate = LocalDate.of(1952,3,9)) =
        SokKriterier(fornavn, etternavn, fdato)

}
