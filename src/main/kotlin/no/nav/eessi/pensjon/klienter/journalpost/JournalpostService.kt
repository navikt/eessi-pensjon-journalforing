package no.nav.eessi.pensjon.klienter.journalpost

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.HendelseType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.YtelseType
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
                journalfoerendeEnhet,
                journalpostType,
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

    private fun hentBehandlingsTema(bucType: BucType, ytelseType: YtelseType?): Behandlingstema {
        return if (bucType == BucType.R_BUC_02) {
            return when (ytelseType) {
                YtelseType.UFOREP -> Behandlingstema.UFOREPENSJON
                YtelseType.GJENLEV -> Behandlingstema.GJENLEVENDEPENSJON
                else -> Behandlingstema.ALDERSPENSJON
            }
        } else bucType.behandlingstema
    }

    @VisibleForTesting
    fun hentTema(bucType: BucType, sedType: SedType, enhet: Enhet, ytelseType: YtelseType?): Tema {
        logger.debug("hentTema  bucType: $bucType sedType: $sedType  enhet: $enhet  ytelse: $ytelseType")
        if (bucType == BucType.P_BUC_05) {
            return if (ytelseType == YtelseType.UFOREP) Tema.UFORETRYGD
            else Tema.PENSJON
        }
        if (bucType == BucType.R_BUC_02) {
            if (sedType == SedType.R004 && enhet == Enhet.OKONOMI_PENSJON) {
                return Tema.PENSJON
            }
            return when (ytelseType) {
                YtelseType.UFOREP -> Tema.UFORETRYGD
                else -> Tema.PENSJON
            }
        } else if (bucType == BucType.P_BUC_02 && ytelseType == YtelseType.UFOREP) {
            return Tema.UFORETRYGD
        } else {
            return bucType.tema
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
