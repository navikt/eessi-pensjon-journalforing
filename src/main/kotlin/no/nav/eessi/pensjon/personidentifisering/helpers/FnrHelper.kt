package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.personidentifisering.PersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.PersonidentifiseringService
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)
    private val mapper = jacksonObjectMapper()

    /**
     * leter etter et gyldig fnr i alle seder henter opp person i PersonV3
     * ved R_BUC_02 leter etter alle personer i Seder og lever liste
     */
    fun getPotensielleFnrFraSeder(seder: List<String?>):Set<PersonRelasjon> {
        var fnr: String? = null
        val fnrListe = mutableSetOf<PersonRelasjon>()
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
                            fnrListe.addAll( filterPinPersonR005(sedRootNode) )
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

            if (PersonidentifiseringService.erFnrDnrFormat(fnr)) {
                fnrListe.add(PersonRelasjon(fnr!!, Relasjon.FORSIKRET))
            }
            logger.info("Ingen person funnet ut fra sed: $sedType")
        }
        return fnrListe

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
    private fun filterPinPersonR005(sedRootNode: JsonNode): MutableList<PersonRelasjon>{
        val subnode = sedRootNode.at("/nav/bruker").toList()

        val personRelasjoner  = mutableListOf<PersonRelasjon>()
        subnode.forEach {
            val enkelNode = it.get("person")
            val pin = finPin(enkelNode)
            val type = getType(it)
            personRelasjoner.add(PersonRelasjon(pin!!, type))
        }
        return personRelasjoner
    }

    //Kun for R_BUC_02
    private fun getType(node: JsonNode): Relasjon {
        return when(node.get("tilbakekreving").get("status").get("type").textValue()) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }

    private fun finPin(pinNode: JsonNode): String? {
        return pinNode.findValue("pin")
            .filter{ pin -> pin.get("land").textValue() =="NO" }
            .map { pin -> pin.get("identifikator").textValue() }
            .lastOrNull()
    }
}
