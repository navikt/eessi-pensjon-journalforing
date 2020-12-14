package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.sed.Bruker
import no.nav.eessi.pensjon.models.sed.Person
import no.nav.eessi.pensjon.models.sed.SED
import org.slf4j.LoggerFactory

/**
 * Går gjennom SED og søker etter fnr/dnr
 *
 * Denne koden søker etter kjente keys som kan inneholde fnr/dnr i P og H-SED
 *
 * Kjente keys: "Pin", "kompetenteuland"
 *
 */
class SEDFnrExtractor {
    companion object {
        private val logger = LoggerFactory.getLogger(SEDFnrExtractor::class.java)

        private val mapper = jacksonObjectMapper()

        /**
         * Finner alle fnr i SED
         *
         * @param sed SED i json format
         * @return distinkt set av fnr
         */
        fun finnAlleFnrDnrISed(sed: SED): Set<Fodselsnummer> {
            logger.info("Søker etter fnr i SED")
            try {
                val fnrBrukerListe: List<Fodselsnummer> = sed.nav?.bruker?.mapNotNull { it.person?.hentNorskFnr() }
                        ?: emptyList()
                val fnrMor: List<Fodselsnummer> = sed.nav?.bruker?.mapNotNull { it.mor?.person?.hentNorskFnr() }
                        ?: emptyList()
                val fnrFar: List<Fodselsnummer> = sed.nav?.bruker?.mapNotNull { it.far?.person?.hentNorskFnr() }
                        ?: emptyList()

                val fnrEktefelle: Fodselsnummer? = sed.nav?.ektefelle?.person?.hentNorskFnr()
                val fnrBarn: List<Fodselsnummer> = sed.nav?.barn?.mapNotNull { it.person?.hentNorskFnr() }
                        ?: emptyList()

//                return listOf(fnrBrukerListe.flatten(), fnrMor, fnrFar, fnrEktefelle, fnrBarn)

                val asdf =

                        return emptySet()
            } catch (ex: Exception) {
                logger.info("En feil oppstod under søk av fødselsnummer i SED", ex)
                throw ex
            }
        }

        private fun extract(sed: SED, block: (Bruker?) -> Person?): List<Fodselsnummer> {
            return sed.nav?.bruker?.mapNotNull { block(it)?.hentNorskFnr() } ?: emptyList()
        }
    }
}