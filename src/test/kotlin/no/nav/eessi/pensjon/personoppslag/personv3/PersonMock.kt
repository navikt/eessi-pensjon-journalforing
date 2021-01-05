package no.nav.eessi.pensjon.personoppslag.personv3

import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bostedsadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Gateadresse
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoenn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kjoennstyper
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Kommune
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Landkoder
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Personnavn
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Postnummer
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Statsborgerskap

object BrukerMock {
    internal fun createWith(fnr: String? = null, landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen"):
            Bruker? = Bruker()
            .withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(fnr)))
            .withPersonnavn(Personnavn()
                    .withEtternavn(etternavn)
                    .withFornavn(fornavn)
                    .withSammensattNavn("$fornavn $etternavn"))
            .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
            .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))
            .withBostedsadresse(Bostedsadresse()
                    .withStrukturertAdresse(Gateadresse()
                            .withGatenavn("Oppoverbakken")
                            .withHusnummer(66)
                            .withPoststed(Postnummer().withValue("1920"))
                            .withLandkode(when(landkoder){
                                true -> Landkoder().withValue("NOR")
                                else -> null
                            })))
            .withGeografiskTilknytning(when(landkoder) {
                true -> Kommune().withGeografiskTilknytning("026123")
                else -> null
            })
}
