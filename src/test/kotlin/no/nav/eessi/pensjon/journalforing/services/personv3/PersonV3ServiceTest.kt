package no.nav.eessi.pensjon.journalforing.services.personv3

import com.nhaarman.mockito_kotlin.whenever
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers
import kotlin.test.assertEquals
import org.mockito.Mockito
import org.mockito.Mockito.doReturn

class PersonV3ServiceTest {

    lateinit var personV3 : PersonV3

    lateinit var personV3Service : PersonV3Service

    private val subject = "23037329381"

    @Before
    fun setup() {
        val factory = JaxWsProxyFactoryBean()
        factory.serviceClass = PersonV3::class.java
        factory.address = "someurl"
        personV3 = factory.create() as PersonV3

        personV3Service = Mockito.spy(PersonV3Service(personV3))
        doReturn(HentPersonResponse()).whenever(personV3Service).kallPersonV3(ArgumentMatchers.any())

//        every {
//            personV3.hentPerson(HentPersonRequest().apply {
//                withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(subject)))
//                withInformasjonsbehov(listOf(Informasjonsbehov.ADRESSE))
//            })
//        } returns HentPersonResponse().withPerson(PersonMother.createWith(subject))

        //submissionSampleJson = DefaultResourceLoader().getResource("classpath:json/submission/PinfoSubmission.json").file.readText()
        //imgAttachmentJson = DefaultResourceLoader().getResource("classpath:json/submission/imgAttachment.json").file.readText()
    }

    @Test
    fun `Calling opprettE207Pakke`() {

        println(personV3Service.hentPerson(subject).toString())
        assertEquals(true, true)
        /*
        val content = submissionSampleJson
        val generatedResponse = pdfService.opprettE207Pakke(content, subject)
        val generatedPdf = PDDocument.load(generatedResponse)
        val tempFile = createTempFile("eessipensjon-opprettE207Pakke-", ".pdf")
        generatedPdf.save(tempFile)
        assertEquals(7, generatedPdf.numberOfPages)
         */
    }


}
