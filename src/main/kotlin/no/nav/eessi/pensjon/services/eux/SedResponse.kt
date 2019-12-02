package no.nav.eessi.pensjon.services.eux


class SedResponse(
        val pin: List<Pin>
)

class Pin(val sektor: String,
                    val identifikator: String,
                    val land: String)
