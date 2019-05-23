package no.nav.eessi.pensjon.journalforing.services.personv3

import no.nav.tjeneste.virksomhet.person.v3.informasjon.*
import java.util.*
import javax.xml.datatype.DatatypeFactory
import javax.xml.datatype.XMLGregorianCalendar

object PersonMother {
    internal fun createWith(fodselsnr: String): Person? {
        return Person().withPersonnavn(Personnavn().withEtternavn("Testesen").withFornavn("Test"))
                .withKjoenn(Kjoenn().withKjoenn(Kjoennstyper().withValue("M")))
                .withStatsborgerskap(Statsborgerskap().withLand(Landkoder().withValue("NOR")))
                .withFoedselsdato(Foedselsdato().withFoedselsdato(fodselsdato(fodselsnr)))
                .withBostedsadresse(Bostedsadresse()
                        .withStrukturertAdresse(
                                Gateadresse()
                                        .withGatenavn("Oppoverbakken")
                                        .withHusnummer(66)
                                        .withPoststed(Postnummer().withValue("1920"))
                        )
                )
    }

    private fun fodselsdato(fodselsnr: String): XMLGregorianCalendar? {
        val foedselsdatoCalendar = GregorianCalendar()
        foedselsdatoCalendar.set(
                ("19" + fodselsnr.substring(4..5)).toInt(),
                fodselsnr.substring(2..3).toInt() - 1,
                fodselsnr.substring(0..1).toInt())
        return DatatypeFactory.newInstance().newXMLGregorianCalendar(foedselsdatoCalendar)
    }
}