package no.nav.eessi.pensjon.journalforing.services.personv3

import no.nav.tjeneste.virksomhet.person.v3.informasjon.*

object PersonMock {
    internal fun createWith(landkoder: Boolean = true): Person? =
            Person().withPersonnavn(Personnavn().withEtternavn("Testesen").withFornavn("Test"))
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