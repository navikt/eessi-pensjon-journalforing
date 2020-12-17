package no.nav.eessi.pensjon

import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.sed.Bruker
import no.nav.eessi.pensjon.models.sed.Krav
import no.nav.eessi.pensjon.models.sed.Nav
import no.nav.eessi.pensjon.models.sed.Pensjon
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.PinItem
import no.nav.eessi.pensjon.models.sed.RelasjonAvdodItem
import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.models.sed.Status
import no.nav.eessi.pensjon.models.sed.Tilbakekreving
import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer

internal class DummySed {
    companion object {
        fun createH070(forsikretFnr: String?): SED =
                generateSED(
                        SedType.H070,
                        forsikretFnr = forsikretFnr
                )

        fun createR005(forsikretFnr: String?,
                       forsikretTilbakekreving: String?,
                       annenPersonFnr: String? = null,
                       annenPersonTilbakekreving: String? = null): SED {

            val annenPerson = annenPersonFnr?.let {
                Bruker(
                        person = createPerson(it),
                        tilbakekreving = annenPersonTilbakekreving?.let { type ->
                            Tilbakekreving(status = Status(type))
                        }
                )
            }

            return SED(
                    type = SedType.R005,
                    nav = Nav(bruker = listOfNotNull(
                            Bruker(
                                    person = createPerson(forsikretFnr),
                                    tilbakekreving = forsikretTilbakekreving?.let {
                                        Tilbakekreving(status = Status(it))
                                    }
                            ),
                            annenPerson
                    ))
            )
        }

        fun createP2100(forsikretFnr: String?,
                        gjenlevFnr: String?,
                        relasjon: String?): SED =
                generateSED(
                        SedType.P2100,
                        forsikretFnr = forsikretFnr,
                        gjenlevFnr = gjenlevFnr,
                        gjenlevRelasjon = relasjon
                )

        fun createP15000(forsikretFnr: String?,
                         gjenlevFnr: String?,
                         krav: String?,
                         relasjon: String?): SED =
                generateSED(
                        SedType.P15000,
                        forsikretFnr = forsikretFnr,
                        navKrav = krav,
                        gjenlevFnr = gjenlevFnr,
                        gjenlevRelasjon = relasjon
                )

        fun createP5000(forsikretFnr: String?,
                        gjenlevFnr: String?,
                        relasjon: String? = null,
                        gjenlevRolle: String? = null): SED {
            return generateSED(
                    SedType.P5000,
                    forsikretFnr = forsikretFnr,
                    gjenlevFnr = gjenlevFnr,
                    gjenlevRelasjon = relasjon,
                    gjenlevRolle = gjenlevRolle
            )
        }

        fun createP6000(forsikretFnr: String?,
                        gjenlevFnr: String?,
                        gjenlevRelasjon: String? = null,
                        gjenlevRolle: String? = null): SED =
                generateSED(
                        SedType.P6000,
                        forsikretFnr = forsikretFnr,
                        gjenlevFnr = gjenlevFnr,
                        gjenlevRelasjon = gjenlevRelasjon,
                        gjenlevRolle = gjenlevRolle
                )

        fun createP8000(forsikretFnr: String?,
                        annenPersonFnr: String?,
                        rolle: String?): SED =
                generateSED(
                        SedType.P8000,
                        forsikretFnr = forsikretFnr,
                        forsikretRolle = rolle,
                        annenPersonFnr = annenPersonFnr
                )

        private fun generateSED(
                sedType: SedType,
                forsikretFnr: String? = null,
                forsikretRolle: String? = null,
                annenPersonFnr: String? = null,
                annenPersonRolle: String? = null,
                navKrav: String? = null,
                // Gjenlevende (IKKE annenPerson)
                gjenlevFnr: String? = null,
                gjenlevRolle: String? = null,
                gjenlevRelasjon: String? = null
        ): SED {
            return SED(
                    type = sedType,
                    nav = Nav(
                            bruker = listOf(Bruker(createPerson(forsikretFnr, forsikretRolle))),
                            annenperson = Bruker(person = createPerson(annenPersonFnr)),
                            krav = navKrav?.let { Krav(type = it) }
                    ),
                    pensjon = gjenlevFnr?.let { createPensjon(gjenlevFnr, gjenlevRelasjon, gjenlevRolle) }
            )
        }

        private fun createPerson(fnr: String?, rolle: String? = null): Person {
            return Person(
                    rolle = rolle,
                    foedselsdato = Fodselsnummer.fra(fnr)?.getBirthDateAsIso() ?: "1955-09-12",
                    pin = listOfNotNull(
                            PinItem(land = "DE", identifikator = "1234567"), // Ugyldig utland
                            fnr?.let { PinItem(land = "NO", identifikator = fnr) }
                    )
            )
        }

        private fun createPensjon(gjenlevFnr: String?, relasjon: String?, rolle: String? = null): Pensjon =
                Pensjon(
                        gjenlevende = Bruker(
                                Person(
                                        pin = listOf(PinItem(land = "NO", identifikator = gjenlevFnr)),
                                        relasjontilavdod = RelasjonAvdodItem(relasjon),
                                        rolle = rolle
                                )
                        )
                )
    }
}