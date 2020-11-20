package no.nav.eessi.pensjon.models

data class SediBuc(
    val id: String,
    val type: SedType,
    val status: String,
    val sedjson: String? = null
){
    companion object {
        fun getList(list: List<SediBuc>): List<String?> {
            return list.map { it.sedjson }
        }

        fun getValuesOf(type: SedType, list: List<SediBuc>): String? {
            return list.firstOrNull { type == it.type }?.sedjson
        }

    }

}
