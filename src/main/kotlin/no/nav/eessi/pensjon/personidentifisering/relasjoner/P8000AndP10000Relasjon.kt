package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer


class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String): AbstractRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")

        val forsikret = hentForsikretPerson(bestemSaktype(bucType))

        //Annenperson søker/barn o.l
        val gjenlevende: List<SEDPersonRelasjon> = if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
            val annenPerson = sed.nav?.annenperson?.person
            val annenPersonPin = Fodselsnummer.fra(annenPerson?.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val annenPersonFdato = mapFdatoTilLocalDate(annenPerson?.foedselsdato)
            val rolle = annenPerson?.rolle

            annenPerson?.let { person ->
                    val sokAnnenPersonKriterie =  opprettSokKriterie(person)
                    val annenPersonRelasjon = when (rolle) {
                        ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                        //FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato,rinaDocumentId = rinaDocumentId)
                        //BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie, fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                        else -> null
                    }
                    annenPersonRelasjon?.let {
                        fnrListe.add(it)
                    }
                }
            emptyList()
        } else {
            emptyList()
        }

        return fnrListe.ifEmpty { forsikret }
    }

}