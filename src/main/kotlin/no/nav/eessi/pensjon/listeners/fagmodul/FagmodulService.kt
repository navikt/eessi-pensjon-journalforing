package no.nav.eessi.pensjon.listeners.fagmodul

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.buc.SakType.*
import no.nav.eessi.pensjon.oppgaverouting.SakInformasjon
import no.nav.eessi.pensjon.personoppslag.pdl.PersonService
import no.nav.eessi.pensjon.personoppslag.pdl.model.AktoerId
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe
import no.nav.eessi.pensjon.personoppslag.pdl.model.IdentGruppe.NPID
import no.nav.eessi.pensjon.utils.toJson
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FagmodulService(private val fagmodulKlient: FagmodulKlient, val personService: PersonService) {

    private val logger = LoggerFactory.getLogger(FagmodulService::class.java)
    private val secureLog = LoggerFactory.getLogger("secureLog")

    fun hentPesysSakId(aktoerId: String, bucType: BucType): List<SakInformasjon>? {
        val eessipenSakTyper = listOf(UFOREP, GJENLEV, BARNEP, ALDER, GENRL, OMSORG)

        val fnr = personService.hentIdent(IdentGruppe.FOLKEREGISTERIDENT, AktoerId(aktoerId)) ?: return null.also {
            logger.info("IdentifisertPerson mangler fnr")
        }
        val sak = fagmodulKlient.hentPensjonSaklist(fnr.id).also {secureLog.info("Svar fra pensjonsinformasjon, før filtrering: ${it.toJson()}")}
            .filter { it.sakId != null && it.sakType in eessipenSakTyper }
            .also {
                secureLog.info("Svar fra pensjonsinformasjon: ${it.toJson()}")
            }
        if (bucType == BucType.P_BUC_03) {
            return sak.sortedBy { if (it.sakType == UFOREP) 0 else 1 }
        } else {
            logger.info("Velger ALDER før UFOERE dersom begge finnes")
            return sak.sortedBy { if (it.sakType == ALDER) 0 else 1 }
        }
    }
}