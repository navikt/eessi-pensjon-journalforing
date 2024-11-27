package no.nav.eessi.pensjon.eux

import no.nav.eessi.pensjon.eux.SedTypeUtils.typerMedIdentEllerFDato
import no.nav.eessi.pensjon.eux.SedTypeUtils.ugyldigeTyper
import no.nav.eessi.pensjon.eux.model.SedType
import no.nav.eessi.pensjon.eux.model.SedType.*
import no.nav.eessi.pensjon.eux.model.buc.SakType
import no.nav.eessi.pensjon.eux.model.sed.KravType

object SedTypeUtils {

    /**
     * SED-typer som kan inneholde ident (fnr/dnr) og/eller fdato
     */
    val typerMedIdentEllerFDato: Set<SedType> = setOf(
        SEDTYPE_P2000, SEDTYPE_P2100, SEDTYPE_P2200, SEDTYPE_P3000_AT, SEDTYPE_P3000_BE, SEDTYPE_P3000_BG, SEDTYPE_P3000_CH, SEDTYPE_P3000_CY, SEDTYPE_P3000_CZ,
        SEDTYPE_P3000_DE, SEDTYPE_P3000_DK, SEDTYPE_P3000_EE, SEDTYPE_P3000_EL, SEDTYPE_P3000_ES, SEDTYPE_P3000_FI, SEDTYPE_P3000_FR, SEDTYPE_P3000_HR,
        SEDTYPE_P3000_HU, SEDTYPE_P3000_IE, SEDTYPE_P3000_IS, SEDTYPE_P3000_IT, SEDTYPE_P3000_LI, SEDTYPE_P3000_LT, SEDTYPE_P3000_LU, SEDTYPE_P3000_LV,
        SEDTYPE_P3000_MT, SEDTYPE_P3000_NL, SEDTYPE_P3000_NO, SEDTYPE_P3000_PL, SEDTYPE_P3000_PT, SEDTYPE_P3000_RO, SEDTYPE_P3000_SE, SEDTYPE_P3000_SI,
        SEDTYPE_P3000_SK, SEDTYPE_P3000_UK, SEDTYPE_P4000, SEDTYPE_P5000, SEDTYPE_P6000, SEDTYPE_P7000, SEDTYPE_P8000, SEDTYPE_P9000, SEDTYPE_P1000, SEDTYPE_P10000, SEDTYPE_P11000,
        SEDTYPE_P12000, SEDTYPE_P14000, SEDTYPE_P15000, SEDTYPE_H020, SEDTYPE_H021, SEDTYPE_H070, SEDTYPE_H120, SEDTYPE_H121, SEDTYPE_R004, SEDTYPE_R005, SEDTYPE_R006,
        SEDTYPE_X005, SEDTYPE_X008, SEDTYPE_X010,
        SEDTYPE_M040, SEDTYPE_M050, SEDTYPE_M051, SEDTYPE_M052, SEDTYPE_M053
    )

    /**
     * SED-typer vi IKKE benytter for innhenting av data/meta-data i Journalføring.
     */
    val ugyldigeTyper: Set<SedType> = setOf(
        SEDTYPE_P13000, SEDTYPE_X001, SEDTYPE_X002, SEDTYPE_X003, SEDTYPE_X004, SEDTYPE_X006, SEDTYPE_X007, SEDTYPE_X009,
        SEDTYPE_X011, SEDTYPE_X012, SEDTYPE_X013, SEDTYPE_X050, SEDTYPE_X100, SEDTYPE_H001, SEDTYPE_H002, SEDTYPE_H020, SEDTYPE_H021, SEDTYPE_H120, SEDTYPE_H121, SEDTYPE_R006
    )

    fun mapKravtypeTilSaktype(krav: KravType?): SakType {
        return when (krav) {
            KravType.GJENLEV -> SakType.GJENLEV
            KravType.UFOREP -> SakType.UFOREP
            else -> SakType.ALDER
        }
    }
}

fun SedType.kanInneholdeIdentEllerFdato(): Boolean = this in typerMedIdentEllerFDato

fun SedType?.erGyldig(): Boolean = (this != null) && (this !in ugyldigeTyper)
