package no.nav.eessi.pensjon.journalforing.services.oppgave

import no.nav.eessi.pensjon.journalforing.services.kafka.SedHendelseModel
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

class OppgaveRoutingModelTest {

    val mapper = OppgaveRoutingMapper()
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

        val enhet = mapper.map(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt landkode når opppgaverouting gjelder for buc01, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_01,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, "NOR", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc01, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_01,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, "SE", "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.PENSJON_UTLAND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc02, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_02,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc03, så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_03,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc04, så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_04,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, "010580", null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc05, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc05, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_05,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc06, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc06, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_06,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc07, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc07, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_07,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }


    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc08, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc08, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_08,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc09, NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nfpFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc09, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_09,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, null)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 AP, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.AP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 GP, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.GP)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }

    @Test
    fun `Gitt manglende landkode når opppgaverouting gjelder for buc10 UT, NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val sedHendelse = SedHendelseModel(sektorKode = "",
                bucType = SedHendelseModel.BucType.P_BUC_10,
                rinaSakId = "",
                rinaDokumentId = "",
                sedType = SedHendelseModel.SedType.P2000)

        val enhet = mapper.map(sedHendelse, null, nayFodselsDato, OppgaveRoutingModel.YtelseType.UT)
        assertEquals(enhet, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND)
    }
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc02, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc02, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc03, norgesbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc03, utlandsbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc04, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc04, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc05, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc05, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc05, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc05, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc06, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc06, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc06, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc06, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc07, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc07, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc07, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc07, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc08, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc08, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc08, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc08, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc09, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc09, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc09, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc09, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 AP, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 AP, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 GP, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 GP, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
//    }
//
//    @Ignore("Skru på denne testen når hentBosatt() er implementert med PersonV3")
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 UT, utlandsbosatt så send oppgave til UFORE_UTLAND`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLAND.enhetsNr)
//    }
//
//    @Test
//    fun `Gitt fnr når opppgaverouting gjelder for buc10 UT, norgesbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
//        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
//        val response = oppgaveRoutingService.route(request)
//        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
//    }
}