package no.nav.eessi.pensjon.buc

import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.Document
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.EessisakItem
import no.nav.eessi.pensjon.eux.model.sed.Nav
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.toJson
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SakInformasjon
import no.nav.eessi.pensjon.models.SakStatus
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.sed.SedHendelseModel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Paths

internal class EuxDokumentHelperTest {

    private val euxKlient: EuxKlient = mockk(relaxed = true)
    private val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)

    private val helper = EuxDokumentHelper(fagmodulKlient, euxKlient)

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

        val bucDocs = allSedTypes.mapIndexed { index, sedType -> Document(id = "$index", type = sedType, status = SedStatus.RECEIVED.name.toLowerCase() ) }
        val buc = Buc(id = "1", processDefinitionName = "P_BUC_01", documents = bucDocs)
        assertEquals(allSedTypes.size, buc.documents?.size)

        val dokumenter = helper.hentAlleGyldigeDokumenter(buc)

        assertEquals(52, dokumenter.size)
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter filtrerer korrekt fra mock Buc`() {
        val json = javaClass.getResource("/eux/buc/buc279020.json").readText()
        val buc = mapJsonToAny(json, typeRefs<Buc>())

        assertEquals(25, helper.hentBucDokumenter(buc).size)
        assertEquals(13, helper.hentAlleGyldigeDokumenter(buc).size)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/sed/R_BUC_02-R005-AP.json").readText(), typeRefs<R005>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = listOf(sedR005)
        val actual = helper.hentSaktypeType(sedHendelse, seds)

        assertEquals(Saktype.ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText(), typeRefs<R005>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "R", bucType =
        BucType.R_BUC_02)

        val seds = listOf(sedR005)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(Saktype.UFOREP, actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedR005 = mapJsonToAny(javaClass.getResource("/sed/R_BUC_02-R005-UT.json").readText(), typeRefs<R005>())
        val sedP15000 = mapJsonToAny(javaClass.getResource("/buc/P15000-NAV.json").readText(), typeRefs<P15000>())

        val sedHendelse = SedHendelseModel(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = "P", bucType = BucType.P_BUC_10, sedType = SedType.P15000)
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
        val alldocsid = mapJsonToAny(allDocsJson, typeRefs<List<ForenkletSED>>())
        val bucDocs = alldocsid.mapIndexed { index, docs -> Document(id = "$index", type = docs.type, status = docs.status?.name?.toLowerCase() )  }
        val buc = Buc(id = "2", processDefinitionName = "P_BUC_01", documents = bucDocs)

        val sedJson = javaClass.getResource("/buc/P2000-NAV.json").readText()
        val sedP2000 = mapJsonToAny(sedJson, typeRefs<SED>())

        every { euxKlient.hentSedJson(any(), any()) } returns sedP2000.toJson()

        val result = helper.hentAlleGyldigeDokumenter(buc)
        val actual = helper.hentAlleSedIBuc(rinaSakId, result)

        assertEquals(1, actual.size)

        val actualSed = actual.first()
        assertEquals(SedType.P2000, actualSed.second.type)

        verify(exactly = 1) { euxKlient.hentSedJson(any(), any()) }
    }

    @Test
    fun `Gitt det finnes aktoerid og det finnes en eller flere pensjonsak Så skal det sakid fra sed valideres og sakid returneres`() {

        val expected = SakInformasjon(sakId = "22874955", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
        val mockPensjonSaklist = listOf(expected, SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist

        val sedP5000 = String(Files.readAllBytes(Paths.get("src/test/resources/sed/P5000-medNorskGjenlevende-NAV.json")))
        val mockAllSediBuc = listOf(
                mapJsonToAny(sedP5000, typeRefs<SED>())
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)

        assertNotNull(result)
        assertEquals(expected.sakId, result?.sakId)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes eessisak der land ikke er Norge så returneres null`() {
        val sedP2000 = javaClass.getResource("/sed/P2000-ugyldigFNR-NAV.json").readText()

        val mockAllSediBuc = listOf(
                mapJsonToAny(sedP2000, typeRefs<SED>())
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)
        assertNull(result)

        verify { fagmodulKlient wasNot Called }
    }

    @Test
    fun `Gitt at det finnes eessisak der land er Norge og saksnummer er på feil format så skal null returneres`() {
        val mockAlleSedIBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "UGYLDIG SAK ID")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc)
        assertNull(result)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt at det finnes en aktoerid med eessisak der land er Norge når kall til tjenesten feiler så kastes det en exception`() {
        every { fagmodulKlient.hentPensjonSaklist(any()) } throws RuntimeException()

        val mockAlleSedIBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "12345")
        )

        assertNull(helper.hentPensjonSakFraSED("123123", mockAlleSedIBuc))

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag mot pensjoninformasjon tjenesten, For så å hente ut rett SakInformasjon`() {
        val expected = SakInformasjon(sakId = "22874955", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = Saktype.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874456", sakType = Saktype.BARNEP, sakStatus = SakStatus.AVSLUTTET))

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "22874955"),
                mockSED(SedType.P4000, eessiSakId = "22874955"),
                mockSED(SedType.P5000, eessiSakId = "22874955"),
                mockSED(SedType.P6000, eessiSakId = "22874955")
        )

        val result = helper.hentPensjonSakFraSED("123123", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc som har like saknr hents kun et for oppslag, hvis sak er GENERELL kan sjekkes om har tilknytteteSaker`() {
        val expected = SakInformasjon(sakId = "22874456", sakType = Saktype.GENRL, sakStatus = SakStatus.LOPENDE)

        val mockPensjonSaklist = listOf(
                expected,
                SakInformasjon(sakId = "22874901", sakType = Saktype.UFOREP, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874123", sakType = Saktype.GJENLEV, sakStatus = SakStatus.AVSLUTTET),
                SakInformasjon(sakId = "22874457", sakType = Saktype.ALDER, sakStatus = SakStatus.LOPENDE)
        )

        every { fagmodulKlient.hentPensjonSaklist(any()) } returns mockPensjonSaklist
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "22874456"),
                mockSED(SedType.P4000, eessiSakId = "22874456"),
                mockSED(SedType.P5000, eessiSakId = "22874456"),
                mockSED(SedType.P6000, eessiSakId = "22874456")
        )

        val result = helper.hentPensjonSakFraSED("aktoerId", mockAllSediBuc)!!
        assertNotNull(result)
        assertEquals(expected.sakType, result.sakType)
        assertTrue(result.harGenerellSakTypeMedTilknyttetSaker())
        assertEquals(3, result.tilknyttedeSaker.size)

        verify(exactly = 1) { fagmodulKlient.hentPensjonSaklist(any()) }
    }

    @Test
    fun `Gitt flere sed i buc har forskjellige saknr hents ingen for oppslag, ingen SakInformasjon returneres`() {
        val mockAllSediBuc = listOf(
                mockSED(SedType.P2000, eessiSakId = "111"),
                mockSED(SedType.P4000, eessiSakId = "222"),
                mockSED(SedType.P6000, eessiSakId = "333")
        )

        assertNull(
                helper.hentPensjonSakFraSED("111", mockAllSediBuc),
                "Skal ikke få noe i retur dersom det finnes flere unike EessiSakIDer."
        )

        verify { fagmodulKlient wasNot Called }
    }

    private fun mockSED(sedType: SedType, eessiSakId: String?): SED {
        return SED(
                type = sedType,
                nav = Nav(
                        eessisak = listOf(EessisakItem(saksnummer = eessiSakId, land = "NO"))
                )
        )
    }

}
