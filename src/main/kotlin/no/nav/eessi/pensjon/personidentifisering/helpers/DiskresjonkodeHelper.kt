package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DiskresjonkodeHelper(private val personV3Klient: PersonV3Klient,
                           private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(DiskresjonkodeHelper::class.java)

    private val mapper = jacksonObjectMapper()


    fun hentDiskresjonskode(alleSediBuc: List<String?>): Diskresjonskode? {

        val diskresjonskode = null

        alleSediBuc.forEach { sed -> return finnDiskresjonkode(sed!!) }

        return diskresjonskode
    }

    private fun finnDiskresjonkode(sed: String): Diskresjonskode? {
        logger.debug("Henter Sed dokument for å lete igjennom FNR for diskresjonkode")

        val fnre = sedFnrSøk.finnAlleFnrDnrISed(sed)
        fnre.forEach { fnr ->
            try {
                val person = personV3Klient.hentPerson(fnr)
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

enum class Diskresjonskode(val term: String) {
    SPFO("Sperret adresse, fortrolig"),
    SPSF("Sperret adresse, strengt fortrolig"),
}