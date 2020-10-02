package no.nav.eessi.pensjon.listeners

import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Profile("prod")
@Component
class GyldigeFunksjonerToggleProd : GyldigFunksjoner {
    override fun togglePensjonSak() = false
}

@Profile("!prod")
@Component
class GyldigeFunksjonerToggleNonProd : GyldigFunksjoner {
    override fun togglePensjonSak() = true
}

interface GyldigFunksjoner {
    fun togglePensjonSak(): Boolean
}