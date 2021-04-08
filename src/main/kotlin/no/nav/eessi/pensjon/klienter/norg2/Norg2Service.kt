package no.nav.eessi.pensjon.klienter.norg2

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.klienter.norg2.BehandlingType.BOSATT_NORGE
import no.nav.eessi.pensjon.klienter.norg2.BehandlingType.BOSATT_UTLAND
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Norg2Service(private val klient: Norg2Klient) {
    private val logger = LoggerFactory.getLogger(Norg2Service::class.java)

    fun hentArbeidsfordelingEnhet(norgRequest: NorgKlientRequest): Enhet? {
        val request = opprettNorg2ArbeidsfordelingRequest(norgRequest)
        logger.debug("følgende request til Norg2 : $request, norgklientreq: $norgRequest")

        return try {
            val enheter = klient.hentArbeidsfordelingEnheter(request)

            val enhet = finnArbeidsfordelingEnheter(request, enheter)

            logger.info("Norg2 gav følgende enhet: $enhet")

            enhet?.let { Enhet.getEnhet(it) }

        } catch (e: Exception) {
            logger.warn("Feil oppsto ved uthenting av Norg2 enhet: ", e)
            null
        }
    }

    @VisibleForTesting
    internal fun opprettNorg2ArbeidsfordelingRequest(req: NorgKlientRequest): Norg2ArbeidsfordelingRequest {
        if (req.harAdressebeskyttelse) return Norg2ArbeidsfordelingRequest(tema = "ANY", diskresjonskode = "SPSF")

        return Norg2ArbeidsfordelingRequest(
            tema = velgTema(req.saktype).also { logger.debug("HentTema: $it") },
            behandlingstema = velgBehandlingTema(req.personRelasjon).also { logger.debug("hentBehandlingtema: $it") },
            geografiskOmraade = req.geografiskTilknytning ?: "ANY",
            behandlingstype = velgBehandligstype(req.landkode)
        )
    }

    fun velgBehandligstype(landkode: String?) =  if (landkode === "NOR") BOSATT_NORGE.kode else BOSATT_UTLAND.kode

    fun velgBehandlingTema(personRelasjon: PersonRelasjon?) : String {
        return if (personRelasjon?.saktype == Saktype.BARNEP) {
            "BARNP"
        } else if (personRelasjon?.saktype == Saktype.GJENLEV) {
            "GJENLEV"
        } else {
            "ANY"
        }
    }

    fun velgTema(sakType: Saktype?): String {
        return if (sakType == Saktype.UFOREP) {
            "UFO"
        } else {
            "PEN"
        }
    }

    @VisibleForTesting
    internal fun finnArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest, list: List<Norg2ArbeidsfordelingItem>): String? {
        return list
                .onEach { logger.debug("enheter-tema: ${it.tema} enhetnr: ${it.enhetNr}") }
                .filter { it.diskresjonskode == request.diskresjonskode }
                .filter { it.behandlingstype == request.behandlingstype }
                .filter { it.behandlingstema == request.behandlingstema }
                .filter { it.tema == request.tema }
                .map { it.enhetNr }.also { logger.debug("Mapped: $it") }
                .lastOrNull()
    }
}
