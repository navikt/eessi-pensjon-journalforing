package no.nav.eessi.pensjon.services.personv3

import no.nav.tjeneste.virksomhet.person.v3.informasjon.*

object PersonMock {
    internal fun createWith(landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen"):
            Person? = Person()
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
}

object BrukerMock {
    internal fun createWith(landkoder: Boolean = true, fornavn: String = "Test", etternavn: String = "Testesen"):
            Bruker? = Bruker()
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
