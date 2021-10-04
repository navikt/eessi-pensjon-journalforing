package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.Brukere
import no.nav.eessi.pensjon.eux.model.sed.PersonR005
import no.nav.eessi.pensjon.eux.model.sed.PinItem
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.RNav
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.Status
import no.nav.eessi.pensjon.eux.model.sed.TilbakekrevingBrukere
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer

open class RelasjonTestBase {

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

}
