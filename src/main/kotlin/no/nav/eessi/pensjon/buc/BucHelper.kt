package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.databind.JsonNode

class BucHelper {

    companion object {
        fun filterUtGyldigSedId(alleDokumenterJsonNode: JsonNode): List<Pair<String, String>> {
            val validSedtype = listOf("P2000","P2100","P2200","P1000",
                    "P5000","P6000","P7000", "P8000",
                    "P10000","P1100","P11000","P12000","P14000","P15000", "H070", "R005")

            return alleDokumenterJsonNode
                    .asSequence()
                    .filterNot { rootNode -> rootNode.get("status").textValue() =="empty" }
                    .filter { rootNode ->  validSedtype.contains(rootNode.get("type").textValue()) }
                    .map { validSeds -> Pair(validSeds.get("id").textValue(), validSeds.get("type").textValue()) }
                    .sortedBy { (_, sorting) -> sorting }
                    .toList()
        }
    }
}