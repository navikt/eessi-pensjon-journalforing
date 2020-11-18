package no.nav.eessi.pensjon.service.buc

import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.eux.Role
import org.springframework.stereotype.Service

@Service
class BucService (private val euxKlient: EuxKlient) {

    /**
     * Sjekker om Norge er caseowner i BUC
     */
    fun isNorgeCaseOwner(bucId: String) : Boolean {
        return euxKlient.hentInstitusjonerIBuc(bucId)
                .any { it.organisation.address.country == "NO" && it.role == Role.CASEOWNER }
    }

}