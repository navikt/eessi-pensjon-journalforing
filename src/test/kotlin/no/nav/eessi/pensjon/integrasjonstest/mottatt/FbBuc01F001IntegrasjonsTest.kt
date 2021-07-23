package no.nav.eessi.pensjon.integrasjonstest.mottatt

import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles
import java.util.concurrent.TimeUnit

private const val SED_MOTTATT_TOPIC = "eessi-basis-sedMottatt-v1"
private const val OPPGAVE_TOPIC = "eessi-pensjon-oppgave-v1"

@SpringBootTest( classes = [IntegrasjonsBase.TestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka( topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC] )
    internal class FbBuc01F001IntegrasjonsIntegrasjons : IntegrasjonsBase(){

    @Test
    fun `Sender 1 Foreldre SED til Kafka`() {

        //given a person
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-NAV.json")

        //when receiving a P2000
        initAndRunContainer(SED_MOTTATT_TOPIC, OPPGAVE_TOPIC)
            .also {
                it.kafkaTemplate.send(SED_MOTTATT_TOPIC, javaClass.getResource("/eux/hendelser/FB_BUC_01_F001.json").readText())
            }

        sedListener.getSendtLatch().await(10, TimeUnit.SECONDS)
    }
}
