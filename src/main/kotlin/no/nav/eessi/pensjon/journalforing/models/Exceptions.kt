package no.nav.eessi.pensjon.journalforing.models

import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.ResponseStatus

@ResponseStatus(value = HttpStatus.NOT_FOUND)
class PersonV3IkkeFunnetException(message: String?): Exception(message)

@ResponseStatus(value = HttpStatus.FORBIDDEN)
class PersonV3SikkerhetsbegrensningException(message: String?): Exception(message)