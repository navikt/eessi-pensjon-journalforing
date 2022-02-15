package no.nav.eessi.pensjon.integrasjonstest.mottatt

import com.ninjasquad.springmockk.MockkBean
import no.nav.eessi.pensjon.EessiPensjonJournalforingTestApplication
import no.nav.eessi.pensjon.buc.EuxKlient
import no.nav.eessi.pensjon.integrasjonstest.CustomMockServer
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsBase
import no.nav.eessi.pensjon.integrasjonstest.IntegrasjonsTestConfig
import no.nav.eessi.pensjon.integrasjonstest.OPPGAVE_TOPIC
import no.nav.eessi.pensjon.integrasjonstest.SED_MOTTATT_TOPIC
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.kafka.test.context.EmbeddedKafka
import org.springframework.test.annotation.DirtiesContext
import org.springframework.test.context.ActiveProfiles

@SpringBootTest( classes = [IntegrasjonsTestConfig::class, EessiPensjonJournalforingTestApplication::class], value = ["SPRING_PROFILES_ACTIVE", "integrationtest"])
@ActiveProfiles("integrationtest")
@DirtiesContext
@EmbeddedKafka( topics = [SED_MOTTATT_TOPIC, OPPGAVE_TOPIC] )
internal class FbBuc01F001IntegrasjonsIntegrasjons : IntegrasjonsBase(){

    @MockkBean
    lateinit var euxKlient: EuxKlient

    @Test
    fun `Sender 1 Foreldre SED til Kafka`() {

        //setup server
        CustomMockServer()
            .medJournalforing(false, "429434378")
            .medNorg2Tjeneste()
            .mockBestemSak()
            .medEuxGetRequest("/buc/747729177/sed/44cb68f89a2f4e748934fb4722721018","/sed/P2000-NAV.json")

        //when receiving a P2000
        initAndRunContainer(SED_MOTTATT_TOPIC, OPPGAVE_TOPIC).also {
            it.sendMsgOnDefaultTopic("/eux/hendelser/FB_BUC_01_F001.json")
            it.waitForlatch(mottattListener)
        }
    }
}
