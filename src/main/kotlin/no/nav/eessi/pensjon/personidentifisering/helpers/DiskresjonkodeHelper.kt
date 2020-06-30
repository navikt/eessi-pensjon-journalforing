package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Klient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DiskresjonkodeHelper(private val personV3Klient: PersonV3Klient,
                           private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(DiskresjonkodeHelper::class.java)

    fun hentDiskresjonskode(alleSediBuc: List<String?>): Diskresjonskode? {
        var diskresjonskode : Diskresjonskode? = null

        alleSediBuc.forEach { sed ->
            diskresjonskode = finnDiskresjonkode(sed!!)
            if(diskresjonskode != null) {
                return diskresjonskode
            }
        }
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
}

enum class Diskresjonskode(val term: String) {
    SPFO("Sperret adresse, fortrolig"),
    SPSF("Sperret adresse, strengt fortrolig"),
}
