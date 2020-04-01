package no.nav.eessi.pensjon.buc

import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
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
    fun `Finn korrekt ytelsestype fra sed R005`(){
        val sedR005 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/R_BUC_02-R005-AP.json")))
        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = mapOf<String,String?>(SedType.R005.name to sedR005)

        val actual = helper.hentYtelseType(sedHendelse, seds)

        Assertions.assertEquals("alderspensjon",actual)



    }









}