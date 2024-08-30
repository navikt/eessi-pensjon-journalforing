package no.nav.eessi.pensjon.listeners

import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.EuxService
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType.P15000
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.*
import no.nav.eessi.pensjon.gcp.GcpStorageService
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPDLPerson
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SedMottattListenerLettTest {
    private val gcpStorageService = mockk<GcpStorageService>()
    private val journalforingService = mockk<JournalforingService>()
    private val personidentifiseringService = mockk<PersonidentifiseringService>()
    private val euxService = mockk<EuxService>()
    private val fagmodulService = mockk<FagmodulService>(relaxed = true)

    private val sedMottattListener = SedMottattListener(
        journalforingService = journalforingService,
        personidentifiseringService = personidentifiseringService,
        euxService = euxService,
        fagmodulService = fagmodulService,
        bestemSakService = mockk(),
        gcpStorageService = gcpStorageService,
        profile = "test"
    )

    @Test
    fun `tester ut om vi kan populere gjenlevende fra flere bucer enn kun 10 og 02`() {
        val sed = P15000(
            type = P15000, nav = Nav(
                bruker = Bruker(
                    person = Person(
                        fornavn = "Testfornavn",
                        etternavn = "Testetternavn",
                        foedselsdato = "1952-02-25",
                        pin = listOf(
                            PinItem(
                                identifikator = "NO121212"
                            )
                        )
                    )

                )
            ), pensjon = P15000Pensjon(
                gjenlevende = Bruker(
                    person = Person(
                        rolle = "01"
                    )
                )
            )
        )

        val buc = Buc(processDefinitionName = P_BUC_10.name)
        val listofSeds = listOf(Pair("", sed))
        val listofCancelledSeds = listOf(sed)
        val identifisertePersonerPDL = listOf(identifisertPDLPerson())
        val identifisertPersonPDL = identifisertPDLPerson()
        val sakInformasjon = SakInformasjon(
            sakId = "74389487",
            sakType = SakType.GJENLEV,
            sakStatus = SakStatus.LOPENDE
        )

        every { gcpStorageService.journalFinnes(any()) } returns false
        every { euxService.hentBuc(any()) } returns buc
        every { euxService.hentSedMedGyldigStatus(any(), any()) } returns listofSeds
        every { euxService.hentAlleKansellerteSedIBuc(any(), any()) } returns listofCancelledSeds
        every { personidentifiseringService.finnesPersonMedAdressebeskyttelseIBuc(any()) } returns false
        every { personidentifiseringService.hentIdentifisertePersoner(any()) } returns identifisertePersonerPDL
        every { personidentifiseringService.hentIdentifisertPerson(any(), any(), any(), any(), any(), any()) } returns identifisertPersonPDL
        every { personidentifiseringService.hentFodselsDato(any(), any()) } returns LocalDate.of(56, 8, 25)
        every { fagmodulService.hentSakIdFraSED(any()) } returns "74389487"
        every { euxService.hentSaktypeType(any(), any()) } returns SakType.GJENLEV
        justRun { journalforingService.journalfor(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
        every { fagmodulService.hentPensjonSakFraPesys(any(), any()) } returns sakInformasjon


        sedMottattListener.behandleSedHendelse(enSedHendelse())

        verify(exactly = 1) { fagmodulService.hentSakIdFraSED(any()) }

    }

    private fun identifisertPDLPerson() = IdentifisertPDLPerson(
        aktoerId = "123",
        fnr = Fodselsnummer.fra("09035225916"),
        geografiskTilknytning = "1234",
        landkode = "NO",
        personRelasjon = null
    )

    private fun enSedHendelse() = SedHendelse(
        sektorKode = "P",
        bucType = P_BUC_10,
        sedType = P15000,
        rinaSakId = "74389487",
        rinaDokumentId = "743982",
        rinaDokumentVersjon = "1",
        avsenderNavn = "Svensk institusjon",
        avsenderLand = "SE"
    )

}
