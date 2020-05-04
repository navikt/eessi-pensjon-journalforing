package no.nav.eessi.pensjon.buc

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.whenever
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.ArgumentMatchers
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import java.nio.file.Files
import java.nio.file.Paths


@ExtendWith(MockitoExtension::class)
class SedDokumentHelperTest {

    @Mock
    private lateinit var euxKlient: EuxKlient

    @Mock
    private lateinit var fagmodulKlient: FagmodulKlient

    lateinit var helper: SedDokumentHelper


    @BeforeEach
    fun before() {
        helper = SedDokumentHelper(fagmodulKlient, euxKlient)
    }


    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-AP.json")))
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = mapOf<String,String?>(SedType.R005.name to sedR005)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        Assertions.assertEquals("AP",actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-UT.json")))
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = mapOf<String,String?>(SedType.R005.name to sedR005)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        Assertions.assertEquals("UT",actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-UT.json")))
        val sed = String(Files.readAllBytes(Paths.get("src/test/resources/buc/P15000-NAV.json")))

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = BucType.P_BUC_10, sedType = SedType.P15000)
        val seds = mapOf<String,String?>(SedType.R005.name to sedR005, SedType.P15000.name to sed)
        val actual = helper.hentYtelseType(sedHendelse, seds)

        Assertions.assertEquals("AP" ,actual)
    }

    @Test
    fun `henter en map av gyldige seds i buc`() {
        val alldocsid = String(Files.readAllBytes(Paths.get("src/test/resources/fagmodul/alldocumentsids.json")))
        val sedP2000 =  String(Files.readAllBytes(Paths.get("src/test/resources/buc/P2000-NAV.json")))

        doReturn(alldocsid).whenever(fagmodulKlient).hentAlleDokumenter(ArgumentMatchers.anyString())
        doReturn(sedP2000).whenever(euxKlient).hentSed(ArgumentMatchers.anyString(), ArgumentMatchers.anyString())

        val actual = helper.hentAlleSedIBuc("123123")
        Assertions.assertEquals(1, actual.size)
        Assertions.assertEquals(SedType.P2000.name, actual.keys.toList().get(0))
        Assertions.assertEquals(sedP2000, actual[SedType.P2000.name])
        Assertions.assertEquals(sedP2000, helper.hentAlleSeds(actual)[0])

    }

}
