package no.nav.eessi.pensjon.journalforing

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.SERVICE_UNAVAILABLE)
class SystembrukerTokenException(message: String) : Exception(message)

