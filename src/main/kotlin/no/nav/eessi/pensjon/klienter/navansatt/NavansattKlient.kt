package no.nav.eessi.pensjon.klienter.navansatt

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.buc.Buc
import no.nav.eessi.pensjon.metrics.MetricsHelper
import no.nav.eessi.pensjon.utils.mapJsonToAny
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate

@Component
class NavansattKlient(private val navansattRestTemplate: RestTemplate,
                      @Autowired(required = false) private val metricsHelper: MetricsHelper = MetricsHelper.ForTest()
) {

    private val logger: Logger by lazy { LoggerFactory.getLogger(NavansattKlient::class.java) }

    /**
     * Benytter tjenesten navansatt (https://github.com/navikt/navansatt)
     * Ved henting av saksbehandler fra navansatt
     * vil blant annet 0000-GO-Enhet samt saksbehandlers ident og navn returneres
     */
    //@Cacheable("hentNavansatt")
    fun hentAnsatt(saksbehandler: String): String? {
        val path = "/navansatt/$saksbehandler"
        try {
            val json = navansattRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java
            ).body.orEmpty()
            return json
        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av saksbehandler fra navansatt ex: $ex")
        }
        return null
    }

    fun hentAnsattEnhet(saksbehandler: String): String? {
        val path = "/navansatt/$saksbehandler/enheter"
        try {
            val responsebody = navansattRestTemplate.exchange(
                path,
                HttpMethod.GET,
                null,
                String::class.java
            ).body
            return responsebody.orEmpty()
        } catch (ex: Exception) {
            logger.error("En feil oppstod under henting av enhet for saksbehandler ex: $ex")
        }
        return null
    }

    //Pair(saksbehandlerIdent, enhetsId - Enhetsnavn)
    fun navAnsattMedEnhetsInfo(buc: Buc, sedHendelse: SedHendelse): Pair<String, String?>? {
        val navAnsattIdent = buc.documents?.firstOrNull { it.id == sedHendelse.rinaDokumentId }?.versions?.last()?.user?.name
        logger.debug("navAnsatt: $navAnsattIdent")
        if (navAnsattIdent == null) {
            logger.warn("Fant ingen NAV_ANSATT i BUC: ${buc.processDefinitionName} med sakId: ${buc.id}")
            return null
        } else {
            logger.info("Nav ansatt i ${buc.processDefinitionName} med sakId ${buc.id} er: $navAnsattIdent")
            val enhetsInformasjon = hentEnhetsInfo(hentAnsatt(navAnsattIdent)) ?: return null
            return Pair(navAnsattIdent, enhetsInformasjon)
        }
    }

    fun hentEnhetsInfo(enhetsInfo: String?): String? {
        enhetsInfo?.let { it ->
            val navansatt = mapJsonToAny<Navansatt>(it)
            val enhet = navansatt.groups.firstOrNull { it.contains("GO-Enhet") }?.replace("-GO-Enhet", "")
            val enhetsNavn = mapJsonToAny<List<EnheterFraAd>>(enhet!!).firstOrNull { it.id == enhetsInfo }
            return "${enhetsNavn?.id} - ${enhetsNavn?.navn}"
        }
        return null
    }

}