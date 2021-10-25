package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.models.BucType
import no.nav.eessi.pensjon.personidentifisering.Relasjon
import no.nav.eessi.pensjon.personidentifisering.SEDPersonRelasjon
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.BARN
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.FORSORGER
import no.nav.eessi.pensjon.personoppslag.Fodselsnummer


class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String): AbstractRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        logger.info("Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}")


        val forsikret = hentForsikretPerson(bestemSaktype(bucType))

        hentAnnenpersonRelasjon()?.let { fnrListe.add(it) }

        logger.debug("forsikret $forsikret")
        logger.debug("gjenlevlist: $fnrListe")

        if (fnrListe.firstOrNull { it.relasjon == Relasjon.BARN || it.relasjon == Relasjon.FORSORGER } != null ) {
            return fnrListe + forsikret
        }
        return fnrListe.ifEmpty { forsikret }
    }

    //Annenperson søker/barn o.l
    fun hentAnnenpersonRelasjon(): SEDPersonRelasjon? {
        if (bucType == BucType.P_BUC_05 || bucType == BucType.P_BUC_10 || bucType == BucType.P_BUC_02) {
            val annenPerson = sed.nav?.annenperson?.person

            logger.debug("annenPerson: $annenPerson")
            annenPerson?.let { person ->
                val sokAnnenPersonKriterie =  opprettSokKriterie(person)
                val annenPersonPin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
                val annenPersonFdato = mapFdatoTilLocalDate(person.foedselsdato)
                val rolle = person.rolle
                val annenPersonRelasjon = when (rolle) {
                    //Rolle barn benyttes ikke i noe journalføring hendelse kun hente ut for...?
                    BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                    //Rolle forsorger benyttes ikke i noe journalføring hendelse...
                    FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                    //etterlatte benyttes i journalføring hendelse..
                    ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                    else -> null
                }
                return annenPersonRelasjon
            }
        }
        return null
    }

}