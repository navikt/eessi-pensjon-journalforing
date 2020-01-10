package no.nav.eessi.pensjon.buc

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.eessi.pensjon.services.eux.EuxService
import no.nav.eessi.pensjon.services.fagmodul.FagmodulService
import org.springframework.stereotype.Component

@Component
class SedDokumentHelper(private val fagmodulService: FagmodulService,
                        private val euxService: EuxService) {

    private val mapper = jacksonObjectMapper()

    fun hentAlleSedIBuc(rinaSakId: String): List<String?> {
        val alleDokumenter = fagmodulService.hentAlleDokumenter(rinaSakId)
        val alleDokumenterJsonNode = mapper.readTree(alleDokumenter)

        val gyldigeSeds = BucHelper.filterUtGyldigSedId(alleDokumenterJsonNode)

        return gyldigeSeds.map { pair ->
            val sedDocumentId = pair.first
            euxService.hentSed(rinaSakId, sedDocumentId)
        }
    }
}