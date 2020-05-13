package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper (private val personV3Klient: PersonV3Klient) {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)
    private val mapper = jacksonObjectMapper()

    companion object {
        fun trimFnrString(fnrAsString: String) = fnrAsString.replace("[^0-9]".toRegex(), "")

        fun erFnrDnrFormat(id: String?) : Boolean {
            return id != null && id.length == 11 && id.isNotBlank()
        }
    }

    /**
     * leter etter et gyldig fnr i alle seder henter opp person i PersonV3
     * ved R_BUC_02 leter etter alle personer i Seder og lever liste
     */
    fun getPersonOgFnrFraSeder(seder: List<String?>): Pair<Bruker?, String?>? {
        var fnr: String? = null
        var fnrListe = mutableListOf<Pair<String?, PersonRelasjon>?>()
        var sedType: SedType

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)
                sedType = SedType.valueOf(sedRootNode.get("sed").textValue())

                if(sedType.kanInneholdeFnrEllerFdato) {
                    when (sedType) {
                        SedType.P2100 -> {
                            fnr = filterGjenlevendePinNode(sedRootNode)
                        }
                        SedType.P15000 -> {
                            val krav = sedRootNode.get("nav").get("krav").textValue()
                            fnr = if (krav == "02") {
                                filterGjenlevendePinNode(sedRootNode)
                            } else {
                                filterPersonPinNode(sedRootNode)
                            }
                        }
                        SedType.R005 -> {
                            fnrListe = filterPinPersonR005(sedRootNode)
                        }
                        else -> {
                            // P10000, P9000
                            fnr = filterAnnenpersonPinNode(sedRootNode)
                            if (fnr == null) {
                                //P2000 - P2200 -- andre..
                                fnr = filterPersonPinNode(sedRootNode)
                            }
                        }
                    }
                }
            } catch (ex: Exception) {
                logger.error("Noe gikk galt under henting av fnr fra buc", ex.message)
                throw RuntimeException("Noe gikk galt under henting av fnr fra buc")
            }


            if (fnr != null) {
                fnrListe.add(Pair(fnr, PersonRelasjon.FORSIKRET))
            }

            val personList = mutableSetOf<Triple<Bruker, String, PersonRelasjon>?>()
            fnrListe.forEach {
                try {
                    val fnr = it?.first
                    val type = it?.second
                    val trimmetFnr = trimFnrString(fnr!!)
                    if(erFnrDnrFormat(trimmetFnr)) {
                        val person = personV3Klient.hentPerson(trimmetFnr)
                                ?: throw NullPointerException("PersonV3Klient returnerte null for fnr: $fnr trimmet: $trimmetFnr")
                        logger.info("Funnet person validert og hentet ut fra sed: $sedType")

                        personList.add( Triple(person, trimmetFnr, type) )
                    } else {
                        logger.warn("Feil i personidentifikator ikkenumerisk: $fnr")
                    }
                } catch (ex:Exception) {
                    logger.error("Feil ved henting av PersonV3, fortsetter å sjekke neste sed for fnr", ex)
                }
            }
            return personList
            logger.info("Ingen person funnet ut fra sed: $sedType")
        }
        return null
    }


    fun filterAnnenpersonPinNode(node: JsonNode): String? {
        val subNode = node.at("/nav/annenperson") ?: return null
        if (subNode.at("/person/rolle").textValue() == "01") {
            return subNode.get("person")
                    .findValue("pin")
                    .filter { it.get("land").textValue() == "NO" }
                    .map { it.get("identifikator").textValue() }
                    .lastOrNull()
        }
        return null
    }

    private fun filterGjenlevendePinNode(sedRootNode: JsonNode): String? {
        return finPin(sedRootNode.at("/pensjon/gjenlevende"))
    }

    private fun filterPersonPinNode(sedRootNode: JsonNode): String? {
        return finPin(sedRootNode.at("/nav/bruker"))
    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * * hvis ingen intreffer returnerer vi null
     */
    private fun filterPinPersonR005(sedRootNode: JsonNode): MutableList<Pair<String?, PersonRelasjon>?>{
        val subnode = sedRootNode.at("/nav/bruker").toList()

        var pairList  = mutableListOf<Pair<String?, PersonRelasjon>?>()
        subnode.forEach {
            val enkelNode = it.get("person")
            val type = getType(enkelNode)
            val pin = finPin(enkelNode)
            pairList.add(Pair(pin, type))
        }
        return pairList
    }

    //Kun for R_BUC_02
    private fun getType(node: JsonNode): PersonRelasjon {
        val personType = node.get("tilbakekreving").get("status").get("type").textValue()
        return when(personType) {
            "enke_eller_enkemann" -> PersonRelasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> PersonRelasjon.AVDOD
            else -> PersonRelasjon.ANNET
        }
    }

    private fun finPin(pinNode: JsonNode): String? {
        return pinNode.findValue("pin")
            .filter{ pin -> pin.get("land").textValue() =="NO" }
            .map { pin -> pin.get("identifikator").textValue() }
            .lastOrNull()
    }
}
