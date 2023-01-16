package no.nav.eessi.pensjon.integrasjonstest.saksflyt

import io.mockk.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.BucType
import no.nav.eessi.pensjon.eux.model.buc.BucType.*
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P5000
import no.nav.eessi.pensjon.eux.model.sed.P8000
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.handler.OppgaveMelding
import no.nav.eessi.pensjon.handler.OppgaveType
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.Enhet.*
import no.nav.eessi.pensjon.models.Tema.PENSJON
import no.nav.eessi.pensjon.models.Tema.UFORETRYGD
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.NorskIdent
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("P_BUC_05 - IntegrationTest")
internal class PBuc05IntegrationTest : JournalforingTestBase() {

    /**
     * Flytskjema utgående:
     * https://confluence.adeo.no/pages/viewpage.action?pageId=387092731
     *
     * Flytskjema inngående:
     * https://confluence.adeo.no/pages/viewpage.action?pageId=387092731
     */


    /* ============================
     * UTGÅENDE
     * ============================ */

    @Nested
    @DisplayName("Utgående - Scenario 1")
    inner class Scenario1Utgaende {
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


    @Nested
    @DisplayName("Utgående - Scenario 2")
    inner class Scenario2Utgaende {
        @Test
        fun `2 personer i SED, fnr og rolle mangler, men saksnummer finnes`() {
            testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = null, rolle = Rolle.ETTERLATTE, sakId = SAK_ID) {
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
            testRunnerFlerePersoner(fnr = null, fnrAnnenPerson = null, rolle = Rolle.ETTERLATTE, sakId = null) {
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


    @Nested
    @DisplayName("Utgående - Scenario 3")
    inner class Scenario3Utgaende {
        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, men finnes flere sakstyper`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "1111", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2222", sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes, saktype er GENRL, men finnes flere sakstyper, bosatt utland`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "1111", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2222", sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
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
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))

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
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING))

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


    @Nested
    @DisplayName("Utgående - Scenario 4")
    inner class Scenario4Utgaende {
        @Test
        fun `1 person i SED fnr finnes og saktype er GENRL, med flere sakstyper, person bosatt Norge`() {
            val saker = listOf(
                    SakInformasjon(SAK_ID, Saktype.GENRL, SakStatus.TIL_BEHANDLING),
                    SakInformasjon("1240128", Saktype.BARNEP, SakStatus.TIL_BEHANDLING)
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
                    SakInformasjon(SAK_ID, Saktype.GENRL, SakStatus.TIL_BEHANDLING),
                    SakInformasjon("124123", Saktype.BARNEP, SakStatus.TIL_BEHANDLING)
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
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN_2, saker, rolle = Rolle.FORSORGER, sakId = SAK_ID, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes, rolle er 02 og bestemsak finner flere sak Så journalføres manuelt på tema PENSJON og enhet NFP_UTLAND_AALESUND`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET)
            )

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, saker, sakId = SAK_ID, rolle = Rolle.FORSORGER) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 5")
    inner class Scenario5Utgaende {
        @Test
        fun `1 person i SED fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = "2131123123", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)
            )

            testRunner(FNR_OVER_60, saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `1 person i SED fnr finnes og bestemsak finner sak UFORE Så journalføres automatisk på tema UFORETRYGD`() {
            val saker = listOf(SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING))

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


    @Nested
    @DisplayName("Utgående - Scenario 6")
    inner class Scenario6Utgaende {
        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, mangler sakInformasjon`() {
            val fnrAnnenPerson = FNR_BARN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson, saker = emptyList(), rolle = Rolle.ETTERLATTE, sakId = null) {
                // forvent tema == PEN og enhet 4303
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }

            // Har SakID, men finner ingen saker
            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson, saker = emptyList(), rolle = Rolle.ETTERLATTE, sakId = SAK_ID) {
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
                    rolle = Rolle.ETTERLATTE
            ) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, og bestemsak finner sak ALDER`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123", sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val fnrAnnenPerson = FNR_VOKSEN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson = fnrAnnenPerson, saker = saker, sakId = SAK_ID, rolle = Rolle.ETTERLATTE) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle GJENLEV, fnr finnes, og bestemsak finner sak UFØRE`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val fnrAnnenPerson = FNR_VOKSEN

            testRunnerFlerePersoner(FNR_OVER_60, fnrAnnenPerson = fnrAnnenPerson, saker = saker, sakId = SAK_ID, rolle = Rolle.ETTERLATTE) {
                // forvent tema == PEN og enhet 9999
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(fnrAnnenPerson, it.bruker?.id)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 7")
    inner class Scenario7Utgaende {
        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak UFØRE Så journalføres automatisk på tema UFORETRYGD`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, saker, SAK_ID, rolle = Rolle.FORSORGER) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak ALDER Så journalføres automatisk på tema PENSJON`() {
            val saker = listOf(
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123123123", sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "34234123", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, saker, SAK_ID, rolle = Rolle.FORSORGER) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }

        @Test
        fun `2 personer i SED, har rolle familiemedlem, fnr finnes og bestemsak finner sak GENRL`() {
            val saker = listOf(
                    SakInformasjon(sakId = "234123123", sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                    SakInformasjon(sakId = "123123123", sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                    SakInformasjon(sakId = "98989898", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)
            )

            val forsikredeFnr = FNR_OVER_60

            testRunnerFlerePersoner(forsikredeFnr, FNR_VOKSEN, rolle = Rolle.FORSORGER, sakId = SAK_ID, saker = saker) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
                assertEquals(forsikredeFnr, it.bruker?.id)
            }
        }

    }


    @Nested
    @DisplayName("Utgående - Scenario 8")
    inner class Scenario8Utgaende {
        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er ALDER`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er UFORE`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.UFOREP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(UFORETRYGD, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er OMSORG`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED, har rolle barn 03 og sak er OMSORG, ignorerer diskresjonskode`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.GENRL, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE", harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(AUTOMATISK_JOURNALFORING, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Utgående - Scenario 9")
    inner class Scenario9Utgaende {
        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er BARNEP`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.OMSORG, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.BARNEP, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV, ignorerer Diskresjonskode 6`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE", harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype er GJENLEV, ignorerer Diskresjonskode`() {
            val saker = listOf(
                    SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.TIL_BEHANDLING),
                    SakInformasjon(sakId = SAK_ID, sakType = Saktype.GJENLEV, sakStatus = SakStatus.TIL_BEHANDLING)
            )

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, harAdressebeskyttelse = false) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE", harAdressebeskyttelse = false) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, saker, rolle = Rolle.BARN, land = "SWE", harAdressebeskyttelse = true) {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `2 personer i SED fnr finnes og rolle er barn, og saktype mangler`() {
            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.BARN) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.BARN, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }

    @Nested
    @DisplayName("Utgående - Scenario 13")
    inner class Scenario13Utgaende {
        @Test
        fun `2 personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.ETTERLATTE), null))

            initCommonMocks(sed, getMockDocuments(), getDokumentfilerUtenVedlegg())

            val voksen = createBrukerWith(FNR_OVER_60, "Voksen", "Vanlig", "NOR", "1213", aktorId = AKTOER_ID)
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
            Assertions.assertNull(request.bruker)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Hente opp korrekt fnr fra P8000 som er sendt fra oss med flere P8000 i BUC`() {
            val fnr = "28127822044"
            val afnr = "05127921999"
            val aktoera = "${fnr}1111"
            val aktoerf = "${fnr}0000"
            val saknr = "1223123123"

            val sedP8000_2 = SED.generateSedToClass<P8000>(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = afnr, rolle = Rolle.ETTERLATTE), saknr))
            val sedP8000sendt = SED.generateSedToClass<P8000>(createSed(SedType.P8000, fnr, createAnnenPerson(fnr = afnr, rolle = Rolle.ETTERLATTE), saknr))
            val sedP8000recevied = SED.generateSedToClass<P8000>(createSed(SedType.P8000, null, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE), null))

            val dokumenter = mapJsonToAny<List<ForenkletSED>>(getResource("/fagmodul/alldocumentsids_P_BUC_05_multiP8000.json"))

            every { euxKlient.hentBuc(any()) } returns Buc(id = "2", processDefinitionName = "P_BUC_01", documents = bucDocumentsFrom(dokumenter))
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000_2.toJson() andThen sedP8000sendt.toJson() andThen sedP8000recevied.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.harAdressebeskyttelse(any(), any()) } returns false
            every { personService.hentPerson(NorskIdent(afnr)) } returns createBrukerWith(afnr, "Lever", "Helt i live", "NOR", aktorId = aktoera)
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Død", "Helt Død", "NOR", aktorId = aktoerf)


            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = saknr, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "34234123", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET)
            )
            every { fagmodulKlient.hentPensjonSaklist(aktoera) } returns saker
            every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

            val (journalpost, _) = initJournalPostRequestSlot(true)
            val hendelse = createHendelseJson(SedType.P6000, P_BUC_05)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured

            // forvent tema == PEN og enhet 9999
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
            assertEquals(afnr, request.bruker?.id)

            verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 3) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()

        }

        @Test
        fun `Scenario 13 - 0 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
            val sedP8000recevied = SED.generateSedToClass<P8000>(createSed(SedType.P8000, null, fdato = "1955-07-11"))

            val sedP5000sent = SED.generateSedToClass<P5000>(createSedPensjon(SedType.P5000, FNR_OVER_60, gjenlevendeFnr = FNR_BARN))

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns createBrukerWith(FNR_BARN, "Lever", "Helt i live", "NOR", aktorId = AKTOER_ID)
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns createBrukerWith(FNR_OVER_60, "Død", "Helt Død", "NOR", aktorId = AKTOER_ID_2)
            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 1 Sed sendes som svar med flere personer pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
            val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
            val sedP5000sent = createSed(SedType.P5000, null, fdato = "1955-07-11")

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()


            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PEN og enhet ID OG FORDELING `() {
            val fnr = "07115521999"
            val aktoer = "${fnr}111"
            val sedP8000recevied = createSed(SedType.P8000, null, fdato = "1955-07-11")
            val sedP5000sent = createSed(SedType.P5000, fnr)

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()

            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR", aktorId = aktoer)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P5000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P5000", oppgaveMelding.sedType?.name)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()

        }

        @Test
        fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og sakid i sed journalføres automatisk `() {
            val fnr = FNR_VOKSEN
            val aktoer = "${fnr}111"
            val sakid = SAK_ID
            val sedP8000recevied = mapJsonToAny<P8000>(createSed(SedType.P8000, null, fdato = Fodselsnummer.fra(FNR_VOKSEN)?.getBirthDateAsIso()).toJson())
            val sedP9000sent = createSed(SedType.P9000, fnr, eessiSaknr = sakid)

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P9000, SedStatus.SENT)
            )

            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP9000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "KRAFTIG ", "VEGGPRYD", "NOR", aktorId = aktoer)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

            val hendelse = createHendelseJson(SedType.P9000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(AUTOMATISK_JOURNALFORING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P9000", oppgaveMelding.sedType?.name)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)
            assertEquals(fnr, request.bruker?.id!!)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 2 Sed sendes som svar med fnr pa tidligere mottatt P8000 ingen ident, svar sed med fnr og ingen sakid i sed journalføres UFO `() {
            val fnr = FNR_VOKSEN
            val aktoer = "${fnr}111"
            val sakid = SAK_ID
            val sedP8000recevied = mapJsonToAny<P8000>(createSed(SedType.P8000, null, fdato = "1955-07-11").toJson())
            val sedP9000sent = createSed(SedType.P9000, fnr, eessiSaknr = sakid)

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P9000, SedStatus.SENT)
            )

            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123", sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP9000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()

            every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "KRAFTIG ", "VEGGPRYD", "NOR", aktorId = aktoer)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()

            val hendelse = createHendelseJson(SedType.P9000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)
            assertEquals("P9000", oppgaveMelding.sedType?.name)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
            assertEquals(fnr, request.bruker?.id!!)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 3 Sed sendes som svar med fnr og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema NFP UTLAND AALESUND`() {
            val fnr = FNR_VOKSEN
            val sakid = "1231232323"
            val aktoer = "${fnr}111"
            val sedP8000recevied = mapJsonToAny<P8000>(createSed(SedType.P8000, null, fdato = "1955-07-11").toJson())
            val sedP5000sent = mapJsonToAny<P5000>(createSed(SedType.P5000, fnr, eessiSaknr = sakid).toJson())

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR", aktorId = aktoer)

            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "34234234234234", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)
            )
            every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P6000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 4 Sed sendes som svar med fnr utland og sak finnes og er GENRL pa tidligere mottatt P8000, opprettes en journalføringsoppgave på tema PENSJON UTLAND`() {
            val fnr = FNR_VOKSEN
            val sakid = "1231232323"
            val aktoer = "${fnr}111"
            val sedP8000recevied = mapJsonToAny<P8000>((createSed(SedType.P8000, null, fdato = "1955-07-11").toJson()))
            val sedP5000sent = mapJsonToAny<P5000>(createSed(SedType.P5000, fnr, eessiSaknr = sakid).toJson())

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Lever", "Helt i live", "SWE", aktorId = aktoer)

            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "123123123123123123", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)
            )
            every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()
            val (journalpost, journalpostResponse) = initJournalPostRequestSlot()
            val hendelse = createHendelseJson(SedType.P6000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured
            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)
            assertEquals(ID_OG_FORDELING, oppgaveMelding.tildeltEnhetsnr)
            assertEquals(journalpostResponse.journalpostId, oppgaveMelding.journalpostId)

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Scenario 13 - 5 Sed sendes som svar med fnr og sak finnes og er UFOREP pa tidligere mottatt P8000, journalføres automatisk`() {
            val fnr = FNR_VOKSEN
            val sakid = "1231232323"
            val aktoer = "${fnr}111"
            val sedP8000recevied = mapJsonToAny<P8000>(createSed(SedType.P8000, null, fdato = Fodselsnummer.fra(FNR_VOKSEN)?.getBirthDateAsIso()).toJson())
            val sedP5000sent = mapJsonToAny<P5000>(createSed(SedType.P5000, fnr, eessiSaknr = sakid).toJson())

            val alleDocumenter = listOf(
                ForenkletSED("10001", SedType.P8000, SedStatus.RECEIVED),
                ForenkletSED("30002", SedType.P5000, SedStatus.SENT)
            )

            every { euxKlient.hentBuc(any()) } returns bucFrom(P_BUC_05, alleDocumenter)
            every { euxKlient.hentSedJson(any(), any()) } returns sedP8000recevied.toJson() andThen sedP5000sent.toJson()
            every { euxKlient.hentAlleDokumentfiler(any(), any()) } returns getDokumentfilerUtenVedlegg()
            every { personService.hentPerson(NorskIdent(fnr)) } returns createBrukerWith(fnr, "Lever", "Helt i live", "NOR", aktorId = aktoer)

            val saker = listOf(
                SakInformasjon(sakId = "34234234", sakType = Saktype.OMSORG, sakStatus = SakStatus.LOPENDE),
                SakInformasjon(sakId = "23232312", sakType = Saktype.GENRL, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = sakid, sakType = Saktype.UFOREP, sakStatus = SakStatus.LOPENDE)
            )
            every { fagmodulKlient.hentPensjonSaklist(aktoer) } returns saker
            every { journalpostKlient.oppdaterDistribusjonsinfo(any()) } returns Unit

            val (journalpost, _) = initJournalPostRequestSlot(true)
            val hendelse = createHendelseJson(SedType.P6000, P_BUC_05)

            sendtListener.consumeSedSendt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val request = journalpost.captured

            assertEquals("UTGAAENDE", request.journalpostType.name)
            assertEquals(UFORETRYGD, request.tema)
            assertEquals(AUTOMATISK_JOURNALFORING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 2) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }
            verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }

            clearAllMocks()
        }
    }

    /* ============================
     * INNGÅENDE
     * ============================ */

    @Nested
    @DisplayName("Inngående - Scenario 1")
    inner class Scenario1Inngaende {
        @Test
        fun `Kun én person, mangler FNR`() {
            testRunner(fnr = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Kun én person, ugyldig FNR`() {
            testRunner(fnr = "1244091349018340918341029", hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(ID_OG_FORDELING, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Inngående - Scenario 2")
    inner class Scenario2Inngaende {
        @Test
        fun `Manglende eller feil i FNR, DNR for forsikret - to personer angitt, ROLLE 03`() {
            val sed = createSed(SedType.P8000, null, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.BARN), SAK_ID)
            initCommonMocks(sed)

            val barn = createBrukerWith(FNR_BARN, "Barn", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns barn

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            // forvent tema == PEN og enhet 9999
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Manglende eller feil i FNR, DNR for forsikret - to personer angitt, ROLLE 02`() {
            val sed = createSed(SedType.P8000, null, createAnnenPerson(fnr = FNR_VOKSEN, rolle = Rolle.FORSORGER), SAK_ID)
            initCommonMocks(sed)

            val voksen = createBrukerWith(FNR_VOKSEN, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns voksen

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }
    }

    @Nested
    @DisplayName("Inngående - Scenario 3")
    inner class Scenario3Inngaende {
        @Test
        fun `Manglende eller feil FNR-DNR - to personer angitt - etterlatte mangler fnr medfører bruk av sokPerson`() {

            val mockEttrelatte = createBrukerWith(FNR_VOKSEN, "Voksen", "Etterlatte", "NOR", "1213", aktorId = "123123123123")

            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, FNR_VOKSEN_2, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE, pdlPerson = mockEttrelatte, fdato = Fodselsnummer.fra(FNR_VOKSEN)?.getBirthDateAsIso()), SAK_ID))

            val documetAction = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P8000, SedStatus.RECEIVED))
            initCommonMocks(sed, documetAction)

            val hendelse = createHendelseJson(SedType.P8000)

            every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(FNR_VOKSEN, IdentGruppe.FOLKEREGISTERIDENT))
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns createBrukerWith(FNR_VOKSEN_2, "Voksen", "Avdod", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns mockEttrelatte

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            // forvent tema == PEN og enhet 9999
            assertEquals(PENSJON, request.tema)
            assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Manglende eller feil FNR-DNR på forsikret medfører bruk av sokPerson`() {

            val mockForsikret = createBrukerWith(FNR_VOKSEN, "Voksen", "Etternavn", "NOR", "1213", aktorId = "123123123123")
            val sed = mapJsonToAny<P8000>(createSed(SedType.P8000, null, pdlPerson = mockForsikret, fdato = Fodselsnummer.fra(FNR_VOKSEN)?.getBirthDateAsIso()).toJson())

            val documetAction = listOf(ForenkletSED("b12e06dda2c7474b9998c7139c841646", SedType.P8000, SedStatus.RECEIVED))

            initCommonMocks(sed, documetAction)
            val hendelse = createHendelseJson(SedType.P8000)

            every { personService.sokPerson(any()) } returns setOf(IdentInformasjon(FNR_VOKSEN, IdentGruppe.FOLKEREGISTERIDENT))
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN)) } returns mockForsikret

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `Manglende eller feil FNR-DNR - to personer angitt - etterlatte med feil FNR for annen person, eller soker`() {
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, FNR_VOKSEN_2, createAnnenPerson(fnr = null, rolle = Rolle.ETTERLATTE), SAK_ID))
            initCommonMocks(sed)

            val voksen = createBrukerWith(FNR_VOKSEN_2, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(FNR_VOKSEN_2)) } returns voksen

            val hendelse = createHendelseJson(SedType.P8000)
            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            // forvent tema == PEN og enhet 9999
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }
    }


    @Nested
    @DisplayName("Inngående - Scenario 4")
    inner class Scenario4Inngaende{
        @Test
        fun `Én person, gyldig fnr`() {
            testRunner(FNR_OVER_60, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `Én person, gyldig fnr, bosatt utland`() {
            testRunner(FNR_OVER_60, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_VOKSEN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
            }

            testRunner(FNR_BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }
    }


    @Nested
    @DisplayName("Inngående - Scenario 5")
    inner class Scenario5Inngaende {
        @Test
        fun `To personer angitt, gyldig fnr, rolle er 03, bosatt norge`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                assertEquals(FNR_OVER_60, it.bruker!!.id)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker!!.id)
            }
        }

        @Test
        fun `To personer angitt, gyldig fnr, rolle er 02, bosatt norge`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
                assertEquals(FNR_OVER_60, it.bruker!!.id)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLANDSTILSNITT, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker!!.id)
            }
        }

        @Test
        fun `To personer angitt, gyldig fnr, rolle er 03, bosatt utland`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
                assertEquals(FNR_OVER_60, it.bruker!!.id)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.BARN, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker!!.id)
            }
        }

        @Test
        fun `To personer angitt, gyldig fnr, rolle er 02, bosatt utland`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
                assertEquals(FNR_OVER_60, it.bruker!!.id)
            }

            testRunnerFlerePersoner(FNR_VOKSEN, FNR_BARN, rolle = Rolle.FORSORGER, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(UFORE_UTLAND, it.journalfoerendeEnhet)
                assertEquals(FNR_VOKSEN, it.bruker!!.id)
            }
        }

        @Test
        fun `To personer angitt, gyldig fnr og ugyldig annenperson, rolle er 02, bosatt utland del 1`() {
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, FNR_OVER_60, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null))
            initCommonMocks(sed)

            val voksen = createBrukerWith(FNR_OVER_60, "Voksen", "Vanlig", "SWE", "1213")
            every { personService.hentPerson(NorskIdent(FNR_OVER_60)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(PENSJON_UTLAND, request.journalfoerendeEnhet)
            assertEquals(FNR_OVER_60, request.bruker!!.id)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `To personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt utland del 2`() {
            val valgtFNR = FNR_VOKSEN
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null))
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "SWE", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(UFORE_UTLAND, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `To personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 3`() {
            val valgtFNR = FNR_VOKSEN
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null))
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(UFORE_UTLANDSTILSNITT, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `To personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 02, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.FORSORGER), null))
            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(NFP_UTLAND_AALESUND, request.journalfoerendeEnhet)
            assertEquals(valgtFNR, request.bruker!!.id)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

        @Test
        fun `To personer angitt, gyldig fnr og ufgyldig fnr annenperson, rolle er 01, bosatt Norge del 4`() {
            val valgtFNR = FNR_OVER_60
            val sed = SED.generateSedToClass<P8000>(createSed(SedType.P8000, valgtFNR, createAnnenPerson(fnr = FNR_BARN, rolle = Rolle.ETTERLATTE), null))

            initCommonMocks(sed)

            val voksen = createBrukerWith(valgtFNR, "Voksen", "Vanlig", "NOR", "1213")
            every { personService.hentPerson(NorskIdent(valgtFNR)) } returns voksen
            every { personService.hentPerson(NorskIdent(FNR_BARN)) } returns null

            val hendelse = createHendelseJson(SedType.P8000)

            val meldingSlot = slot<String>()
            every { oppgaveHandlerKafka.sendDefault(any(), capture(meldingSlot)).get() } returns mockk()

            val (journalpost, _) = initJournalPostRequestSlot()

            mottattListener.consumeSedMottatt(hendelse, mockk(relaxed = true), mockk(relaxed = true))

            val oppgaveMelding = mapJsonToAny<OppgaveMelding>(meldingSlot.captured)

            assertEquals(OppgaveType.JOURNALFORING, oppgaveMelding.oppgaveType)

            val request = journalpost.captured
            assertEquals(PENSJON, request.tema)
            assertEquals(ID_OG_FORDELING, request.journalfoerendeEnhet)
            Assertions.assertNull(request.bruker)

            verify(exactly = 1) { euxKlient.hentBuc(any()) }
            verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
            verify(exactly = 1) { euxKlient.hentAlleDokumentfiler(any(), any()) }

            clearAllMocks()
        }

    }


    @Nested
    @DisplayName("Inngående - Scenario 6")
    inner class Scenario6Inngaende {
        @Test
        fun `To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt norge`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT) {
                assertEquals(PENSJON, it.tema)
                assertEquals(NFP_UTLAND_AALESUND, it.journalfoerendeEnhet)
            }
        }

        @Test
        fun `To personer angitt, gyldig fnr, rolle 02 etterlatte, bosatt utland`() {
            testRunnerFlerePersoner(FNR_OVER_60, FNR_BARN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }

            testRunnerFlerePersoner(FNR_OVER_60, FNR_VOKSEN, rolle = Rolle.ETTERLATTE, sakId = null, hendelseType = HendelseType.MOTTATT, land = "SWE") {
                assertEquals(PENSJON, it.tema)
                assertEquals(PENSJON_UTLAND, it.journalfoerendeEnhet)
            }
        }
    }


    private fun getResource(resourcePath: String): String =
        javaClass.getResource(resourcePath).readText()

    private fun getMockDocuments(): List<ForenkletSED> {
        return listOf(
            ForenkletSED("44cb68f89a2f4e748934fb4722721018", SedType.P2000, SedStatus.SENT),
            ForenkletSED("3009f65dd2ac4948944c6b7cfa4f179d", SedType.H121, null),
            ForenkletSED("9498fc46933548518712e4a1d5133113", SedType.H070, null)
        )
    }

}
