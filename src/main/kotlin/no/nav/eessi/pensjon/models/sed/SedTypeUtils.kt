package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SedType.*
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.kanInneholdeIdentEllerFdato
import no.nav.eessi.pensjon.models.sed.SedTypeUtils.ugyldigeTyper

object SedTypeUtils {

    /**
     * SED-typer som kan inneholde ident (fnr/dnr) og/eller fdato
     */
    val kanInneholdeIdentEllerFdato: Set<SedType> = setOf(
        P2000, P2100, P2200, P3000_AT, P3000_BE, P3000_BG, P3000_CH, P3000_CY, P3000_CZ,
        P3000_DE, P3000_DK, P3000_EE, P3000_EL, P3000_ES, P3000_FI, P3000_FR, P3000_HR,
        P3000_HU, P3000_IE, P3000_IS, P3000_IT, P3000_LI, P3000_LT, P3000_LU, P3000_LV,
        P3000_MT, P3000_NL, P3000_NO, P3000_PL, P3000_PT, P3000_RO, P3000_SE, P3000_SI,
        P3000_SK, P3000_UK, P4000, P5000, P6000, P7000, P8000, P9000, P10000, P11000,
        P12000, P14000, P15000, H020, H021, H070, H120, H121, R004, R005, R006
    )

    /**
     * SED-typer vi IKKE behandler i Journalføring.
     */
    val ugyldigeTyper: Set<SedType> = setOf(
        P13000, X001, X002, X003, X004, X005, X006, X007, X008, X009, X010,
        X011, X012, X013, X050, X100, H001, H002, H020, H021, H120, H121, R004, R006
    )
}

fun SedType.kanInneholdeIdentEllerFdato(): Boolean = this in kanInneholdeIdentEllerFdato

fun SedType?.erGyldig(): Boolean = this != null && this !in ugyldigeTyper
