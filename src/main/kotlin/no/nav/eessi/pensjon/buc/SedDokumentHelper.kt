package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.klienter.eux.EuxKlient
import no.nav.eessi.pensjon.klienter.fagmodul.FagmodulKlient
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulKlient: FagmodulKlient,
                        private val euxKlient: EuxKlient) {

    private val mapper = jacksonObjectMapper()

    fun hentAlleSedIBuc(rinaSakId: String): List<String?> {
        val alleDokumenter = fagmodulKlient.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            euxKlient.hentSed(rinaSakId, sedDocumentId)
        }
    }
}