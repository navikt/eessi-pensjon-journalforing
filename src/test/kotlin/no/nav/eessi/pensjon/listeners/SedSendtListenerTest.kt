package no.nav.eessi.pensjon.listeners

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.buc.EuxService
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakStatus
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.journalforing.JournalforingService
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulService
import no.nav.eessi.pensjon.klienter.pesys.BestemSakService
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPersonPDL
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.kafka.support.Acknowledgment
import java.nio.file.Files
import java.nio.file.Paths

internal class SedSendtListenerTest {

    private val acknowledgment = mockk<Acknowledgment>(relaxUnitFun = true)
    private val cr = mockk<ConsumerRecord<String, String>>(relaxed = true)
    private val jouralforingService = mockk<JournalforingService>(relaxed = true)
    private val personidentifiseringService = mockk<PersonidentifiseringService>(relaxed = true)
    private val sedDokumentHelper = mockk<EuxService>(relaxed = true)
    private val bestemSakService = mockk<BestemSakService>(relaxed = true)
    private val fagmodulService = mockk<FagmodulService>(relaxed = true)

    private val sedListener = SedSendtListener(jouralforingService,
        personidentifiseringService,
        sedDokumentHelper,
        fagmodulService,
        bestemSakService,
        "test")

    @BeforeEach
    fun setup() {
        sedListener.initMetrics()
    }

    @Test
    fun `gitt en gyldig sedHendelse når sedSendt hendelse konsumeres så ack melding`() {
        sedListener.consumeSedSendt(String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/P_BUC_01_P2000.json"))), cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en ugyldig sedHendelse av type R_BUC_02 når sedSendt hendelse konsumeres, skal melding ackes`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/R_BUC_02_R005.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
    }

    @Test
    fun `gitt en exception ved sedSendt så kastes RunTimeException og meldig blir IKKE ack'et`() {
        assertThrows<RuntimeException> {
            sedListener.consumeSedSendt("Explode!", cr, acknowledgment)
        }
        verify { acknowledgment wasNot Called }
    }

    @Test
    fun `Mottat og sendt Sed med ugyldige verdier kaster exception`(){
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/BAD_BUC_01.json")))

        assertThrows<SedSendtRuntimeException> {
            sedListener.consumeSedSendt(hendelse, cr, acknowledgment)
        }
    }

    @Test
    fun `gitt en sendt sed som ikke tilhoerer pensjon saa blir den ignorert`() {
        val hendelse = String(Files.readAllBytes(Paths.get("src/test/resources/eux/hendelser/FB_BUC_01_F001.json")))
        sedListener.consumeSedSendt(hendelse, cr, acknowledgment)

        verify(exactly = 1) { acknowledgment.acknowledge() }
        verify { jouralforingService wasNot Called }
    }


    @Test
    fun `Ved kall til pensjonSakInformasjonSendt der vi har saktype GJENLEV i svar fra pensjonsinformasjon returneres sakInformasjon med saktype GJENLEV`() {
        val identifisertPerson = identifisertPersonPDL(
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = null,
            fnr = Fodselsnummer.fra("16115038745")
        )

        val sakInformasjon = SakInformasjon(
            sakId  = "25595454",
            sakType = SakType.GJENLEV,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        every { fagmodulService.hentPensjonSakFraPesys(any(), any()) } returns sakInformasjon

        val actual = sedListener.pensjonSakInformasjonSendt(identifisertPerson, BucType.P_BUC_05, null, emptyList() )

        assertEquals(SakType.GJENLEV, actual?.sakType)
    }

    @Test
    fun `Ved kall til pensjonSakInformasjonSendt der vi har saktype BARNEP i svar fra bestemSak returneres sakInformasjon med saktype BARNEP`() {
        val identifisertPerson = identifisertPersonPDL(
            landkode = "NO",
            geografiskTilknytning = null,
            personRelasjon = null,
            fnr = Fodselsnummer.fra("16115038745")
        )

        val sakInformasjon = SakInformasjon(
            sakId  = "25595454",
            sakType = SakType.BARNEP,
            sakStatus = SakStatus.LOPENDE,
            saksbehandlendeEnhetId = "",
            nyopprettet = false
        )

        every { fagmodulService.hentPensjonSakFraPesys(any(), any()) } returns null
        every { bestemSakService.hentSakInformasjonViaBestemSak(any(), any()) } returns sakInformasjon

        val actual = sedListener.pensjonSakInformasjonSendt(identifisertPerson, BucType.P_BUC_05, null, emptyList() )

        assertEquals(SakType.BARNEP, actual?.sakType)
    }

    fun identifisertPersonPDL(
        aktoerId: String = "3216549873215",
        personRelasjon: SEDPersonRelasjon?,
        landkode: String? = "",
        geografiskTilknytning: String? = "",
        fnr: Fodselsnummer? = null,
        personNavn: String = "Test Testesen"
    ): IdentifisertPersonPDL =
        IdentifisertPersonPDL(aktoerId, landkode, geografiskTilknytning, personRelasjon, fnr, personNavn = personNavn)

}
