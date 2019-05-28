package no.nav.eessi.pensjon.journalforing.services.oppgave

import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals

class OppgaveRoutingModelTest {

    val oppgaveRoutingService = OppgaveRoutingService()

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc01, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_01, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc01, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_01, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc02, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc02, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc03, norgesbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc03, utlandsbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc04, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc04, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc05, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc05, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc05, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc05, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc06, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc06, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc06, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc06, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc07, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc07, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc07, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc07, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc08, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc08, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc08, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc08, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc09, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc09, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc09, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc09, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 AP, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 AP, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 GP, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 GP, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 UT, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, null, OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt manglende fnr når opppgaverouting gjelder for buc10 UT, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, null, OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc01, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_01, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc01, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_01, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc02, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc02, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_02, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc03, norgesbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc03, utlandsbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_03, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc04, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc04, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_04, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc05, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc05, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc05, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc05, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_05_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc06, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc06, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc06, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc06, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_06_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc07, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc07, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc07, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc07, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_07_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc08, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc08, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc08, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc08, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_08_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc09, norgesbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc09, utlandsbosatt NFP_KRETS så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NFP_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc09, norgesbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc09, utlandsbosatt NAY_KRETS så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_09_NAY_KRETS, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 AP, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 AP, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_AP, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 GP, utlandsbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 GP, norgesbosatt så send oppgave til NFP_UTLAND_AALESUND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_GP, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.NFP_UTLAND_AALESUND.enhetsNr)
    }

    @Ignore("Skru på denne testen når hentBosatt() er implementert med PersonV3")
    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 UT, utlandsbosatt så send oppgave til UFORE_UTLAND`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, "12345678910", OppgaveRoutingModel.Bosatt.UTLAND)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLAND.enhetsNr)
    }

    @Test
    fun `Gitt fnr når opppgaverouting gjelder for buc10 UT, norgesbosatt så send oppgave til UFORE_UTLANDSTILSNITT`() {
        val request = OppgaveRoutingModel(OppgaveRoutingModel.BucType.P_BUC_10_UT, "12345678910", OppgaveRoutingModel.Bosatt.NORGE)
        val response = oppgaveRoutingService.route(request)
        assertEquals(response.enhetsNr, OppgaveRoutingModel.Enhet.UFORE_UTLANDSTILSNITT.enhetsNr)
    }
}