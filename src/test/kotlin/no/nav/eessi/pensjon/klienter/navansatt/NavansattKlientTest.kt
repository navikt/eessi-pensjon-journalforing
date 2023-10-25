import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.klienter.navansatt.EnheterFraAd
import no.nav.eessi.pensjon.klienter.navansatt.Navansatt
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavansattKlientTest {

    @Test
    fun `Sjekker at vi får ut riktig navansatt`() {
        val hendelse = SedHendelse(
            sedType = SedType.M051,
            rinaDokumentId = "19fd5292007e4f6ab0e337e89079aaf4",
            bucType = BucType.M_BUC_03a,
            rinaSakId = "123456789",
            avsenderId = "NO:noinst002",
            avsenderNavn = "NOINST002, NO INST002, NO",
            mottakerId = "SE:123456789",
            mottakerNavn = "SE, SE INST002, SE",
            rinaDokumentVersjon = "1",
            sektorKode = "M",
        )
        val buc = mapJsonToAny<Buc>(javaClass.getResource("/buc/M_BUC.json")!!.readText())
        val sisteSedsendt = buc.documents?.firstOrNull{it.id == hendelse.rinaDokumentId }?.versions?.last()

        assertEquals("Z990965", sisteSedsendt?.user?.name)
        assertEquals("5", sisteSedsendt?.id)
        assertEquals("2023-10-12T07:16:08.491+00:00", sisteSedsendt?.date)
    }

    @Test
    fun `Ved kall til navansatt så skal vi slå sammen enhetsnummer og navn for å sende det som journalførende enhet`() {
        //https://navansatt.dev.adeo.no/navansatt/K105134
        val navansattInfo = """
            {
                "ident":"H562012",
                "navn":"Andersen, Anette Christin",
                "fornavn":"Anette Christin",
                "etternavn":"Andersen",
                "epost":"Anette.Christin.Andersen@nav.no",
                "groups":[
                    "Group_be80eb75-e270-40ca-a5f9-29ae8b63eecd",
                    "1209XX-GA-Brukere",
                    "4407-GO-Enhet",
                    "0000-GA-EESSI-CLERK-UFORE",
                    "0000-GA-Person-EndreSprakMalform",
                    "0000-GA-Person-EndreKommunikasjon",
                    "0000-ga-eessi-basis",
                    "0000-GA-Arena","0000-GA-STDAPPS"
                ]
            }
        """.trimIndent()
        val navansatt = mapJsonToAny<Navansatt>(navansattInfo)
        assertEquals("4407-GO-Enhet", navansatt.groups[2])
        assertEquals("Andersen, Anette Christin", navansatt.navn)
    }

    @Test
    fun henterEnheterForAnsatt() {
        val enhetsResponse = """
            [
                {
                "id":"0001",
                "navn":"NAV Familie- og pensjonsytelser Utland",
                "nivaa":"SPESEN"
                },
                {
                "id":"4407",
                "navn":"NAV Arbeid og ytelser Tønsberg",
                "nivaa":"EN"
                }
            ] 
        """.trimIndent()
        val enheter = mapJsonToAny<List<EnheterFraAd>>(enhetsResponse)
        assertEquals("NAV Familie- og pensjonsytelser Utland", enheter[0].navn)
        assertEquals("NAV Arbeid og ytelser Tønsberg", enheter[1].navn)

    }


}