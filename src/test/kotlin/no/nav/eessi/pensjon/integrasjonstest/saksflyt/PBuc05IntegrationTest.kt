package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.journalpost.JournalpostType
import no.nav.eessi.pensjon.klienter.journalpost.OpprettJournalpostRequest
import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import no.nav.eessi.pensjon.personoppslag.aktoerregister.AktoerId
import no.nav.eessi.pensjon.personoppslag.aktoerregister.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.aktoerregister.NorskIdent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_05 - Utgående journalføring - IntegrationTest")
internal class PBuc05IntegrationTest : JournalforingTestBase() {

    companion object {
        private const val FNR_OVER_60 = "01115043352"
        private const val FNR_VOKSEN = "01119043352"
        private const val FNR_VOKSEN_2 = "01118543352"
        private const val FNR_BARN = "01110854352"

        private const val AKTOER_ID = "0123456789000"
        private const val AKTOER_ID_2 = "0009876543210"

        private const val SAK_ID = "12345"
    }

    /**
     * P_BUC_05 DEL 1
     *
     * Flytskjema (utgående):
     *  https://confluence.adeo.no/pages/viewpage.action?pageId=387092731
     */


    /* ============================
     * SCENARIO 1
     * ============================ */

    @Nested
    inner class Scenario1 {
        @Test
        fun `1 person i SED fnr finnes men ingen bestemsak Så journalføres på ID_OG_FORDELING`() {
            testRunner(FNR_OVER_60, saker = emptyList(), sakId = SAK_ID) {
                // forvent tema == PEN og enhet 4303
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED, men fnr er feil`() {
            testRunner(fnr = "123456789102356878546525468432") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED, men fnr mangler`() {
            testRunner(fnr = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 2
     * ============================ */

    @Nested
    inner class Scenario2 {
        @Test
        fun `2 personer i SED, fnr og rolle mangler, men saksnummer finnes`() {
            testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = null, rolle = "01", sakId = SAK_ID) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har fnr, mangler rolle og saksnummer`() {
            testRunnerFlerePersoner(FNR_VOKSEN, fnrAnnenPerson = null, rolle = null, sakId = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, mangler fnr og saksnummer, men rolle finnes`() {
            testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = null, rolle = "01", sakId = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, mangler fnr, rolle, og sakId`() {
            testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = null, rolle = null, sakId = null) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 3
     * ============================ */

    @Nested
    inner class Scenario3 {
        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, men finnes flere sakstyper`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "1111", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2222", sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, men finnes flere sakstyper, bosatt utland`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "1111", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2222", sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunner(FNR_OVER_60, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL`() {
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, bosatt utland`() {
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))

            testRunner(FNR_OVER_60, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 4
     * ============================ */

    @Nested
    inner class Scenario4 {
        @Test
        fun `1 person i SED fnr finnes og saktype er GENRL, med flere sakstyper, person bosatt Norge`() {
            val saker = listOf(
                    SakInformasjon(SAK_ID, YtelseType.GENRL, SakStatus.TIL_BEHANDLING),
                    SakInformasjon("1240128", YtelseType.BARNEP, SakStatus.TIL_BEHANDLING)
            )

            testRunner(FNR_VOKSEN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, med flere sakstyper, person bosatt utland`() {
            val saker = listOf(
                    SakInformasjon(SAK_ID, YtelseType.GENRL, SakStatus.TIL_BEHANDLING),
                    SakInformasjon("124123", YtelseType.BARNEP, SakStatus.TIL_BEHANDLING)
            )

            testRunner(FNR_VOKSEN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED der fnr finnes, rolle er 02, land er Sverige og bestemsak finner flere saker Så journalføres det manuelt på tema PENSJON og enhet PENSJON_UTLAND`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN_2, saker, rolle = "02", sakId = SAK_ID, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes, rolle er 02 og bestemsak finner flere sak Så journalføres manuelt på tema PENSJON og enhet NFP_UTLAND_AALESUND`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.AVSLUTTET)
            )

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, saker, sakId = SAK_ID, rolle = "02") {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 5
     * ============================ */

    @Nested
    inner class Scenario5 {
        @Test
        fun `1 person i SED fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2131123123", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
            )

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes og bestemsak finner sak UFORE Så journalføres automatisk på tema UFORETRYGD`() {
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING))

            testRunner(FNR_VOKSEN, saker) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_OVER_60, saker) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, saker) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 6
     * ============================ */

    @Nested
    inner class Scenario6 {
        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, mangler sakInformasjon`() {
            val fnrAnnenPerson = FNR_BARN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson, saker = emptyList(), rolle = "01", sakId = null) {
                // forvent tema == PEN og enhet 4303
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }

            // Har SakID, men finner ingen saker
            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson, saker = emptyList(), rolle = "01", sakId = SAK_ID) {
                // forvent tema == PEN og enhet 4303
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, bosatt i utland, mangler sakInformasjon`() {
            testRunnerFlerePersoner(
                    fnr = FNR_OVER_60,
                    fnrAnnenPerson = FNR_BARN,
                    saker = emptyList(),
                    sakId = null,
                    rolle = "01"
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, og bestemsak finner sak ALDER`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val fnrAnnenPerson = FNR_VOKSEN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson = fnrAnnenPerson, saker = saker, sakId = SAK_ID, rolle = "01") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, og bestemsak finner sak UFØRE`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val fnrAnnenPerson = FNR_VOKSEN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson = fnrAnnenPerson, saker = saker, sakId = SAK_ID, rolle = "01") {
                // forvent tema == PEN og enhet 9999
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }
        }
    }


    /* ============================
     * SCENARIO 7
     * ============================ */

    @Nested
    inner class Scenario7 {
        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak UFØRE Så journalføres automatisk på tema UFORETRYGD`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, saker, SAK_ID, rolle = "02") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = YtelseType.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, saker, SAK_ID, rolle = "02") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak GENRL`() {
            val saker = listOf(
                    SakInformasjon(sakId = "234123123", sakType = YtelseType.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123123123", sakType = YtelseType.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, rolle = "02", sakId = SAK_ID, saker = saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }
    }


    /* ============================
     * SCENARIO 8
     * ============================ */

    @Nested
    inner class Scenario8 {
        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er ALDER`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er UFORE`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er OMSORG`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er OMSORG, ignorerer diskresjonskode`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }


    /* ============================
     * SCENARIO 9
     * ============================ */

    @Nested
    inner class Scenario9 {
        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er BARNEP`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV, ignorerer Diskresjonskode 6`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV, ignorerer Diskresjonskode`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = YtelseType.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = YtelseType.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", diskresjonkode = Diskresjonskode.SPFO) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE", diskresjonkode = Diskresjonskode.SPFO) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = "03", land = "SWE", diskresjonkode = Diskresjonskode.SPSF) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype mangler`() {
            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "03") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = "03", land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    private fun testRunnerFlerePersoner(fnr: String?,
                                        fnrAnnenPerson: String?,
                                        saker: List<SakInformasjon> = emptyList(),
                                        sakId: String? = SAK_ID,
                                        diskresjonkode: Diskresjonskode? = null,
                                        land: String = "NOR",
                                        rolle: String?, // Barn som standard
                                        block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedJson(SedType.P8000, fnr, createAnnenPersonJson(fnr = fnrAnnenPerson, rolle = rolle), sakId)
        initCommonMocks(sed)

        if (fnr != null) {
            every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Mamma forsørger", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(AKTOER_ID)
        }

        if (fnrAnnenPerson != null) {
            every { personV3Service.hentPerson(fnrAnnenPerson) } returns createBrukerWith(fnrAnnenPerson, "Barn", "Diskret", land, "1213", diskresjonkode?.name)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnrAnnenPerson)) } returns AktoerId(AKTOER_ID_2)
        }

        if (rolle == "01")
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID_2) } returns saker
        else
            every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker

        val (journalpost, _) = initJournalPostRequestSlot()

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        // forvent tema == PEN og enhet 2103
        val oppgaveMelding = mapJsonToAny(meldingSlot.captured, typeRefs<OppgaveMelding>())
        assertEquals(HendelseType.SENDT, oppgaveMelding.hendelseType)

        val request = journalpost.captured
        assertEquals(JournalpostType.UTGAAENDE, request.journalpostType)

        block(request)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }

        val antallPersoner = listOfNotNull(fnr, fnrAnnenPerson).size
        verify(exactly = antallPersoner) { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, any<NorskIdent>()) }

        val antallKallHentPerson = (antallPersoner + (1.takeIf { antallPersoner > 0 && rolle != null } ?: 0)) * 2
        verify(exactly = antallKallHentPerson) { personV3Service.hentPerson(any()) }

        val antallKallTilPensjonSaklist = if (antallPersoner > 0 && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    private fun testRunner(fnr: String?,
                           saker: List<SakInformasjon> = emptyList(),
                           sakId: String? = SAK_ID,
                           land: String = "NOR",
                           block: (OpprettJournalpostRequest) -> Unit
    ) {
        val sed = createSedJson(SedType.P8000, fnr, null, sakId)
        initCommonMocks(sed)

        if (fnr != null) {
            every { personV3Service.hentPerson(fnr) } returns createBrukerWith(fnr, "Fornavn", "Etternavn", land)
            every { aktoerregisterService.hentGjeldendeIdent(IdentGruppe.AktoerId, NorskIdent(fnr)) } returns AktoerId(AKTOER_ID)
        }

        every { fagmodulKlient.hentPensjonSaklist(AKTOER_ID) } returns saker
        every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

        val (journalpost, _) = initJournalPostRequestSlot(true)

        val hendelse = createHendelseJson(SedType.P8000)

        val meldingSlot = slot<String>()
        every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

        listener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

        block(journalpost.captured)

        verify(exactly = 1) { fagmodulKlient.hentAlleDokumenter(any()) }
        verify(exactly = 1) { euxKlient.hentSed(any(), any()) }
        verify(exactly = 0) { bestemSakKlient.kallBestemSak(any()) }

        val gyldigFnr: Boolean = sakId != null && fnr != null && fnr.length == 11
        val antallKallTilPensjonSaklist = if (gyldigFnr && sakId != null) 1 else 0
        verify(exactly = antallKallTilPensjonSaklist) { fagmodulKlient.hentPensjonSaklist(any()) }

        clearAllMocks()
    }

    private fun initCommonMocks(sed: String) {
        every { fagmodulKlient.hentAlleDokumenter(any()) } returns getResource("fagmodul/alldocumentsids.json")
        every { euxKlient.hentSed(any(), any()) } returns sed
        every { euxKlient.hentSedDokumenter(any(), any()) } returns getResource("pdf/pdfResponseUtenVedlegg.json")
    }

    private fun getResource(resourcePath: String): String? =
            javaClass.classLoader.getResource(resourcePath)!!.readText()
}
