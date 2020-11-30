package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import no.nav.eessi.pensjon.models.Enhet.AUTOMATISK_JOURNALFORING
import no.nav.eessi.pensjon.models.Enhet.ID_OG_FORDELING
import no.nav.eessi.pensjon.models.Enhet.NFP_UTLAND_AALESUND
import no.nav.eessi.pensjon.models.Enhet.PENSJON_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLAND
import no.nav.eessi.pensjon.models.Enhet.UFORE_UTLANDSTILSNITT
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.models.YtelseType
import no.nav.eessi.pensjon.personidentifisering.helpers.Diskresjonskode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_05 - Utgående journalføring - IntegrationTest")
internal class PBuc05IntegrationTest : JournalforingTestBase() {

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
                    SakInformasjon(sakId = "98989898", sakType = YtelseType.GENRL, sakStatus = SakStatus.LOPENDE)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, rolle = "02", sakId = SAK_ID, saker = saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
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
}
