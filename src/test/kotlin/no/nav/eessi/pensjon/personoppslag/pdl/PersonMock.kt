package no.nav.eessi.pensjon.personoppslag.pdl

import no.nav.eessi.pensjon.personidentifisering.helpers.Fodselsnummer
import no.nav.eessi.pensjon.personoppslag.pdl.model.AdressebeskyttelseGradering
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.Foedsel
import no.nav.eessi.pensjon.personoppslag.pdl.model.GeografiskTilknytning
import no.nav.eessi.pensjon.personoppslag.pdl.model.GtType
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.Navn
import no.nav.eessi.pensjon.personoppslag.pdl.model.Person
import no.nav.eessi.pensjon.personoppslag.pdl.model.UtenlandskAdresse
import no.nav.eessi.pensjon.personoppslag.pdl.model.Vegadresse
import java.time.LocalDateTime

object PersonMock {
    internal fun createWith(fnr: String? = null,
                            landkoder: Boolean = true,
                            fornavn: String = "Test",
                            etternavn: String = "Testesen",
                            aktoerId: AktoerId? = null,
                            geo: String? = "0301"): Person {

        val foedselsdato = fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }
        val utenlandskadresse = if (landkoder) null else UtenlandskAdresse(landkode = "SWE")

        val identer = listOfNotNull(
                aktoerId?.let { IdentInformasjon(ident = it.id, gruppe = IdentGruppe.AKTORID) }
        )

        return Person(
                identer = identer,
                navn = Navn(fornavn, null, etternavn),
                adressebeskyttelse = listOf(AdressebeskyttelseGradering.UGRADERT),
                bostedsadresse = no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse(
                        gyldigFraOgMed = LocalDateTime.now(),
                        gyldigTilOgMed = LocalDateTime.now(),
                        vegadresse = Vegadresse("Oppoverbakken", "66", null, "1920"),
                        utenlandskAdresse = utenlandskadresse
                ),
                oppholdsadresse = null,
                statsborgerskap = emptyList(),
                foedsel = Foedsel(foedselsdato, null),
                geografiskTilknytning = geo?.let { GeografiskTilknytning(GtType.KOMMUNE, it, null, null) }
        )
    }
}