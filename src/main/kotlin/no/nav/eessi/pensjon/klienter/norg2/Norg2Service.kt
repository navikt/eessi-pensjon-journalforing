package no.nav.eessi.pensjon.klienter.norg2

import no.nav.eessi.pensjon.klienter.norg2.BehandlingType.BOSATT_NORGE
import no.nav.eessi.pensjon.klienter.norg2.BehandlingType.BOSATT_UTLAND
import no.nav.eessi.pensjon.models.Enhet
import no.nav.eessi.pensjon.models.Saktype
import no.nav.eessi.pensjon.models.Tema
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class Norg2Service(private val klient: Norg2Klient) {
    private val logger = LoggerFactory.getLogger(Norg2Service::class.java)

    fun hentArbeidsfordelingEnhet(norgRequest: NorgKlientRequest): Enhet? {
        val request = opprettNorg2ArbeidsfordelingRequest(norgRequest)
        return try {
            val enheter = klient.hentArbeidsfordelingEnheter(request)

            val enhet = finnArbeidsfordelingEnheter(request, enheter)

            enhet?.let { Enhet.getEnhet(it) }

        } catch (e: Exception) {
            logger.warn("Feil oppsto ved uthenting av Norg2 enhet: ", e)
            null
        }
    }

    internal fun opprettNorg2ArbeidsfordelingRequest(req: NorgKlientRequest): Norg2ArbeidsfordelingRequest {
        if (req.harAdressebeskyttelse) return Norg2ArbeidsfordelingRequest(tema = "ANY", diskresjonskode = "SPSF")

        return Norg2ArbeidsfordelingRequest(
            tema = velgTema(req.saktype).also { logger.debug("HentTema: $it") },
            behandlingstema = velgBehandlingTema(req.SEDPersonRelasjon).also { logger.debug("hentBehandlingtema: $it") },
            geografiskOmraade = req.geografiskTilknytning ?: "ANY",
            behandlingstype = velgBehandligstype(req.landkode)
        )
    }

    fun velgBehandligstype(landkode: String?) = if (landkode === "NOR") BOSATT_NORGE.kode else BOSATT_UTLAND.kode

    fun velgTema(sakType: Saktype?) = if (sakType == Saktype.UFOREP) Tema.UFORETRYGD.kode else Tema.PENSJON.kode

    fun velgBehandlingTema(SEDPersonRelasjon: SEDPersonRelasjon?) : String {
        return when (SEDPersonRelasjon?.saktype) {
            Saktype.BARNEP -> Norg2BehandlingsTema.BARNEP.kode
            Saktype.GJENLEV -> Norg2BehandlingsTema.GJENLEV.kode
            else -> Norg2BehandlingsTema.ANY.kode
        }
    }

    internal fun finnArbeidsfordelingEnheter(request: Norg2ArbeidsfordelingRequest, list: List<Norg2ArbeidsfordelingItem>): String? {
        return list
                .onEach { logger.debug("enheter-tema: ${it.tema} enhetnr: ${it.enhetNr}") }
                .filter { it.diskresjonskode == request.diskresjonskode }
                .filter { it.behandlingstype == request.behandlingstype }
                .filter { it.behandlingstema == request.behandlingstema }
                .filter { it.tema == request.tema }
                .map { it.enhetNr }
                .also { logger.info("Funnet enhet(er) etter filtrering fra NORG: $it, velger: ${it.lastOrNull()}") }
                .lastOrNull()
    }
}
