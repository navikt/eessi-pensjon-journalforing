package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.sed.SedFnrSøk
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths

@ExtendWith(MockitoExtension::class)
class BegrensInnsynServiceTest {

    @Mock
    private lateinit var euxService: EuxService

    @Mock
    private lateinit var fagmodulService: FagmodulService

    @Mock
    private lateinit var personV3Service: PersonV3Service

    private lateinit var begrensInnsynService: BegrensInnsynService

    private lateinit var sedFnrSøk: SedFnrSøk

    @BeforeEach
    fun setup() {
        sedFnrSøk = SedFnrSøk()

        begrensInnsynService = BegrensInnsynService(
                euxService,
                fagmodulService,
                personV3Service,
                sedFnrSøk
        )
    }

    @Test
    fun sjekkeUtJacksonMapper() {
        val json = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val expected = listOf<String>("44cb68f89a2f4e748934fb4722721018")
        val actual = begrensInnsynService.hentSedDocumentsIds(json)

        Assertions.assertEquals(expected, actual)

    }




}