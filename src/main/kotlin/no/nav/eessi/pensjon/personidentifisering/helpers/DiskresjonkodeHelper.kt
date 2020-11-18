package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DiskresjonkodeHelper(private val personV3Service: PersonV3Service,
                           private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(DiskresjonkodeHelper::class.java)

    fun hentDiskresjonskode(alleSediBuc: List<String?>): Diskresjonskode? {
        return alleSediBuc.map { finnDiskresjonkode(it!!) }.firstOrNull()
    }

    private fun finnDiskresjonkode(sed: String): Diskresjonskode? {
        logger.debug("Henter Sed dokument for å lete igjennom FNR for diskresjonkode")

        val fnre = sedFnrSøk.finnAlleFnrDnrISed(sed)
//    TODO: sjekke for kode 6 eller 7 og route deretter
        fnre.forEach { fnr ->
            try {
                val person = personV3Service.hentPerson(fnr)
                person?.diskresjonskode?.value?.let { kode ->
                    logger.debug("Diskresjonskode: $kode")
                    val diskresjonskode = Diskresjonskode.valueOf(kode)
                    if (diskresjonskode == Diskresjonskode.SPSF || diskresjonskode == Diskresjonskode.SPFO) {
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

enum class Diskresjonskode {
    SPFO, //Sperret adresse, fortrolig kode 7
    SPSF //Sperret adresse, strengt fortrolig kode 6
}
