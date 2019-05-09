package no.nav.eessi.pensjon.journalforing.services.aktoerregister

class AktoerregisterException(message: String) : Exception(message)

class AktoerregisterIkkeFunnetException(message: String?): Exception(message)