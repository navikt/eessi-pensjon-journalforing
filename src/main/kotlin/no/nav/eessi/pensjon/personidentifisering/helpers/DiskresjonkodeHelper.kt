package no.nav.eessi.pensjon.personidentifisering.helpers

import no.nav.eessi.pensjon.personoppslag.personv3.PersonV3Service
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
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

        return sedFnrSøk.finnAlleFnrDnrISed(sed)
                .mapNotNull { hentPerson(it) }
                .mapNotNull { person -> person.diskresjonskode?.value }
                .map { Diskresjonskode.valueOf(it)}
                .firstOrNull { it == Diskresjonskode.SPSF }
    }

    private fun hentPerson(fnr: String): Bruker? {
        return try {
            personV3Service.hentPerson(fnr)
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
