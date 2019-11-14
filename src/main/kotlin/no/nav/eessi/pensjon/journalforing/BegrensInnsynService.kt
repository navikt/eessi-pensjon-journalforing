package no.nav.eessi.pensjon.journalforing

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.pdf.SedDokumenter
import no.nav.eessi.pensjon.sed.SedFnrSøk
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BegrensInnsynService(private val euxService: EuxService,
                           private val fagmodulService: FagmodulService,
                           private val personV3Service: PersonV3Service,
                           private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(BegrensInnsynService::class.java)

    private val mapper = jacksonObjectMapper()


    fun begrensInnsyn(sedHendelse: SedHendelseModel): Diskresjonskode? {

        val diskresjonskode = finnDiskresjonkode(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)

        if (diskresjonskode == null) {
            //hvis null prøver vi samtlige SEDs på bucken
            val documentsIds = hentSedDocumentsIds(hentSedsIdfraRina(sedHendelse.rinaSakId))

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


    fun hentSedsIdfraRina(rinaNr: String): String? {
        logger.debug("Prøver å Henter nødvendige Rina documentid fra rinasaknr: $rinaNr")
        return fagmodulService.hentAlleDokumenterFraRinaSak(rinaNr)
    }


    fun hentSedDocumentsIds(sedJson: String?): List<String> {
        val sedRootNode = mapper.readTree(sedJson)
        return sedRootNode
                .filterNot { it.get("status").textValue() =="empty" }
                .map { it.get("id").textValue() }
                .toList()
    }

}