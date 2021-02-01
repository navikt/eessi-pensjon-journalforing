package no.nav.eessi.pensjon.klienter.norg2

import com.google.common.annotations.VisibleForTesting
import no.nav.eessi.pensjon.models.Enhet
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Norg2Service(private val klient: Norg2Klient) {
    private val logger = LoggerFactory.getLogger(Norg2Service::class.java)

    //https://kodeverk-web.nais.preprod.local/kodeverksoversikt/kodeverk/Behandlingstyper
    companion object {
        private const val BOSATT_NORGE = "ae0104"
        private const val BOSATT_UTLAND = "ae0107"
    }

    fun hentArbeidsfordelingEnhet(person: NorgKlientRequest): Enhet? {
        val request = opprettNorg2ArbeidsfordelingRequest(person)
        logger.debug("følgende request til Norg2 : $request")

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
        if (req.harAdressebeskyttelse)
            return Norg2ArbeidsfordelingRequest(tema = "ANY", diskresjonskode = "SPSF")

        val behandlingstype = if (req.landkode === "NOR") BOSATT_NORGE else BOSATT_UTLAND

        return Norg2ArbeidsfordelingRequest(
            geografiskOmraade = req.geografiskTilknytning ?: "ANY",
            behandlingstype = behandlingstype
        )
    }

    @VisibleForTesting
    internal fun finnArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest, list: List<Norg2ArbeidsfordelingItem>): String? {
        return list.asSequence()
                .filter { it.diskresjonskode == request.diskresjonskode }
                .filter { it.behandlingstype == request.behandlingstype }
                .filter { it.tema == request.tema }
                .map { it.enhetNr }
                .lastOrNull()
    }
}
