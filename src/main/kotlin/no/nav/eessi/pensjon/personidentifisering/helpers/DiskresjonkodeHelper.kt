package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.models.sed.SED
import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class DiskresjonkodeHelper(private val personV3Service: PersonV3Service) {

    private val logger = LoggerFactory.getLogger(DiskresjonkodeHelper::class.java)

    fun hentDiskresjonskode(alleSediBuc: List<SED>): Diskresjonskode? {
        return alleSediBuc
                .map { finnDiskresjonkode(it) }
                .firstOrNull()
    }

    private fun finnDiskresjonkode(sed: SED): Diskresjonskode? {
        logger.debug("Henter Sed dokument for å lete igjennom FNR for diskresjonkode")

        return SedFnrSøk.finnAlleFnrDnrISed(sed)
                .mapNotNull { fnr -> hentDiskresjonskode(fnr) }
                .firstOrNull { it == Diskresjonskode.SPSF }
    }

    private fun hentDiskresjonskode(fnr: String): Diskresjonskode? {
        return try {
            val person = personV3Service.hentPerson(fnr)
            val kode = person?.diskresjonskode?.value

            kode?.let { Diskresjonskode.valueOf(it) }
        } catch (ex: Exception) {
            logger.info("forsetter videre: ${ex.message}")
            null
        }
    }
}

enum class Diskresjonskode {
    SPFO, //Sperret adresse, fortrolig kode 7
    SPSF //Sperret adresse, strengt fortrolig kode 6
}
