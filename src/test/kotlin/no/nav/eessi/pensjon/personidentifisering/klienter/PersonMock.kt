package no.nav.eessi.pensjon.personidentifisering.klienter

import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse

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

object HentPersonResponse {
    internal fun createWith(fnr: String? = null, landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen"):
            HentPersonResponse = HentPersonResponse().withPerson(BrukerMock.createWith(fnr, landkoder, fornavn, etternavn))
}

