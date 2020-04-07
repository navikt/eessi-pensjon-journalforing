package no.nav.eessi.pensjon.personidentifisering.helpers

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.models.SedType
import no.nav.eessi.pensjon.personidentifisering.klienter.PersonV3Klient
import no.nav.tjeneste.virksomhet.person.v3.binding.PersonV3
import no.nav.tjeneste.virksomhet.person.v3.informasjon.Bruker
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FnrHelper (private val personV3Klient: PersonV3Klient) {

    private val logger = LoggerFactory.getLogger(FnrHelper::class.java)
    private val mapper = jacksonObjectMapper()

    fun getPersonOgFnrFraSeder(seder: List<String?>): Pair<Bruker?, String?>? {
        var fnr: String? = null
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
            if(fnr != null) {
                try {
                    val person = personV3Klient.hentPerson(fnr!!)
                            ?: throw NullPointerException("PersonV3Klient returnerte null for fnr: $fnr")
                    logger.info("Funnet person validert og hentet ut fra sed: $sedType")
                    return Pair(person,fnr)
                } catch (ex:Exception) {
                    logger.error("Feil ved henting av PersonV3, fortsetter å sjekke neste sed for fnr", ex)
                }
            }
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
        return sedRootNode
                .at("/pensjon/gjenlevende")
                .findValue("pin")
                .filter{ pin -> pin.get("land").textValue() =="NO" }
                .map { pin -> pin.get("identifikator").textValue() }
                .lastOrNull()
    }

    private fun filterPersonPinNode(sedRootNode: JsonNode): String? {
        return sedRootNode
                .at("/nav/bruker")
                .findValue("pin")
                .filter{ pin -> pin.get("land").textValue() =="NO" }
                .map { pin -> pin.get("identifikator").textValue() }
                .lastOrNull()
    }
}
