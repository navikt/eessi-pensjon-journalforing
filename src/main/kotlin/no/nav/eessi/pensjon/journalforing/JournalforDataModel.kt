package no.nav.eessi.pensjon.journalforing

import no.nav.eessi.pensjon.eux.model.SedHendelse
import no.nav.eessi.pensjon.eux.model.sed.KravType
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.SaksInfoSamlet
import no.nav.eessi.pensjon.oppgaverouting.Enhet

data class JournalforDataModel (
    val sedHendelse: SedHendelse,
    val saksInfoSamlet: SaksInfoSamlet? = null,
    val sed: SED? = null,
    val harAdressebeskyttelse: Boolean = false,
    val navAnsattInfo: Pair<String, Enhet?>? = null,
    val kravTypeFraSed: KravType?
)