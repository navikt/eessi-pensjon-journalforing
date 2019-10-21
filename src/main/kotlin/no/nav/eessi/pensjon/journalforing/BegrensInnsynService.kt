package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.sed.SedFnrSøk
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.norg2.Diskresjonskode
import no.nav.eessi.pensjon.services.personv3.PersonV3Service
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class BegrensInnsynService(private val euxService: EuxService,
                           private val personV3Service: PersonV3Service,
                           private val sedFnrSøk: SedFnrSøk)  {

    private val logger = LoggerFactory.getLogger(BegrensInnsynService::class.java)

    fun begrensInnsyn(sedHendelse: SedHendelseModel): Diskresjonskode? {
        if (sedHendelse.sektorKode != "P") {
            // Vi ignorerer alle hendelser som ikke har vår sektorkode
            return null
        }

        val sed = euxService.hentSed(sedHendelse.rinaSakId, sedHendelse.rinaDokumentId)
        val fnre = sedFnrSøk.finnAlleFnrDnrISed(sed!!)

        fnre.forEach { fnr ->
            val person = personV3Service.hentPerson(fnr)
            person.diskresjonskode?.value?.let { kode ->
                logger.debug("Diskresjonskode: $kode")
                val diskresjonskode = Diskresjonskode.valueOf(kode)
                if(diskresjonskode == Diskresjonskode.SPFO || diskresjonskode == Diskresjonskode.SPSF) {
                    logger.debug("Personen har diskret adresse")
                    return diskresjonskode
                }
            }
        }
        return null
    }
}