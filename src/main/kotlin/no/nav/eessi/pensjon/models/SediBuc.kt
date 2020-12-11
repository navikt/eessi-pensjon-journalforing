package no.nav.eessi.pensjon.models

import no.nav.eessi.pensjon.json.mapJsonToAny
import no.nav.eessi.pensjon.json.typeRefs
import no.nav.eessi.pensjon.models.sed.SED

data class SediBuc(
        val id: String,
        val type: SedType,
        val status: String,
        val sedjson: String? = null
) {
    companion object {
        fun getList(list: List<SediBuc>): List<String?> {
            return list.map { it.sedjson }
        }

        fun getListAsSED(list: List<SediBuc>): List<SED> {
            return list.mapNotNull { it.sedjson?.let { json -> mapJsonToAny(json, typeRefs<SED>()) } }
        }

        fun getValuesOf(type: SedType, list: List<SediBuc>): String? {
            return list.firstOrNull { type == it.type }?.sedjson
        }

    }

}
