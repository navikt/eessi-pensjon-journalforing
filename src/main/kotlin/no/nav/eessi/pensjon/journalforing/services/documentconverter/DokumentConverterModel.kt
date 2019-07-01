package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType

data class DokumentConverterModel(
        val dokumentInnhold: String,
        val mimeType: MimeType)
