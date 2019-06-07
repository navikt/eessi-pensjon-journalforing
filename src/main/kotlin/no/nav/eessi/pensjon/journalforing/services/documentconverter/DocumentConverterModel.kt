package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType

data class DocumentConverterModel(
        val documentContent: String,
        val mimeType: MimeType)
