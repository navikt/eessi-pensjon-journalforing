package no.nav.eessi.pensjon.servicer.buc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.servicer.buc.model.buc.ParticipantHolder
import no.nav.eessi.pensjon.servicer.buc.model.buc.Role
import org.springframework.stereotype.Service

@Service
class BucService (private val euxKlient: EuxKlient) {

    val mapper = jacksonObjectMapper()

    fun isCaseOwner(bucId: String) : Boolean {
        val buc = euxKlient.hentBuc(bucId)
        val participantHolder = mapper.readValue(buc, ParticipantHolder::class.java)

        return participantHolder.participants.any { it.organisation.address.country == "NO" && it.role == Role.CASEOWNER }
    }

}