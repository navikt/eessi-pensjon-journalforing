package no.nav.eessi.pensjon.klienter.eux

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonValue

@JsonIgnoreProperties(ignoreUnknown = true)
class ParticipantHolder(val participants: List<Participant>)

@JsonIgnoreProperties(ignoreUnknown = true)
class Participant(
        val role: Role,
        val organisation: Organisation
)

@JsonIgnoreProperties(ignoreUnknown = true)
class Organisation(val id: String,
                   val address: Address)

@JsonIgnoreProperties(ignoreUnknown = true)
class Address(val country: String)

enum class Role(  @JsonValue val role: String) {
    CASEOWNER("CaseOwner"),
    COUNTERPARTY("CounterParty")
}
