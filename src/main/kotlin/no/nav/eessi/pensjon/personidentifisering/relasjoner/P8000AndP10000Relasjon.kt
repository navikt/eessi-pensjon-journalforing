package no.nav.eessi.pensjon.personidentifisering.relasjoner

import no.nav.eessi.pensjon.eux.model.BucType
import no.nav.eessi.pensjon.eux.model.BucType.*
import no.nav.eessi.pensjon.eux.model.sed.SED
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.BARN
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.ETTERLATTE
import no.nav.eessi.pensjon.personidentifisering.helpers.Rolle.FORSORGER
import no.nav.eessi.pensjon.personoppslag.pdl.model.Relasjon
import no.nav.eessi.pensjon.personoppslag.pdl.model.SEDPersonRelasjon
import no.nav.eessi.pensjon.shared.person.Fodselsnummer
import no.nav.eessi.pensjon.utils.toJson

class P8000AndP10000Relasjon(private val sed: SED, private val bucType: BucType, private val rinaDocumentId: String): AbstractRelasjon(sed, bucType, rinaDocumentId) {

    override fun hentRelasjoner(): List<SEDPersonRelasjon> {
        val fnrListe = mutableListOf<SEDPersonRelasjon>()
        logger.info("hentRelasjoner Leter etter gyldig ident og relasjon(er) i SedType: ${sed.type}, med rinasak: $rinaDocumentId")


        val forsikret = hentForsikretPerson(bestemSaktype(bucType))

        hentAnnenpersonRelasjon().let {
            if(bucType in listOf(P_BUC_05, P_BUC_10, P_BUC_02, P_BUC_06) && it != null){
                fnrListe.add(it)
            }
        }

        secureLog.info("forsikret ${forsikret.toJson()}")
        secureLog.info("gjenlevlist: ${fnrListe.toJson()}")

        //TODO: Litt usikker på firstOrNull
        if (fnrListe.firstOrNull { it.relasjon == Relasjon.BARN || it.relasjon == Relasjon.FORSORGER || it.relasjon == Relasjon.GJENLEVENDE } != null ) {
            return fnrListe + forsikret
        }
        return fnrListe.ifEmpty { forsikret }
    }

    //Annenperson søker/barn o.l
    fun hentAnnenpersonRelasjon(): SEDPersonRelasjon? {
        val annenPerson = sed.nav?.annenperson?.person

        secureLog.info("annenPerson: $annenPerson")
        annenPerson?.let { person ->
            val sokAnnenPersonKriterie =  opprettSokKriterie(person)
            //TODO: håndtere npid
            val annenPersonPin = Fodselsnummer.fra(person.pin?.firstOrNull { it.land == "NO" }?.identifikator)
            val annenPersonFdato = mapFdatoTilLocalDate(person.foedselsdato)
            val annenPersonRelasjon = when (person.rolle) {
                //Rolle barn benyttes ikke i noe journalføring hendelse kun hente ut for...?
                BARN.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.BARN, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                //Rolle forsorger benyttes ikke i noe journalføring hendelse...
                FORSORGER.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.FORSORGER, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                //etterlatte benyttes i journalføring hendelse..
                ETTERLATTE.kode -> SEDPersonRelasjon(annenPersonPin, Relasjon.GJENLEVENDE, sedType = sed.type, sokKriterier = sokAnnenPersonKriterie , fdato = annenPersonFdato, rinaDocumentId = rinaDocumentId)
                else -> null
            }
            return annenPersonRelasjon.also { secureLog.info("annenPersonRelasjon: $it") }
        }
        return null
    }

}