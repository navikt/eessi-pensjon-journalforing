package no.nav.eessi.pensjon.buc

import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.*
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.*

internal class EuxServiceTest {

    private val euxKlient: EuxKlient = mockk(relaxed = true)
    private val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)

    private val helper = EuxService(euxKlient)

    @BeforeEach
    fun before() {
        helper.initMetrics()
    }

    @AfterEach
    fun after() {
        confirmVerified(fagmodulKlient)
        clearAllMocks()
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter filtrerer korrekt`() {
        val allSedTypes = SedType.values().toList()
        assertEquals(76, allSedTypes.size)

        val bucDocs = allSedTypes.mapIndexed { index, sedType -> Document(id = "$index", type = sedType, status = SedStatus.RECEIVED.name.lowercase(
            Locale.getDefault()
        )) }
        val buc = Buc(id = "1", processDefinitionName = "P_BUC_01", documents = bucDocs)
        assertEquals(allSedTypes.size, buc.documents?.size)

        val dokumenter = helper.hentAlleGyldigeDokumenter(buc)

        assertEquals(55, dokumenter.size)
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter filtrerer korrekt fra mock Buc`() {
        val json = javaClass.getResource("/eux/buc/buc279020.json").readText()
        val buc = mapJsonToAny<Buc>(json)

        assertEquals(9, helper.hentBucDokumenter(buc).size)
        assertEquals(8, helper.hentAlleGyldigeDokumenter(buc).size)
    }



    @Test
    fun `Sjekk at uthenting av gyldige dokumenter fra BUC med gyldig og kansellerte`() {
        val rinaid = "123123123"
        val bucJson = javaClass.getResource("/buc/R_BUC_02.json").readText()
        val r005json = javaClass.getResource("/sed/R_BUC_02_R005_SE.json").readText()

        val buc = mapJsonToAny<Buc>(bucJson)

        every { euxKlient.hentSedJson(eq(rinaid), any()) } returns r005json
        every { euxKlient.hentSedJson(any(), any()) } returns SED(type = X008).toJson()

        val alledocs = helper.hentAlleGyldigeDokumenter(buc)
        assertEquals(2, alledocs.size)

        val alleSediBuc =  helper.hentAlleSedIBuc(rinaid, alledocs)
        assertEquals(1, alleSediBuc.size)

        val kansellertdocs =  helper.hentAlleKansellerteSedIBuc(rinaid, alledocs)
        assertEquals(1, kansellertdocs.size)
    }


    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = mapJsonToAny<R005>(javaClass.getResource("/sed/R_BUC_02-R005-AP.json").readText())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        R_BUC_02, rinaDokumentVersjon = "1")

        val seds = listOf(sedR005)
        val actual = helper.hentSaktypeType(sedHendelse, seds)

        assertEquals(Saktype.ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = mapJsonToAny<R005>(javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        R_BUC_02, rinaDokumentVersjon = "1")

        val seds = listOf(sedR005)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(Saktype.UFOREP, actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = mapJsonToAny<R005>(javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText())
        val sedP15000 = mapJsonToAny<P15000>(javaClass.getResource("/buc/P15000-NAV.json").readText())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = P_BUC_10, sedType = P15000, rinaDokumentVersjon = "1")
        val seds: List<SED> = listOf(
            sedR005,
            sedP15000
        )

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(Saktype.ALDER, actual)
    }

    @Test
    fun `Sjekker om Norge er caseOwner på buc`() {

        val buc = Buc(
            "123545",
            participants = listOf(Participant("CaseOwner", Organisation(
                countryCode = "NO"
            )))
        )
        assertEquals(true, helper.isNavCaseOwner(buc))
    }

    @Test
    fun `Sjekker om Norge ikke er caseOwner på buc`() {

        val buc = Buc(
            "123545",
            participants = listOf(Participant("CaseOwner", Organisation(
                countryCode = "PL"
            )))
        )
        assertEquals(false, helper.isNavCaseOwner(buc))
    }

    @Test
    fun `Sjekker om Norge ikk er caseOwner på buc del 2`() {

        val buc = Buc(
            "123545",
            participants = listOf(Participant("CounterParty", Organisation(
                countryCode = "NO"
            )))
        )
        assertEquals(false, helper.isNavCaseOwner(buc))
    }


    @Test
    fun `henter en map av gyldige seds i buc`() {
        val rinaSakId = "123123"

        val allDocsJson = javaClass.getResource("/fagmodul/alldocumentsids.json").readText()
        val alldocsid = mapJsonToAny<List<ForenkletSED>>(allDocsJson)
        val bucDocs = alldocsid.mapIndexed { index, docs -> Document(id = "$index", type = docs.type, status = docs.status?.name?.lowercase(
            Locale.getDefault()
        ))  }
        val buc = Buc(id = "2", processDefinitionName = "P_BUC_01", documents = bucDocs)

        val sedJson = javaClass.getResource("/buc/P2000-NAV.json").readText()
        val sedP2000 = mapJsonToAny<SED>(sedJson)

        every { euxKlient.hentSedJson(any(), any()) } returns sedP2000.toJson()

        val result = helper.hentAlleGyldigeDokumenter(buc)
        val actual = helper.hentAlleSedIBuc(rinaSakId, result)

        assertEquals(1, actual.size)

        val actualSed = actual.first()
        assertEquals(P2000, actualSed.second.type)

        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
    }
}
