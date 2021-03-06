package no.nav.eessi.pensjon.oppgaverouting

import io.mockk.every
import io.mockk.mockk
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.HendelseType.MOTTATT
import no.nav.eessi.pensjon.models.HendelseType.SENDT
import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.IdentifisertPerson
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

internal class Rbuc02Test {

    private val handler = BucTilEnhetHandlerCreator.getHandler(BucType.R_BUC_02) as Rbuc02

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Verifiser håndtering av diskresjonskode`(hendelseType: HendelseType) {
        val ikkeFortrolig = hendelseType.mockRequest()
        assertNotEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(ikkeFortrolig))

        val strengtFortrolig = hendelseType.mockRequest(harAdressebeskyttelse = true)
        assertEquals(Enhet.DISKRESJONSKODE, handler.hentEnhet(strengtFortrolig))
    }

    @ParameterizedTest
    @EnumSource(Saktype::class)
    fun `Sendt sak med kun én person kan automatisk behandles`(saktype: Saktype) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "111"
            every { flereEnnEnPerson() } returns false
        }

        val request = SENDT.mockRequest(type = saktype, person = person)

        assertEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @Test
    fun `Sendt sak med kun én person, men mangler aktorId`() {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns false
        }

        val request = SENDT.mockRequest(type = mockk(), person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(Saktype::class)
    fun `Mottatt sak skal aldri til automatisk journalføring`(saktype: Saktype) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "111"
            every { flereEnnEnPerson() } returns false
        }

        val request = MOTTATT.mockRequest(type = saktype, person = person)

        assertNotEquals(Enhet.AUTOMATISK_JOURNALFORING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Sak med flere personer skal til ID og Fordeling`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns true
        }

        val request = hendelseType.mockRequest(type = Saktype.ALDER, person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `Sak med ukjent person skal til ID og Fordeling`(hendelseType: HendelseType) {
        val request = hendelseType.mockRequest(type = Saktype.ALDER, person = null)

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `SedType R004 skal alltid til Økonomi og Pensjon`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "gyldig"
            every { flereEnnEnPerson() } returns false
        }

        val request = hendelseType.mockRequest(sedType = SedType.R004, person = person)

        assertEquals(Enhet.OKONOMI_PENSJON, handler.hentEnhet(request))
    }

    @ParameterizedTest
    @EnumSource(HendelseType::class)
    fun `SedType R004 skal ikke til Økonomi og Pensjon dersom person er ugyldig`(hendelseType: HendelseType) {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns " "
            every { flereEnnEnPerson() } returns true
        }

        val request = hendelseType.mockRequest(sedType = SedType.R004, person = person)

        assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
    }

    @Test
    fun `Mottatt sak til manuell behandling`() {
        val person = mockk<IdentifisertPerson> {
            every { aktoerId } returns "gyldig"
            every { flereEnnEnPerson() } returns false
        }

        val forventPensjonUtland = MOTTATT.mockRequest(type = Saktype.ALDER, person = person)
        assertEquals(Enhet.PENSJON_UTLAND, handler.hentEnhet(forventPensjonUtland))

        val forventUforeUtland = MOTTATT.mockRequest(type = Saktype.UFOREP, person = person)
        assertEquals(Enhet.UFORE_UTLAND, handler.hentEnhet(forventUforeUtland))

        listOf(Saktype.OMSORG, Saktype.GJENLEV, Saktype.BARNEP, Saktype.GENRL)
                .forEach { saktype ->
                    val request = MOTTATT.mockRequest(type = saktype, person = person)
                    assertEquals(Enhet.ID_OG_FORDELING, handler.hentEnhet(request))
                }
    }

    private fun HendelseType.mockRequest(
        type: Saktype? = null,
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

            every { this@mockk.sedType } returns sedType
            every { this@mockk.harAdressebeskyttelse } returns harAdressebeskyttelse
        }
    }

}