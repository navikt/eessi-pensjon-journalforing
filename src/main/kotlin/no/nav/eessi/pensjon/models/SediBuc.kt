package no.nav.eessi.pensjon.models

class SediBuc(
    val id: String,
    val type: SedType,
    val status: String,
    var sedjson: String? = null
){
    companion object {
        fun getList(list: List<SediBuc>): List<String?> {
            return list.map { it.sedjson }.toList()
        }

        fun getValuesOf(type: SedType, list: List<SediBuc>): String? {
            return list.firstOrNull { type == it.type }?.sedjson
        }

    }

}
