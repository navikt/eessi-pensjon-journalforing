package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.oppgaverouting.HendelseType.*
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentifisertPerson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

internal class Rbuc02Test {

    private val handler = EnhetFactory.hentHandlerFor(R_BUC_02) as Rbuc02

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Verifiser håndtering av diskresjonskode`(hendelseType: HendelseType) {
        val ikkeFortrolig = hendelseType.mockRequest()
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.finnEnhet(ikkeFortrolig))

        val strengtFortrolig = hendelseType.mockRequest(harAdressebeskyttelse = true)
        assertEquals(Enhet.DISKRESJONSKODE, handler.finnEnhet(strengtFortrolig))
    }

    @ParameterizedTest
    @EnumSource(SakType::class)
    fun `Sendt sak med kun én person kan automatisk behandles`(saktype: SakType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "111"
            every { flereEnnEnPerson() } returns false
        }

        val request = SENDT.mockRequest(type = saktype, person = person)

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.finnEnhet(request))
    }

    @Test
    fun `Sendt sak med kun én person, men mangler aktorId`() {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns false
        }

        val request = SENDT.mockRequest(type = mockk(), person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.finnEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(SakType::class)
    fun `Mottatt sak skal aldri til automatisk journalføring`(saktype: SakType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "111"
            every { flereEnnEnPerson() } returns false
        }

        val request = MOTTATT.mockRequest(type = saktype, person = person)

        assertNotEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.finnEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Sak med flere personer skal til ID og Fordeling`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns true
        }

        val request = hendelseType.mockRequest(type = ALDER, person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.finnEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Sak med ukjent person skal til ID og Fordeling`(hendelseType: HendelseType) {
        val request = hendelseType.mockRequest(type = ALDER, person = null)

        assertEquals(Enhet.ID_OG_FORDELING, handler.finnEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `SedType R004 skal alltid til Økonomi og Pensjon`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "gyldig"
            every { flereEnnEnPerson() } returns false
        }

        val request = hendelseType.mockRequest(sedType = SedType.R004, person = person)

        assertEquals(Enhet.OKONOMI_PENSJON, handler.finnEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `SedType R004 skal ikke til Økonomi og Pensjon dersom person er ugyldig`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns true
        }

        val request = hendelseType.mockRequest(sedType = SedType.R004, person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.finnEnhet(request))
    }

    @Test
    fun `Mottatt sak til manuell behandling`() {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "gyldig"
            every { flereEnnEnPerson() } returns false
        }

        val forventPensjonUtland = MOTTATT.mockRequest(type = ALDER, person = person)
        assertEquals(Enhet.PENSJON_UTLAND, handler.finnEnhet(forventPensjonUtland))

        val forventUforeUtland = MOTTATT.mockRequest(type = UFOREP, person = person)
        assertEquals(Enhet.UFORE_UTLAND, handler.finnEnhet(forventUforeUtland))

        listOf(OMSORG, GJENLEV, BARNEP, GENRL)
                .forEach { saktype ->
                    val request = MOTTATT.mockRequest(type = saktype, person = person)
                    assertEquals(Enhet.ID_OG_FORDELING, handler.finnEnhet(request))
                }
    }

    private fun HendelseType.mockRequest(
        type: SakType? = null,
        harAdressebeskyttelse: Boolean = false,
        sedType: SedType = SedType.R005,
        person: IdentifisertPerson? = null
    ): OppgaveRoutingRequest {
        val hendelse = this

        return mockk {
            every { aktorId } returns "12345"
            every { hendelseType } returns hendelse
            every { saktype } returns type
            every { sakInformasjon?.sakId } returns "sakId"
            every { identifisertPerson } returns person
            every { bucType } returns R_BUC_02


            every { this@mockk.sedType } returns sedType
            every { this@mockk.harAdressebeskyttelse } returns harAdressebeskyttelse
        }
    }

}