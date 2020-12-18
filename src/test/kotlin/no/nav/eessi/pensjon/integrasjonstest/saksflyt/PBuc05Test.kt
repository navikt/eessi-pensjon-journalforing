package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.verify
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostType
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.models.sed.DocStatus
import no.nav.eessi.pensjon.models.sed.Document
import no.nav.eessi.pensjon.models.sed.Rolle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class PBuc05Test : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "09035225916"   // SLAPP SKILPADDE
        private const val FNR_VOKSEN = "11067122781"    // KRAFTIG VEGGPRYD
        private const val FNR_VOKSEN_2 = "22117320034"  // LEALAUS KAKE
        private const val FNR_BARN = "12011577847"      // STERK BUSK

    }

    /**
     * P_BUC_05 DEL 1
     */

    @Test
    fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
        initSed(createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.ETTERLATTE), null))
        initDokumenter(getMockDocuments())
        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.SENDT, SedType.P8000, BucType.P_BUC_05, ferdigstilt = false) {
            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertNull(it.request.bruker)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Hente opp korrekt fnr fra P8000 som er sendt fra oss med flere P8000 i BUC`() {
        initSed(
                createSed(SedType.P8000, FNR_VOKSEN, createAnnenPerson(fnr = FNR_VOKSEN_2, rolle = Rolle.ETTERLATTE), SAK_ID),
                createSed(SedType.P8000, FNR_VOKSEN, createAnnenPerson(fnr = FNR_VOKSEN_2, rolle = Rolle.ETTERLATTE), SAK_ID),
                createSed(SedType.P8000, null, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE), null)
        )

        initDokumenter(
                Document("10000000001", SedType.P8000, DocStatus.SENT),
                Document("20000000002", SedType.P8000, DocStatus.RECEIVED),
                Document("9498fc46933548518712e4a1d513399192", SedType.H121, DocStatus.EMPTY),
                Document("40000000004", SedType.P6000, DocStatus.SENT),
                Document("9498fc46933548518712e4a1d5133113", SedType.H070, DocStatus.EMPTY)
        )

        initMockPerson(fnr = FNR_VOKSEN, aktoerId = AKTOER_ID)
        initMockPerson(fnr = FNR_VOKSEN_2, aktoerId = AKTOER_ID_2)

        initSaker(AKTOER_ID_2,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
        )

        consumeAndAssert(HendelseType.SENDT, SedType.P6000, BucType.P_BUC_05, ferdigstilt = true) {
            assertNull(it.melding)

            assertEquals(UFORETRYGD, it.request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN_2, it.request.bruker!!.id)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 3) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 0 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSedPensjon(SedType.P5000, FNR_OVER_60, gjenlevendeFnr = FNR_BARN)
        )

        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        initMockPerson(FNR_BARN, aktoerId = AKTOER_ID_2)
        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.SENDT, SedType.P5000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(it.response.journalpostId, it.melding.journalpostId)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(FNR_BARN, it.request.bruker!!.id)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 1 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P5000, null, fdato = "1955-07-11")
        )

        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        consumeAndAssert(HendelseType.SENDT, SedType.P5000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertNull(it.request.bruker)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P5000, FNR_OVER_60)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        initMockPerson(FNR_OVER_60, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.SENDT, SedType.P5000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P5000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, it.request.bruker?.id!!)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og sakid i sed journalføres automatisk `() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P9000, FNR_VOKSEN, eessiSaknr = SAK_ID)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P9000, DocStatus.SENT)
        )
        initSaker(AKTOER_ID,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )

        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.SENDT, SedType.P9000, BucType.P_BUC_05, ferdigstilt = true) {
            assertNull(it.melding)

            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(UFORETRYGD, it.request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.request.bruker?.id!!)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og ingen sakid i sed journalføres UFO `() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P9000, FNR_VOKSEN, eessiSaknr = SAK_ID)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P9000, DocStatus.SENT)
        )
        initSaker(AKTOER_ID,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )
        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID)

        consumeAndAssert(HendelseType.SENDT, SedType.P9000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P9000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(FNR_VOKSEN, it.request.bruker?.id!!)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 3 Sed sendes som svar med fnr og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema NFP UTLAND AALESUND`() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P5000, FNR_VOKSEN, eessiSaknr = SAK_ID)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )

        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID)

        initSaker(AKTOER_ID,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "34234234234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )

        consumeAndAssert(HendelseType.SENDT, SedType.P6000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P6000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }

    }

    @Test
    fun `Scenario 13 - 4 Sed sendes som svar med fnr utland og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PENSJON UTLAND`() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P5000, FNR_VOKSEN, eessiSaknr = SAK_ID)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )
        initMockPerson(FNR_VOKSEN, land = "SWE", aktoerId = AKTOER_ID)

        initSaker(AKTOER_ID,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123123", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
        )

        consumeAndAssert(HendelseType.SENDT, SedType.P6000, BucType.P_BUC_05) {
            assertEquals("JOURNALFORING", it.melding!!.oppgaveType())
            assertEquals(ID_OG_FORDELING, it.melding.tildeltEnhetsnr)
            assertEquals(SedType.P6000, it.melding.sedType)

            assertEquals(it.response.journalpostId, it.melding.journalpostId)

            assertEquals(PENSJON, it.request.tema)
            assertEquals(ID_OG_FORDELING, it.request.journalfoerendeEnhet)
            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

    @Test
    fun `Scenario 13 - 5 Sed sendes som svar med fnr og sak finnes og er UFOREP pa tidligere mottatt P8000, journalføres automatisk`() {
        initSed(
                createSed(SedType.P8000, null, fdato = "1955-07-11"),
                createSed(SedType.P5000, FNR_VOKSEN, eessiSaknr = SAK_ID)
        )
        initDokumenter(
                Document("10001", SedType.P8000, DocStatus.RECEIVED),
                Document("30002", SedType.P5000, DocStatus.SENT)
        )
        initMockPerson(FNR_VOKSEN, aktoerId = AKTOER_ID)

        initSaker(AKTOER_ID,
                SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE)
        )

        consumeAndAssert(HendelseType.SENDT, SedType.P6000, BucType.P_BUC_05, ferdigstilt = true) {
            assertNull(it.melding)

            assertEquals(JournalpostType.UTGAAENDE, it.request.journalpostType)
            assertEquals(UFORETRYGD, it.request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, it.request.journalfoerendeEnhet)
        }

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
        verify(exactly = 2) { euxKlient.hentSed(any(), any()) }
    }

}
