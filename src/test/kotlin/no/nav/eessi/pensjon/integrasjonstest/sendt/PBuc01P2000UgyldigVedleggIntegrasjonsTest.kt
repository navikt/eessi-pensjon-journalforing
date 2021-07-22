package no.nav.eessi.pensjon.integrasjonstest.sendt

import no.nav.eessi.pensjon.integrasjonstest.MottattOgSendtIntegrationBase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

private const val SED_SENDT_TOPIC = "eessi-basis-sedSendt-v1"
private const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

@SpringBootTest(classes = [MottattOgSendtIntegrationBase.TestConfig::class],  value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(controlledShutdown = true, partitions = 1, topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC], brokerProperties = ["log.dir=out/embedded-kafkasendt"])
internal class PBuc01P2000UgyldigVedleggIntegrasjonsTest : MottattOgSendtIntegrationBase() {

    @Test
    fun `Når en sedSendt hendelse blir konsumert skal det opprettes journalføringsoppgave for pensjon SEDer`() {
        //gitt en person med frn og aktorid
        val person = mockPerson(aktorId = "1000101917311")

        CustomMockServer()
            .medJournalforing()
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medOppdaterDistribusjonsinfo()
            .medEuxGetRequest("/buc/147666", "/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json")
            .medEuxGetRequest("/buc/147666/sed/b12e06dda2c7474b9998c7139c666666/filer","/pdf/pdfResponseMedUgyldigVedlegg.json")


        //when
        initAndRunContainer().apply {
            send(
                SED_SENDT_TOPIC,
                javaClass.getResource("/eux/hendelser/P_BUC_01_P2000_MedUgyldigVedlegg.json").readText()
            )
            sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)
        }

        //then
        OppgaveMeldingVerification("429434378")
            .medHendelsetype("SENDT")
            .medSedtype("P2000")
            .medtildeltEnhetsnr("9999")
            .medAktorId(person.identer[0].ident)
    }
}
