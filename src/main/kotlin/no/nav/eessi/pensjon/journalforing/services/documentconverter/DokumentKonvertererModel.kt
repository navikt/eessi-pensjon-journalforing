package no.nav.eessi.pensjon.journalforing.services.documentconverter

import no.nav.eessi.pensjon.journalforing.services.eux.MimeType

data class DokumentKonvertererModel(
        val dokumentInnhold: String,
        val mimeType: MimeType)
