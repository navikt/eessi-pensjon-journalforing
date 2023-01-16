package no.nav.eessi.pensjon.klienter.journalpost

import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.models.*
import no.nav.eessi.pensjon.models.Saktype.*
import no.nav.eessi.pensjon.models.Tema.*
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class JournalpostService(private val journalpostKlient: JournalpostKlient) {

    @Value("\${no.nav.orgnummer}")
    private lateinit var navOrgnummer: String

    private val logger = LoggerFactory.getLogger(JournalpostService::class.java)

    companion object {
        private const val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"
    }

    /**
     * Bygger en {@link OpprettJournalpostRequest} som videresendes til {@link JournalpostKlient}.
     *
     * @return {@link OpprettJournalPostResponse?} som inneholder
     */
    fun opprettJournalpost(
        rinaSakId: String,
        fnr: Fodselsnummer?,
        bucType: BucType,
        sedType: SedType,
        sedHendelseType: HendelseType,
        journalfoerendeEnhet: Enhet,
        arkivsaksnummer: String?,
        dokumenter: String,
        avsenderLand: String?,
        avsenderNavn: String?,
        saktype: Saktype?
    ): OpprettJournalPostResponse? {

        val request = OpprettJournalpostRequest(
            avsenderMottaker = populerAvsenderMottaker(avsenderNavn, sedHendelseType, avsenderLand),
            behandlingstema = bestemBehandlingsTema(bucType, saktype),
            bruker = fnr?.let { Bruker(id = it.value) },
            journalpostType = bestemJournalpostType(sedHendelseType),
            sak = arkivsaksnummer?.let { Sak(it, "PSAK")},
            tema = hentTema(bucType, saktype),
            tilleggsopplysninger = listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId)),
            tittel = lagTittel(bestemJournalpostType(sedHendelseType), sedType),
            dokumenter = dokumenter,
            journalfoerendeEnhet = journalfoerendeEnhet
        )

        val forsokFerdigstill: Boolean = journalfoerendeEnhet == Enhet.AUTOMATISK_JOURNALFORING

        return journalpostKlient.opprettJournalpost(request, forsokFerdigstill)
    }

    /**
     *  Ferdigstiller journalposten.
     *
     *  @param journalpostId: ID til journalposten som skal ferdigstilles.
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) = journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)

    private fun bestemBehandlingsTema(bucType: BucType, saktype: Saktype?): Behandlingstema {
        return if (bucType == BucType.R_BUC_02) {
            when (saktype) {
                UFOREP -> Behandlingstema.UFOREPENSJON
                GJENLEV -> Behandlingstema.GJENLEVENDEPENSJON
                else -> Behandlingstema.ALDERSPENSJON
            }
            Behandlingstema.TILBAKEBETALING
        } else {
            return when (bucType) {
                BucType.P_BUC_02 -> Behandlingstema.GJENLEVENDEPENSJON
                BucType.P_BUC_03 -> Behandlingstema.UFOREPENSJON
                else -> Behandlingstema.ALDERSPENSJON
            }
        }

    }

    /**
     * Tema er PENSJON såfremt det ikke er en
     * - uføre buc (P_BUC_03)
     * - saktype er UFØRETRYGD
     */

    fun hentTema(bucType: BucType, saktype: Saktype?) : Tema =
        if (saktype == UFOREP || bucType == BucType.P_BUC_03 && saktype == null) UFORETRYGD else PENSJON

    private fun populerAvsenderMottaker(
            avsenderNavn: String?,
            sedHendelseType: HendelseType,
            avsenderLand: String?): AvsenderMottaker {

        return if (sedHendelseType == HendelseType.SENDT) {
            AvsenderMottaker(navOrgnummer, IdType.ORGNR, "NAV", "NO")
        } else {
            val justertAvsenderLand = justerAvsenderLand(avsenderLand)
            AvsenderMottaker(navn = avsenderNavn, land = justertAvsenderLand)
        }
    }

    /**
     * PESYS støtter kun GB
     */
    private fun justerAvsenderLand(avsenderLand: String?): String? =
            if (avsenderLand == "UK") "GB"
            else avsenderLand

    private fun bestemJournalpostType(sedHendelseType: HendelseType): JournalpostType =
            if (sedHendelseType == HendelseType.SENDT) JournalpostType.UTGAAENDE
            else JournalpostType.INNGAAENDE

    private fun lagTittel(journalpostType: JournalpostType,
                          sedType: SedType) = "${journalpostType.decode()} ${sedType.typeMedBeskrivelse()}"
}