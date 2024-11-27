package no.nav.eessi.pensjon.eux

import io.mockk.*
import no.nav.eessi.pensjon.eux.klient.EuxKlientLib
import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.P_BUC_10
import no.nav.eessi.pensjon.eux.model.BucType.R_BUC_02
import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.eux.model.buc.DocumentsItem
import no.nav.eessi.pensjon.eux.model.buc.Organisation
import no.nav.eessi.pensjon.eux.model.buc.Participant
import no.nav.eessi.pensjon.eux.model.buc.SakType.ALDER
import no.nav.eessi.pensjon.eux.model.buc.SakType.UFOREP
import no.nav.eessi.pensjon.eux.model.document.ForenkletSED
import no.nav.eessi.pensjon.eux.model.document.SedStatus
import no.nav.eessi.pensjon.eux.model.sed.P15000
import no.nav.eessi.pensjon.eux.model.sed.R005
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.listeners.fagmodul.FagmodulKlient
import no.nav.eessi.pensjon.utils.mapJsonToAny
import no.nav.eessi.pensjon.utils.toJson
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

private const val RINASAK_ID = "123456"

internal class EuxServiceTest {

    private val euxKlientLib = mockk<EuxKlientLib>(relaxed = true)
    private val euxCacheableKlient = EuxCacheableKlient(euxKlientLib)
    private val fagmodulKlient: FagmodulKlient = mockk(relaxed = true)
    private val helper = EuxService(euxCacheableKlient)

    @AfterEach
    fun after() {
        confirmVerified(fagmodulKlient)
        clearAllMocks()
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter filtrerer korrekt`() {
        val allSedTypes = SedType.entries
        assertEquals(81, allSedTypes.size)

        val bucDocs = allSedTypes.mapIndexed { index, sedType -> DocumentsItem(id = "$index", type = sedType, status = SedStatus.RECEIVED.name.lowercase(
            Locale.getDefault()
        )) }

        val buc = buc(documents = bucDocs)
        assertEquals(allSedTypes.size, buc.documents?.size)

        val dokumenter = helper.hentAlleGyldigeDokumenter(buc)
        assertEquals(61, dokumenter.size)
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter filtrerer korrekt fra mock Buc`() {
        val json = javaClass.getResource("/eux/buc/buc279020.json")!!.readText()
        val buc = mapJsonToAny<Buc>(json)

        assertEquals(8, helper.hentAlleGyldigeDokumenter(buc).size)
    }

    @Test
    fun `Sjekk at uthenting av gyldige dokumenter fra BUC med gyldig og kansellerte`() {
        val bucJson = javaClass.getResource("/buc/R_BUC_02.json")!!.readText()
        val r005json = javaClass.getResource("/sed/R_BUC_02_R005_SE.json")!!.readText()

        val buc = mapJsonToAny<Buc>(bucJson)

        every { euxKlientLib.hentSedJson(eq(RINASAK_ID), any()) } returns r005json
        every { euxKlientLib.hentSedJson(any(), any()) } returns SED(type = SEDTYPE_X008).toJson()

        val alledocs = helper.hentAlleGyldigeDokumenter(buc)
        assertEquals(2, alledocs.size)

        val alleSediBuc =  helper.hentSedMedGyldigStatus(RINASAK_ID, buc)
        assertEquals(1, alleSediBuc.size)

        val kansellertdocs =  helper.hentAlleKansellerteSedIBuc(RINASAK_ID, buc)
        assertEquals(1, kansellertdocs.size)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed R005`() {
        val sedR005 = r005("/sed/R_BUC_02-R005-AP.json")
        val sedHendelse = sedHendelse( "R")
        val seds = listOf(sedR005)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(ALDER ,actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for UT fra sed R005`() {
        val sedR005 = r005()
        val sedHendelse = sedHendelse("R", sedType = SEDTYPE_P2100)
        val seds = listOf(sedR005)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(UFOREP, actual)
    }

    @Test
    fun `Finn korrekt ytelsestype for AP fra sed P15000`() {
        val sedP15000 = mapJsonToAny<P15000>(javaClass.getResource("/buc/P15000-NAV.json")!!.readText())
        val sedHendelse = sedHendelse("P", P_BUC_10, SEDTYPE_P15000)
        val seds: List<SED> = listOf(r005(), sedP15000)

        val actual = helper.hentSaktypeType(sedHendelse, seds)
        assertEquals(ALDER, actual)
    }

    @Test
    fun `Sjekker om Norge er caseOwner på buc`() {
        val buc = buc("CaseOwner", "NO")
        assertEquals(true, helper.isNavCaseOwner(buc))
    }

    @Test
    fun `Sjekker om Norge ikke er caseOwner på buc`() {
        val buc = buc("CaseOwner", "PL")
        assertEquals(false, helper.isNavCaseOwner(buc))
    }

    @Test
    fun `Sjekker om Norge ikk er caseOwner på buc del 2`() {
        val buc = buc ("CounterParty", "NO")
        assertEquals(false, helper.isNavCaseOwner(buc))
    }

    @Test
    fun `henter en map av gyldige seds i buc`() {
        val allDocsJson = javaClass.getResource("/fagmodul/alldocumentsids.json")!!.readText()
        val alldocsid = mapJsonToAny<List<ForenkletSED>>(allDocsJson)
        val bucDocs = alldocsid.mapIndexed { index, docs -> DocumentsItem(id = "$index", type = docs.type, status = docs.status?.name?.lowercase(
            Locale.getDefault()
        )) }

        val buc = buc(documents = bucDocs)
        val sedJson = javaClass.getResource("/buc/P2000-NAV.json")!!.readText()
        val sedP2000 = mapJsonToAny<SED>(sedJson)

        every { euxKlientLib.hentSedJson(any(), any()) } returns sedP2000.toJson()

        val actual = helper.hentSedMedGyldigStatus(RINASAK_ID, buc)

        assertEquals(1, actual.size)

        val actualSed = actual.first()
        assertEquals(SEDTYPE_P2000, actualSed.second.type)

        verify(exactly = 1) { euxKlientLib.hentSedJson(any(), any()) }
    }

    private fun sedHendelse(sektorkode: String, bucType: BucType? = R_BUC_02, sedType: SedType? = SEDTYPE_R005) : SedHendelse {
        return SedHendelse(rinaSakId = "123456", rinaDokumentId = "1234", sektorKode = sektorkode,
            bucType = bucType, rinaDokumentVersjon = "1", sedType = sedType
        )
    }

    private fun r005(sedfil: String? = "/sed/R_BUC_02-R005-UT.json"): R005 {
        val sedR005 = mapJsonToAny<R005>(javaClass.getResource(sedfil)!!.readText())
        return sedR005
    }

    private fun buc(
        role: String? = "CaseOwner",
        landkode: String? = "NO",
        bucNavn: String? = BucType.P_BUC_01.name,
        documents: List<DocumentsItem>? = emptyList()
    ) = Buc(RINASAK_ID, processDefinitionName = bucNavn, documents = documents, participants = listOf(Participant(role, Organisation(countryCode = landkode))))
}
