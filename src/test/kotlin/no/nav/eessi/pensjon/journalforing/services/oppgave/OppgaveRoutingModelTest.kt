package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class OppgaveRoutingModelTest {

    val routingService = OppgaveRoutingService()

    val dateFormat = DateTimeFormatter.ofPattern("ddMMyy")

    // NFP krets er en person mellom 18 og 60 år
    val nfpFodselsDato = LocalDate.now().minusYears(19).format(dateFormat)

    // NAY krets er en person yngre enn 18 år eller eldre enn 60 år
    val nayFodselsDato = LocalDate.now().minusYears(10).format(dateFormat)

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc01, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_01,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc01, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_01,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc01, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_01,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc02, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_02,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc02, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_02,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc02, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_02,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc03, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_03,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc03, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_03,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc03, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_03,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc04, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_04,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc04, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_04,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc04, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_04,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc05, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc05, NFP_KRETS, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc05, NFP_KRETS, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc05, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc05, NAY_KRETS, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc05, NAY_KRETS, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc06, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc06, NFP_KRETS, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc06, NFP_KRETS, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc06, NAY_KRETS, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc06, NAY_KRETS, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc06, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc07, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc07, NFP_KRETS, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc07, NFP_KRETS, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc07, NAY_KRETS, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc07, NAY_KRETS, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc07, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc08, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc08, NFP_KRETS, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc08, NFP_KRETS, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc08, NAY_KRETS, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc08, NAY_KRETS, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc08, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc09, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc09, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc09, NFP_KRETS, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc09, NFP_KRETS, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc09, NAY_KRETS, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc09, NAY_KRETS, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 AP, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.AP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc10 AP, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, OppgaveRoutingModel.YtelseType.AP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc10 AP, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, OppgaveRoutingModel.YtelseType.AP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 GP, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.GP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc10 GP, så send oppgave til PENSJON_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, OppgaveRoutingModel.YtelseType.GP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc10 GP, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, OppgaveRoutingModel.YtelseType.GP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 UT, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.UT)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt utenlandsk landkode når opppgaverouting gjelder for buc10 UT, så send oppgave til UFORE_UTLAND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "SE", nfpFodselsDato, OppgaveRoutingModel.YtelseType.UT)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLAND)
    }

    @Test
    fun `Gitt norsk landkode når opppgaverouting gjelder for buc10 UT, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = routingService.route(sedHendelse, "NOR", nfpFodselsDato, OppgaveRoutingModel.YtelseType.UT)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }
}