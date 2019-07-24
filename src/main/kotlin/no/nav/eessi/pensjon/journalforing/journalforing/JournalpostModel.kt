package no.nav.eessi.pensjon.journalforing.journalforing

import no.nav.eessi.pensjon.journalforing.metrics.counter
import no.nav.eessi.pensjon.journalforing.models.HendelseType
import no.nav.eessi.pensjon.journalforing.pdf.ImageConverter
import no.nav.eessi.pensjon.journalforing.services.eux.MimeType
import no.nav.eessi.pensjon.journalforing.services.eux.SedDokumenterResponse
import no.nav.eessi.pensjon.journalforing.services.journalpost.*
import org.slf4j.LoggerFactory

data class JournalpostModel (
        val journalpostRequest: JournalpostRequest,
        val uSupporterteVedlegg: List<String>
) {
    companion object {

        private val logger = LoggerFactory.getLogger(JournalpostModel::class.java)

        private val genererJournalpostModelNavn = "eessipensjon_journalforing.genererjournalpostmodel"
        private val genererJournalpostModelVellykkede = counter(genererJournalpostModelNavn, "vellykkede")
        private val genererJournalpostModelFeilede = counter(genererJournalpostModelNavn, "feilede")

        fun from(sedHendelseModel: SedHendelseModel,
                 sedHendelseType: HendelseType,
                 sedDokumenter: SedDokumenterResponse,
                 personNavn: String?): JournalpostModel {
            try {
                val journalpostType = populerJournalpostType(sedHendelseType)
                val avsenderMottaker = populerAvsenderMottaker(sedHendelseModel, sedHendelseType, personNavn)
                val behandlingstema = sedHendelseModel.bucType?.BEHANDLINGSTEMA

                val bruker = when {
                    sedHendelseModel.navBruker != null -> Bruker(id = sedHendelseModel.navBruker)
                    else -> null
                }

                val konvert = sedDokumenter.vedlegg?.map { it ->
                    if(it.mimeType == null) {
                        Dokument(null,
                                "UNSUPPORTED",
                                listOf(),
                                it.filnavn
                        )
                    }else {
                        try {
                            Dokument(sedHendelseModel.sedId,
                                    "SED",
                                    listOf(Dokumentvarianter(
                                            MimeType.PDF.decode(),
                                            konverterBildeTilPDF(it.innhold, it.mimeType.toString()),
                                            Variantformat.ARKIV)),
                                    konverterFilendingTilPdf(it.filnavn))
                        } catch (ex: Exception) {
                            Dokument(null,
                                    "UNSUPPORTED",
                                    listOf(),
                                    it.filnavn
                            )
                        }
                    }
                }

                val partition = konvert?.partition { it.dokumentKategori != "UNSUPPORTED" }

                val dokumenter = listOf(Dokument(
                        sedHendelseModel.sedId,
                        "SED",
                        listOf(Dokumentvarianter(
                                fysiskDokument = sedDokumenter.sed.innhold,
                                filtype = sedDokumenter.sed.mimeType!!.decode(),
                                variantformat = Variantformat.ARKIV)),
                        sedDokumenter.sed.filnavn
                )).plus(when(partition?.first){
                    null -> listOf()
                    else -> partition?.first?.mapNotNull { it }
                })

                val uSupporterteVedlegg = when(partition?.second){
                    null -> listOf()
                    else -> partition.second.mapNotNull { it.tittel }
                }

                val tema = when {
                    sedHendelseModel.bucType != null -> sedHendelseModel.bucType.TEMA
                    else -> throw RuntimeException("bucType er null")
                }
                val tittel = when {
                    sedHendelseModel.sedType != null -> "${journalpostType.decode()} ${sedHendelseModel.sedType}"
                    else -> throw RuntimeException("sedType er null")
                }
                genererJournalpostModelVellykkede.increment()
                return JournalpostModel(JournalpostRequest(
                        avsenderMottaker = avsenderMottaker,
                        behandlingstema = behandlingstema,
                        bruker = bruker,
                        dokumenter = dokumenter,
                        tema = tema,
                        tittel = tittel,
                        journalpostType = journalpostType
                ), uSupporterteVedlegg)
            }

            catch (ex: Exception){
                genererJournalpostModelFeilede.increment()
                logger.error("noe gikk galt under konstruksjon av JournalpostModel, $ex")
                throw RuntimeException("Feil ved konstruksjon av JournalpostModel, $ex")
            }
        }

        private fun konverterBildeTilPDF(base64Document: String, mimeType: String): String {
            if (mimeType == "application/pdf" || mimeType == "application/pdfa"){
                logger.info("Dokumentet er allerede i PDF format, konverteres ikke")
                return base64Document
            }
            logger.info("Konverterer dokument fra $mimeType til application/pdf")
            return ImageConverter.toBase64PDF(base64Document)
        }

        private fun konverterFilendingTilPdf(filnavn: String): String {
            return filnavn.replaceAfterLast(".", "pdf")
        }

        private fun populerAvsenderMottaker(sedHendelse: SedHendelseModel,
                                            sedHendelseType: HendelseType,
                                            personNavn: String?): AvsenderMottaker {
            return if(sedHendelse.navBruker.isNullOrEmpty() || personNavn.isNullOrEmpty()) {
                if(sedHendelseType == HendelseType.SENDT) {
                    AvsenderMottaker(sedHendelse.avsenderId, IdType.ORGNR, sedHendelse.avsenderNavn)
                } else {
                    AvsenderMottaker(sedHendelse.mottakerId, IdType.UTL_ORG, sedHendelse.mottakerNavn)
                }
            } else {
                AvsenderMottaker(sedHendelse.navBruker, IdType.FNR, personNavn)
            }
        }

        private fun populerJournalpostType(sedHendelseType: HendelseType): JournalpostType {
            return if(sedHendelseType == HendelseType.SENDT) {
                JournalpostType.UTGAAENDE
            } else {
                JournalpostType.INNGAAENDE
            }
        }

    }
}