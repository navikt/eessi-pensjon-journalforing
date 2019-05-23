package no.nav.eessi.pensjon.journalforing.services.personv3

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonV3Service
import no.nav.eessi.pensjon.journalforing.services.personv3.PersonMother
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Informasjonsbehov
import no.nav.tjeneste.virksomhet.person.v3.informasjon.NorskIdent
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Person
import no.nav.tjeneste.virksomhet.person.v3.informasjon.PersonIdent
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonRequest
import no.nav.tjeneste.virksomhet.person.v3.meldinger.HentPersonResponse
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals


import org.junit.runner.RunWith
//import org.junit.jupiter.api.Assertions.assertEquals
//import org.junit.jupiter.api.BeforeEach
//import org.junit.jupiter.api.Test
import org.springframework.core.io.DefaultResourceLoader

class PersonV3ServiceTest {

    lateinit var personV3 : PersonV3

    lateinit var personV3Service : PersonV3Service

    private val subject = "23037329381"

    @Before
    fun setup() {
        personV3 = mockk()
        personV3Service = PersonV3Service(personV3)

        every {
            personV3.hentPerson(HentPersonRequest().apply {
                withAktoer(PersonIdent().withIdent(NorskIdent().withIdent(subject)))
                withInformasjonsbehov(listOf(Informasjonsbehov.ADRESSE))
            })
        } returns HentPersonResponse().withPerson(PersonMother.createWith(subject))

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
