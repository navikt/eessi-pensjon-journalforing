package no.nav.eessi.pensjon.klienter.journalpost

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.YtelseType
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class JournalpostService(private val journalpostKlient: JournalpostKlient) {

    @Value("\${no.nav.orgnummer}")
    private lateinit var navOrgnummer: String

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
            fnr: String?,
            personNavn: String?,
            bucType: BucType,
            sedType: SedType,
            sedHendelseType: HendelseType,
            journalfoerendeEnhet: Enhet,
            arkivsaksnummer: String?,
            dokumenter: String,
            forsokFerdigstill: Boolean = false,
            avsenderLand: String?,
            avsenderNavn: String?,
            ytelseType: YtelseType?): OpprettJournalPostResponse? {

        val avsenderMottaker = populerAvsenderMottaker(avsenderNavn, sedHendelseType, avsenderLand)
        val behandlingstema = hentBehandlingsTema(bucType, ytelseType)
        val bruker = fnr?.let { Bruker(id = it) }
        val journalpostType = populerJournalpostType(sedHendelseType)
        val sak = populerSak(arkivsaksnummer)
        val tema = hentTema(bucType, sedType, journalfoerendeEnhet, ytelseType)
        val tilleggsopplysninger = listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId))
        val tittel = "${journalpostType.decode()} $sedType"

        val request = OpprettJournalpostRequest(
                avsenderMottaker,
                behandlingstema,
                bruker,
                dokumenter,
                null,
                journalfoerendeEnhet.enhetsNr,
                journalpostType,
                "EESSI",
                sak,
                tema,
                tilleggsopplysninger,
                tittel)

        return journalpostKlient.opprettJournalpost(request, forsokFerdigstill)
    }

    /**
     *  Ferdigstiller journalposten.
     *
     *  @param journalpostId: ID til journalposten som skal ferdigstilles.
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) = journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)

    private fun hentBehandlingsTema(bucType: BucType, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02) {
            return when (ytelseType) {
                YtelseType.UFOREP -> Behandlingstema.UFOREPENSJON.toString()
                YtelseType.GJENLEV -> Behandlingstema.GJENLEVENDEPENSJON.toString()
                else -> Behandlingstema.ALDERSPENSJON.toString()
            }
        } else bucType.BEHANDLINGSTEMA
    }

    @VisibleForTesting
    fun hentTema(bucType: BucType, sedType: SedType, enhet: Enhet, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02) {
            if (sedType == SedType.R004 && enhet == Enhet.OKONOMI_PENSJON) {
                return Tema.PENSJON.toString()
            }
            return when (ytelseType) {
                YtelseType.UFOREP -> Tema.UFORETRYGD.toString()
                else -> Tema.PENSJON.toString()
            }
        } else if (bucType == BucType.P_BUC_02 && ytelseType == YtelseType.UFOREP) {
            Tema.UFORETRYGD.toString()
        } else {
            bucType.TEMA
        }
    }

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

    private fun populerJournalpostType(sedHendelseType: HendelseType): JournalpostType =
            if (sedHendelseType == HendelseType.SENDT) JournalpostType.UTGAAENDE
            else JournalpostType.INNGAAENDE

    private fun populerSak(arkivsaksnummer: String?): Sak? =
            arkivsaksnummer?.let { Sak(it, "PSAK") }
}
