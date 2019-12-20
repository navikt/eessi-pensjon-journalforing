package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.sed.SedHendelseModel
import no.nav.eessi.pensjon.sed.SedFnrSøk
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.person.Diskresjonskode
import no.nav.eessi.pensjon.services.person.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DiskresjonService(private val euxService: EuxService,
                        private val personV3Service: PersonV3Service,
                        private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(DiskresjonService::class.java)

    private val mapper = jacksonObjectMapper()


    fun hentDiskresjonskode(sedHendelse: SedHendelseModel, alleDokumenter: String?): Diskresjonskode? {

        val diskresjonskode = finnDiskresjonkode(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        if (diskresjonskode == null) {
            //hvis null prøver vi samtlige SEDs på bucken
            val documentsIds = hentSedDocumentsIds(alleDokumenter)

            documentsIds.forEach { documentId ->
                return finnDiskresjonkode(sedHendelse.rinaSakId, documentId)
            }
        }
        return diskresjonskode
    }

    private fun finnDiskresjonkode(rinaNr: String, sedDokumentId: String): Diskresjonskode? {
        logger.debug("Henter Sed dokument for å lete igjennom FNR for diskresjonkode")
        val sed = euxService.hentSed(rinaNr, sedDokumentId)

        val fnre = sedFnrSøk.finnAlleFnrDnrISed(sed!!)
        fnre.forEach { fnr ->
            try {
                val person = personV3Service.hentPerson(fnr)
                person?.diskresjonskode?.value?.let { kode ->
                    logger.debug("Diskresjonskode: $kode")
                    val diskresjonskode = Diskresjonskode.valueOf(kode)
                    if (diskresjonskode == Diskresjonskode.SPSF) {
                        logger.debug("Personen har diskret adresse")
                        return diskresjonskode
                    }
                }
            } catch (ex: Exception) {
                logger.info("forsetter videre: ${ex.message}")
            }
        }
        return null
    }

    fun hentSedDocumentsIds(sedJson: String?): List<String> {
        val sedRootNode = mapper.readTree(sedJson)
        return sedRootNode
                .filterNot { it.get("status").textValue() =="empty" }
                .map { it.get("id").textValue() }
                .toList()
    }
}