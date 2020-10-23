package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.models.YtelseType
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
    fun getPotensielleFnrFraSeder(seder: List<String?>): List<PersonRelasjon> {
        val fnrListe = mutableSetOf<PersonRelasjon>()
        var sedType: SedType

        seder.forEach { sed ->
            try {
                val sedRootNode = mapper.readTree(sed)
                sedType = SedType.valueOf(sedRootNode.get("sed").textValue())

                if (sedType.kanInneholdeFnrEllerFdato) {
                    logger.debug("SED: $sedType")
                    when (sedType) {
                        SedType.P2100 -> {
                            leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe)
                        }
                        SedType.P15000 -> {
                            val krav = sedRootNode.get("nav").get("krav").textValue()
                            if (krav == "02") {
                                leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe)
                            } else {
                                leggTilForsikretFnrHvisFinnes(sedRootNode, fnrListe)
                            }
                        }
                        SedType.R005 -> {
                            fnrListe.addAll(filterPinPersonR005(sedRootNode))
                        }
                        SedType.P5000, SedType.P6000 -> {
                            // Prøver å hente ut gjenlevende på andre seder enn P2100
                            leggTilGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe)
                        }
                        SedType.P8000 -> {
                            leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sedRootNode, fnrListe)
                        }
                        else -> {
                            // P10000, P9000
                            leggTilAnnenGjenlevendeFnrHvisFinnes(sedRootNode, fnrListe)
                            //P2000 - P2200 -- andre..  (H070)
                            leggTilForsikretFnrHvisFinnes(sedRootNode, fnrListe)

                        }
                    }
                }
            } catch (ex: Exception) {
                logger.warn("Noe gikk galt under innlesing av fnr fra sed", ex)
            }
        }

        return fnrListe.distinctBy { it.fnr }
    }

    private fun leggTilAnnenGjenlevendeFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>) {
        filterAnnenpersonPinNode(sedRootNode)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE))
        }
    }

    private fun leggTilForsikretFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>) {
        filterPersonPinNode(sedRootNode)?.let {
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET))
        }
    }

    private fun leggTilGjenlevendeFnrHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>) {
        filterPersonPinNode(sedRootNode)?.let{
            fnrListe.add(PersonRelasjon(it, Relasjon.FORSIKRET))
            logger.debug("Legger til avdød person ${Relasjon.FORSIKRET}")

        }
        filterGjenlevendePinNode(sedRootNode)?.let {
            val gjenlevendeRelasjon = finnGjenlevendeRelasjontilavdod(sedRootNode)
            if (gjenlevendeRelasjon == null) {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med ukjente relasjoner")
                return
            }
            val gyldigeBarn = listOf("06","07","08","09")
            if ( gyldigeBarn.contains(gjenlevendeRelasjon) ) {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, YtelseType.BARNEP))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med barnerelasjoner")
            } else {
                fnrListe.add(PersonRelasjon(it, Relasjon.GJENLEVENDE, YtelseType.GJENLEV))
                logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
            }
        }
    }


    private fun leggTilAnnenGjenlevendeOgForsikretHvisFinnes(sedRootNode: JsonNode, fnrListe: MutableSet<PersonRelasjon>) {
        logger.debug("Leter i P8000")

        val personPin = filterPersonPinNode(sedRootNode)
        //kun gjenlevende / rolle
        val annenPersonPin = filterAnnenpersonPinNode(sedRootNode)

        personPin?.let {
            fnrListe.add( PersonRelasjon(it, Relasjon.FORSIKRET))
            logger.debug("Legger til person ${Relasjon.FORSIKRET } relasjon")
        }

        annenPersonPin?.let {
            fnrListe.add( PersonRelasjon( it, Relasjon.GJENLEVENDE))
            logger.debug("Legger til person ${Relasjon.GJENLEVENDE} med gjenlevende relasjoner")
        }

    }

    /***
     * /person/rolle 01 : forsikret person
     */
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

    private fun finnGjenlevendeRelasjontilavdod(sedRootNode: JsonNode): String? {
        return sedRootNode.at("/pensjon/gjenlevende/person/relasjontilavdod/relasjon").textValue()
    }

    private fun filterGjenlevendePinNode(sedRootNode: JsonNode): String? {
        return finnPin(sedRootNode.at("/pensjon/gjenlevende/person"))
    }

    private fun filterPersonPinNode(sedRootNode: JsonNode): String? {
        return finnPin(sedRootNode.at("/nav/bruker"))
    }

    /**
     * R005 har mulighet for flere personer.
     * har sed kun en person retureres dette fnr/dnr
     * har sed flere personer leter vi etter status 07/avdød_mottaker_av_ytelser og returnerer dette fnr/dnr
     *
     * * hvis ingen intreffer returnerer vi null
     */
    private fun filterPinPersonR005(sedRootNode: JsonNode): MutableList<PersonRelasjon> {
        val subnode = sedRootNode.at("/nav/bruker").toList()

        val personRelasjoner = mutableListOf<PersonRelasjon>()
        subnode.forEach {
            val enkelNode = it.get("person")
            val pin = finnPin(enkelNode)
            val type = getType(it)
            personRelasjoner.add(PersonRelasjon(pin!!, type))
        }
        return personRelasjoner
    }

    //Kun for R_BUC_02
    private fun getType(node: JsonNode): Relasjon {
        return when (node.get("tilbakekreving").get("status").get("type").textValue()) {
            "enke_eller_enkemann" -> Relasjon.GJENLEVENDE
            "forsikret_person" -> Relasjon.FORSIKRET
            "avdød_mottaker_av_ytelser" -> Relasjon.AVDOD
            else -> Relasjon.ANNET
        }
    }

    private fun finnPin(pinNode: JsonNode): String? {
        val subPinNode = pinNode.findValue("pin") ?: return null
         return subPinNode
                 .filter { pin -> pin.get("land").textValue() == "NO" }
                 .map { pin -> PersonidentifiseringService.trimFnrString(pin.get("identifikator").textValue()) }
                 .lastOrNull { pin -> PersonidentifiseringService.erFnrDnrFormat(pin) }
    }

}
