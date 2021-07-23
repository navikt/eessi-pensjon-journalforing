package no.nav.eessi.pensjon.integrasjonstest.sendt

import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_SENDT_TOPIC
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka(topics = [SED_SENDT_TOPIC, OPPGAVE_TOPIC])
internal class FBBuc01IntegrasjonsTest : IntegrasjonsBase() {

    @Test
    fun `Når en sedSendt hendelse med en foreldre blir konsumert så skal den ikke opprette oppgave`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018", "/sed/P2000-NAV.json")

        //send msg
        initAndRunContainer(SED_SENDT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/FB_BUC_01_F001.json")
            it.waitForlatch(sedListener)
        }
    }
}
