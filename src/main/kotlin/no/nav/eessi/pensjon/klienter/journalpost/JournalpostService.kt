package no.nav.eessi.pensjon.klienter.journalpost

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.Behandlingstema
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.models.YtelseType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class JournalpostService(private val journalpostKlient: JournalpostKlient) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(JournalpostKlient::class.java) }
    private final val TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY = "eessi_pensjon_bucid"

    @Value("\${no.nav.orgnummer}")
    private lateinit var navOrgnummer: String

    fun opprettJournalpost(
            rinaSakId: String,
            fnr: String?,
            personNavn: String?,
            bucType: String,
            sedType: String,
            sedHendelseType: String,
            eksternReferanseId: String?,
            kanal: String?,
            journalfoerendeEnhet: String,
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
        val tilleggsopplysninger = populerTilleggsopplysninger(rinaSakId)
        val tittel = "${journalpostType.decode()} $sedType"

        val request = OpprettJournalpostRequest(
                avsenderMottaker,
                behandlingstema,
                bruker,
                dokumenter,
                eksternReferanseId,
                journalfoerendeEnhet,
                journalpostType,
                kanal,
                sak,
                tema,
                tilleggsopplysninger,
                tittel)

        return journalpostKlient.opprettJournalpost(request, forsokFerdigstill)
    }

    private fun hentBehandlingsTema(bucType: String, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02.name) {
            return when (ytelseType) {
                YtelseType.UFOREP -> Behandlingstema.UFOREPENSJON.toString()
                YtelseType.GJENLEV -> Behandlingstema.GJENLEVENDEPENSJON.toString()
                else -> Behandlingstema.ALDERSPENSJON.toString()
            }
        } else {
            BucType.valueOf(bucType).BEHANDLINGSTEMA
        }
    }

    @VisibleForTesting
    fun hentTema(bucType: String, sedType: String, enhet: String, ytelseType: YtelseType?): String {
        return if (bucType == BucType.R_BUC_02.name) {
            if (sedType == SedType.R004.name && enhet == "4819") {
                return Tema.PENSJON.toString()
            }
            return when (ytelseType) {
                YtelseType.UFOREP -> Tema.UFORETRYGD.toString()
                else -> Tema.PENSJON.toString()
            }
        } else if (BucType.P_BUC_02.name == bucType && YtelseType.UFOREP == ytelseType) {
            Tema.UFORETRYGD.toString()
        } else {
            BucType.valueOf(bucType).TEMA
        }
    }

    /**
     *  Oppdaterer journaposten, Kanal og ekspedertstatus settes
     */
    fun oppdaterDistribusjonsinfo(journalpostId: String) = journalpostKlient.oppdaterDistribusjonsinfo(journalpostId)

    private fun populerTilleggsopplysninger(rinaSakId: String): List<Tilleggsopplysning> {
        return listOf(Tilleggsopplysning(TILLEGGSOPPLYSNING_RINA_SAK_ID_KEY, rinaSakId))
    }

    private fun populerAvsenderMottaker(
            avsenderNavn: String?,
            sedHendelseType: String,
            avsenderLand: String?): AvsenderMottaker {

        return if(sedHendelseType == "SENDT") {
            AvsenderMottaker(navOrgnummer, IdType.ORGNR, "NAV", "NO")
        } else {
            val justertAvsenderLand = justerAvsenderLand(avsenderLand)
            AvsenderMottaker(null, null, avsenderNavn, justertAvsenderLand)
        }
    }

    /**
     * PESYS støtter kun GB
     */
    private fun justerAvsenderLand(avsenderLand: String?): String? =
            if (avsenderLand == "UK") "GB"
            else avsenderLand

    private fun populerJournalpostType(sedHendelseType: String): JournalpostType =
            if (sedHendelseType == "SENDT") JournalpostType.UTGAAENDE
            else JournalpostType.INNGAAENDE

    private fun populerSak(arkivsaksnummer: String?): Sak? =
            arkivsaksnummer?.let { Sak(it, "PSAK") }
}
