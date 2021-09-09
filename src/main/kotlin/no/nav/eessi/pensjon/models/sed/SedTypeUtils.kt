package no.nav.eessi.pensjon.models.sed

import no.nav.eessi.pensjon.eux.model.sed.SedType
import no.nav.eessi.pensjon.eux.model.sed.SedType.H001
import no.nav.eessi.pensjon.eux.model.sed.SedType.H002
import no.nav.eessi.pensjon.eux.model.sed.SedType.H020
import no.nav.eessi.pensjon.eux.model.sed.SedType.H021
import no.nav.eessi.pensjon.eux.model.sed.SedType.H070
import no.nav.eessi.pensjon.eux.model.sed.SedType.H120
import no.nav.eessi.pensjon.eux.model.sed.SedType.H121
import no.nav.eessi.pensjon.eux.model.sed.SedType.P10000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P11000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P12000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P13000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P14000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P15000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2100
import no.nav.eessi.pensjon.eux.model.sed.SedType.P2200
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_AT
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_BE
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_BG
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_CH
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_CY
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_CZ
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_DE
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_DK
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_EE
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_EL
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_ES
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_FI
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_FR
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_HR
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_HU
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_IE
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_IS
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_IT
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_LI
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_LT
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_LU
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_LV
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_MT
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_NL
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_NO
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_PL
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_PT
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_RO
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_SE
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_SI
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_SK
import no.nav.eessi.pensjon.eux.model.sed.SedType.P3000_UK
import no.nav.eessi.pensjon.eux.model.sed.SedType.P4000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P5000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P6000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P7000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P8000
import no.nav.eessi.pensjon.eux.model.sed.SedType.P9000
import no.nav.eessi.pensjon.eux.model.sed.SedType.R004
import no.nav.eessi.pensjon.eux.model.sed.SedType.R005
import no.nav.eessi.pensjon.eux.model.sed.SedType.R006
import no.nav.eessi.pensjon.eux.model.sed.SedType.X001
import no.nav.eessi.pensjon.eux.model.sed.SedType.X002
import no.nav.eessi.pensjon.eux.model.sed.SedType.X003
import no.nav.eessi.pensjon.eux.model.sed.SedType.X004
import no.nav.eessi.pensjon.eux.model.sed.SedType.X005
import no.nav.eessi.pensjon.eux.model.sed.SedType.X006
import no.nav.eessi.pensjon.eux.model.sed.SedType.X007
import no.nav.eessi.pensjon.eux.model.sed.SedType.X008
import no.nav.eessi.pensjon.eux.model.sed.SedType.X009
import no.nav.eessi.pensjon.eux.model.sed.SedType.X010
import no.nav.eessi.pensjon.eux.model.sed.SedType.X011
import no.nav.eessi.pensjon.eux.model.sed.SedType.X012
import no.nav.eessi.pensjon.eux.model.sed.SedType.X013
import no.nav.eessi.pensjon.eux.model.sed.SedType.X050
import no.nav.eessi.pensjon.eux.model.sed.SedType.X100
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
        P12000, P14000, P15000, H020, H021, H070, H120, H121, R004, R005, R006,
        X005, X008, X010
    )

    /**
     * SED-typer vi IKKE behandler i Journalføring.
     */
    val ugyldigeTyper: Set<SedType> = setOf(
        P13000, X001, X002, X003, X004, X006, X007, X009,
        X011, X012, X013, X050, X100, H001, H002, H020, H021, H120, H121, R004, R006
    )
}

fun SedType.kanInneholdeIdentEllerFdato(): Boolean = this in kanInneholdeIdentEllerFdato

fun SedType?.erGyldig(): Boolean = this != null && this !in ugyldigeTyper
