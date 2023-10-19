import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NavansattTest {

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


}