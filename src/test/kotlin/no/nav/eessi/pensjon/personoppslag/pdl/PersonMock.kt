package no.nav.eessi.pensjon.personoppslag.pdl

import no.nav.eessi.pensjon.personoppslag.pdl.model.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import java.time.LocalDate
import java.time.LocalDateTime

object PersonMock {
    internal fun createWith(
        fnr: String? = null,
        landkoder: Boolean = true,
        fornavn: String = "Test",
        etternavn: String = "Testesen",
        aktoerId: AktoerId? = null,
        geo: String? = "0301"
    ): Person {

        val foedselsdato  = if(Fodselsnummer.fra(fnr)?.erNpid == true)
            LocalDate.now().minusYears(66)
        else fnr?.let { Fodselsnummer.fra(it)?.getBirthDate() }

        val utenlandskadresse = if (landkoder) null else UtenlandskAdresse(landkode = "SWE")

        val identer = listOfNotNull(
            aktoerId?.let { IdentInformasjon(ident = it.id, gruppe = IdentGruppe.AKTORID) },
            fnr?.let { if (Fodselsnummer.fra(fnr)?.erNpid == true){
                    IdentInformasjon(ident = it, gruppe = IdentGruppe.NPID)
                }
                else IdentInformasjon(ident = it, gruppe = IdentGruppe.FOLKEREGISTERIDENT)
            }
        )

        val metadata = Metadata(
            listOf(
                Endring(
                    "kilde",
                    LocalDateTime.now(),
                    "ole",
                    "system1",
                    Endringstype.OPPRETT
                )
            ),
            false,
            "nav",
            "1234"
        )

        return Person(
            identer = identer,
            navn = Navn(fornavn, null, etternavn, metadata = metadata),
            adressebeskyttelse = listOf(AdressebeskyttelseGradering.UGRADERT),
            bostedsadresse = no.nav.eessi.pensjon.personoppslag.pdl.model.Bostedsadresse(
                gyldigFraOgMed = LocalDateTime.now(),
                gyldigTilOgMed = LocalDateTime.now(),
                vegadresse = Vegadresse("Oppoverbakken", "66", null, "1920"),
                utenlandskAdresse = utenlandskadresse,
                metadata
            ),
            oppholdsadresse = null,
            statsborgerskap = emptyList(),
            foedsel = Foedsel(foedselsdato, null, null, null, metadata = metadata),
            geografiskTilknytning = geo?.let { GeografiskTilknytning(GtType.KOMMUNE, it, null, null) },
            kjoenn = Kjoenn(KjoennType.KVINNE, null, metadata),
            doedsfall = null,
            forelderBarnRelasjon = emptyList(),
            sivilstand = emptyList(),
            utenlandskIdentifikasjonsnummer = emptyList()
        )
    }
}